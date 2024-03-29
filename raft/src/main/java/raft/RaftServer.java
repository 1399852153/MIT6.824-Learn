package raft;

import myrpc.common.URLAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.api.command.GetCommand;
import raft.api.command.SetCommand;
import raft.api.model.*;
import raft.api.service.RaftService;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.common.enums.ServerStatusEnum;
import raft.common.model.RaftSnapshot;
import raft.exception.MyRaftException;
import raft.module.*;
import raft.module.api.KVReplicationStateMachine;
import raft.task.HeartBeatBroadcastTask;
import raft.util.CollectionUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaftServer implements RaftService {

    private static final Logger logger = LoggerFactory.getLogger(RaftServer.class);

    /**
     * 当前服务节点的id(集群内全局唯一)
     * */
    private final int serverId;

    /**
     * Raft服务端配置
     * */
    private final RaftConfig raftConfig;

    /**
     * 当前服务器的状态
     * */
    private volatile ServerStatusEnum serverStatusEnum;

    /**
     * raft服务器元数据(当前任期值currentTerm、当前投票给了谁votedFor)
     * */
    private final RaftServerMetaDataPersistentModule raftServerMetaDataPersistentModule;

    /**
     * 当前服务认为的leader节点的Id
     * */
    private volatile Integer currentLeader;

    /**
     * 集群中的其它raft节点服务
     * */
    protected List<RaftService> otherNodeInCluster;

    private LogModule logModule;
    private SnapshotModule snapshotModule;
    private RaftLeaderElectionModule raftLeaderElectionModule;
    private RaftHeartBeatBroadcastModule raftHeartBeatBroadcastModule;
    private KVReplicationStateMachine kvReplicationStateMachine;

    /**
     * nextIndex[]
     * */
    private final Map<RaftService,Long> nextIndexMap = new HashMap<>();

    /**
     * matchIndex[]
     * */
    private final Map<RaftService,Long> matchIndexMap = new HashMap<>();

    public RaftServer(RaftConfig raftConfig) {
        this.serverId = raftConfig.getServerId();
        this.raftConfig = raftConfig;
        // 初始化时都是follower
        this.serverStatusEnum = ServerStatusEnum.FOLLOWER;

        // 服务器元数据模块
        this.raftServerMetaDataPersistentModule = new RaftServerMetaDataPersistentModule(raftConfig.getServerId());
    }

    public void init(List<RaftService> otherNodeInCluster){
        // 集群中的其它节点服务
        this.otherNodeInCluster = otherNodeInCluster;

        try {
            // 日志模块依赖快照模块
            snapshotModule = new SnapshotModule(this);
            logModule = new LogModule(this);
        } catch (IOException e) {
            throw new MyRaftException("init LogModule error!",e);
        }

        raftLeaderElectionModule = new RaftLeaderElectionModule(this);
        raftHeartBeatBroadcastModule = new RaftHeartBeatBroadcastModule(this);
        kvReplicationStateMachine = new SimpleReplicationStateMachine(this.getServerId());

        logger.info("raft server init end! otherNodeInCluster={}, currentServerId={}",otherNodeInCluster,serverId);
    }

    @Override
    public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        RequestVoteRpcResult requestVoteRpcResult = raftLeaderElectionModule.requestVoteProcess(requestVoteRpcParam);

        processCommunicationHigherTerm(requestVoteRpcParam.getTerm());

        logger.info("do requestVote requestVoteRpcParam={},requestVoteRpcResult={}, currentServerId={}",
            requestVoteRpcParam,requestVoteRpcResult,this.serverId);

        return requestVoteRpcResult;
    }

    @Override
    public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        AppendEntriesRpcResult appendEntriesRpcResult = doAppendEntries(appendEntriesRpcParam);

        processCommunicationHigherTerm(appendEntriesRpcParam.getTerm());

        if(!CollectionUtil.isEmpty(appendEntriesRpcParam.getEntries())){
            logger.info("do appendEntries appendEntriesRpcParam={},appendEntriesRpcResult={},currentServerId={}",
                appendEntriesRpcParam,appendEntriesRpcResult,this.serverId);
        }else{
            logger.debug("do appendEntries appendEntriesRpcParam={},appendEntriesRpcResult={},currentServerId={}",
                appendEntriesRpcParam,appendEntriesRpcResult,this.serverId);
        }

        return appendEntriesRpcResult;
    }

    @Override
    public InstallSnapshotRpcResult installSnapshot(InstallSnapshotRpcParam installSnapshotRpcParam) {
        logger.info("installSnapshot start! serverId={},installSnapshotRpcParam={}",this.serverId,installSnapshotRpcParam);

        if(installSnapshotRpcParam.getTerm() < this.raftServerMetaDataPersistentModule.getCurrentTerm()){
            // Reply immediately if term < currentTerm
            // 拒绝处理任期低于自己的老leader的请求

            logger.info("installSnapshot term < currentTerm");
            return new InstallSnapshotRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm());
        }

        // 安装快照
        this.snapshotModule.appendInstallSnapshot(installSnapshotRpcParam);

        // 快照已经完全安装好了
        if(installSnapshotRpcParam.isDone()){
            // discard any existing or partial snapshot with a smaller index
            // 快照整体安装完毕，清理掉index小于等于快照中lastIncludedIndex的所有日志(日志压缩)
            logModule.compressLogBySnapshot(installSnapshotRpcParam);

            // Reset state machine using snapshot contents (and load snapshot’s cluster configuration)
            // follower的状态机重新安装快照
            RaftSnapshot raftSnapshot = this.snapshotModule.readSnapshot();
            kvReplicationStateMachine.installSnapshot(raftSnapshot.getSnapshotData());
        }

        logger.info("installSnapshot end! serverId={}",this.serverId);

        return new InstallSnapshotRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm());
    }

    @Override
    public synchronized ClientRequestResult clientRequest(ClientRequestParam clientRequestParam) {
        // 不是leader
        if(this.serverStatusEnum != ServerStatusEnum.LEADER){
            if(this.currentLeader == null){
                // 自己不是leader，也不知道谁是leader直接报错
                throw new MyRaftException("current node not leader，and leader is null! serverId=" + this.serverId);
            }

            RaftNodeConfig leaderConfig = this.raftConfig.getRaftNodeConfigList()
                    .stream().filter(item->item.getServerId() == this.currentLeader).findAny().get();

            // 把自己认为的leader告诉客户端
            ClientRequestResult clientRequestResult = new ClientRequestResult();
            clientRequestResult.setLeaderAddress(new URLAddress(leaderConfig.getIp(),leaderConfig.getPort()));

            logger.info("not leader response known leader, result={}",clientRequestResult);
            return clientRequestResult;
        }

        // 是leader，处理读请求
        if(clientRequestParam.getCommand() instanceof GetCommand){
            // 进行一次心跳广播，判断当前自己是否还是leader
            boolean stillBeLeader = HeartBeatBroadcastTask.doHeartBeatBroadcast(this);
            if(stillBeLeader){
                // 还是leader，可以响应客户端
                logger.info("do client read op, still be leader");

                // Read-only operations can be handled without writing anything into the log.
                GetCommand getCommand = (GetCommand) clientRequestParam.getCommand();

                // 直接从状态机中读取就行
                String value = this.kvReplicationStateMachine.get(getCommand.getKey());

                ClientRequestResult clientRequestResult = new ClientRequestResult();
                clientRequestResult.setSuccess(true);
                clientRequestResult.setValue(value);

                logger.info("response getCommand, result={}",clientRequestResult);

                return clientRequestResult;
            }else{
                // 广播后发现自己不再是leader了，报错，让客户端重新自己找leader (客户端和当前节点同时误判，小概率发生)
                throw new MyRaftException("do client read op, but not still be leader!" + this.serverId);
            }
        }

        // 自己是leader，需要处理客户端的写请求

        // 构造新的日志条目
        LogEntry newLogEntry = new LogEntry();
        newLogEntry.setLogTerm(this.raftServerMetaDataPersistentModule.getCurrentTerm());
        // 新日志的索引号为当前最大索引编号+1
        newLogEntry.setLogIndex(this.logModule.getLastIndex() + 1);
        newLogEntry.setCommand(clientRequestParam.getCommand());


        logger.info("handle setCommand, do writeLocalLog entry={}",newLogEntry);

        // 预写入日志
        logModule.writeLocalLog(newLogEntry);

        logger.info("handle setCommand, do writeLocalLog success!");

        List<AppendEntriesRpcResult> appendEntriesRpcResultList = logModule.replicationLogEntry(newLogEntry);

        logger.info("do replicationLogEntry, result={}",appendEntriesRpcResultList);

        // successNum需要加上自己的1票
        long successNum = appendEntriesRpcResultList.stream().filter(AppendEntriesRpcResult::isSuccess).count() + 1;
        if(successNum >= this.raftConfig.getMajorityNum()){
            // If command received from client: append entry to local log, respond after entry applied to state machine (§5.3)

            // 成功复制到多数节点

            // 设置最新的已提交索引编号
            logModule.setLastCommittedIndex(newLogEntry.getLogIndex());
            // 作用到状态机上
            this.kvReplicationStateMachine.apply((SetCommand) newLogEntry.getCommand());
            // 思考一下：lastApplied为什么不需要持久化？ 状态机指令的应用和更新lastApplied非原子性会产生什么问题？
            logModule.setLastApplied(newLogEntry.getLogIndex());

            // 返回成功
            ClientRequestResult clientRequestResult = new ClientRequestResult();
            clientRequestResult.setSuccess(true);

            return clientRequestResult;
        }else{
            // 没有成功复制到多数,返回失败
            ClientRequestResult clientRequestResult = new ClientRequestResult();
            clientRequestResult.setSuccess(false);

            // 删掉之前预写入的日志条目
            // 思考一下如果删除完成之前，宕机了有问题吗？ 个人感觉是ok的
            logModule.deleteLocalLog(newLogEntry.getLogIndex());

            return clientRequestResult;
        }
    }

    private AppendEntriesRpcResult doAppendEntries(AppendEntriesRpcParam appendEntriesRpcParam){
        if(appendEntriesRpcParam.getTerm() < this.raftServerMetaDataPersistentModule.getCurrentTerm()){
            // Reply false if term < currentTerm (§5.1)
            // 拒绝处理任期低于自己的老leader的请求

            logger.info("doAppendEntries term < currentTerm");
            return new AppendEntriesRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm(),false);
        }

        if(appendEntriesRpcParam.getTerm() >= this.raftServerMetaDataPersistentModule.getCurrentTerm()){
            // appendEntries请求中任期值如果大于自己，说明已经有一个更新的leader了，自己转为follower，并且以对方更大的任期为准
            this.serverStatusEnum = ServerStatusEnum.FOLLOWER;
            this.currentLeader = appendEntriesRpcParam.getLeaderId();
            this.raftServerMetaDataPersistentModule.setCurrentTerm(appendEntriesRpcParam.getTerm());
        }

        if(appendEntriesRpcParam.getEntries() == null){
            // 来自leader的心跳处理，清理掉之前选举的votedFor
            this.cleanVotedFor();
            // entries为空，说明是心跳请求，刷新一下最近收到心跳的时间
            raftLeaderElectionModule.refreshLastHeartbeatTime();

            long currentLastCommittedIndex = logModule.getLastCommittedIndex();
            logger.debug("doAppendEntries heartbeat leaderCommit={},currentLastCommittedIndex={}",
                appendEntriesRpcParam.getLeaderCommit(),currentLastCommittedIndex);

            if(appendEntriesRpcParam.getLeaderCommit() > currentLastCommittedIndex) {
                // 心跳处理里，如果leader当前已提交的日志进度超过了当前节点的进度，令当前节点状态机也跟上
                // 如果leaderCommit >= logModule.getLastIndex(),说明当前节点的日志进度不足，但可以把目前已有的日志都提交给状态机去执行
                // 如果leaderCommit < logModule.getLastIndex(),说明当前节点进度比较快，有一些日志是leader已复制但还没提交的，把leader已提交的那一部分作用到状态机就行
                long minNeedCommittedIndex = Math.min(appendEntriesRpcParam.getLeaderCommit(), logModule.getLastIndex());
                pushStatemachineApply(minNeedCommittedIndex);
            }

            // 心跳请求，直接返回
            return new AppendEntriesRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm(),true);
        }

        // logEntries不为空，是真实的日志复制rpc

        // AppendEntry可靠性校验，如果prevLogIndex和prevLogTerm不匹配，则需要返回false，让leader发更早的日志过来
        {
            LogEntry localPrevLogEntry = logModule.readLocalLog(appendEntriesRpcParam.getPrevLogIndex());
            if(localPrevLogEntry == null){
                RaftSnapshot raftSnapshot = snapshotModule.readSnapshotMetaData();
                localPrevLogEntry = new LogEntry();
                if(raftSnapshot == null){
                    // 当前节点日志条目为空,又没有快照，说明完全没有日志(默认任期为-1，这个是约定)
                    localPrevLogEntry.setLogIndex(-1);
                    localPrevLogEntry.setLogTerm(-1);
                }else{
                    // 日志里没有，但是有快照
                    localPrevLogEntry.setLogIndex(raftSnapshot.getLastIncludedIndex());
                    localPrevLogEntry.setLogTerm(raftSnapshot.getLastIncludedTerm());
                }
            }

            if (localPrevLogEntry.getLogTerm() != appendEntriesRpcParam.getPrevLogTerm()) {
                //  Reply false if log doesn’t contain an entry at prevLogIndex
                //  whose term matches prevLogTerm (§5.3)
                //  本地日志和参数中的PrevLogIndex和PrevLogTerm对不上(对应日志不存在，或者任期对不上)

                logger.info("doAppendEntries localPrevLogEntry not match, localLogEntry={}",localPrevLogEntry);

                return new AppendEntriesRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm(),false);
            }
        }

        logger.info("doAppendEntries localEntry is match");

        // 简单起见，先只考虑一次rpc仅单个entry的场景
        LogEntry newLogEntry = appendEntriesRpcParam.getEntries().get(0);

        AppendEntriesRpcResult appendEntriesRpcResult;
        // 新日志的复制操作
        LogEntry localLogEntry = logModule.readLocalLog(newLogEntry.getLogIndex());
        if(localLogEntry == null){
            // 本地日志不存在，追加写入
            // Append any new entries not already in the log
            logModule.writeLocalLog(newLogEntry);

            logger.info("doAppendEntries localEntry not exist, append log");

            appendEntriesRpcResult = new AppendEntriesRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm(), true);
        }else{
            if(localLogEntry.getLogTerm() == newLogEntry.getLogTerm()){
                logger.info("local log existed and term match. return success");
                // 本地日志存在，且任期一致,幂等返回成功
                appendEntriesRpcResult = new AppendEntriesRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm(), true);
            }else{
                logger.info("local log existed but term conflict. delete conflict log");

                // 本地日志存在，但任期不一致
                // If an existing entry conflicts with a new one (same index
                // but different terms), delete the existing entry and all that
                // follow it (§5.3)

                // 先删除index以及以后有冲突的日志条目
                logModule.deleteLocalLog(newLogEntry.getLogIndex());
                // 然后再写入新的日志
                logModule.writeLocalLog(newLogEntry);

                // 返回成功
                appendEntriesRpcResult = new AppendEntriesRpcResult(this.raftServerMetaDataPersistentModule.getCurrentTerm(), true);
            }
        }

        // If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if(appendEntriesRpcParam.getLeaderCommit() > logModule.getLastCommittedIndex()){
            // 如果leaderCommit更大，说明当前节点的同步进度慢于leader，以新的entry里的index为准(更高的index还没有在本地保存(因为上面的appendEntry有效性检查))
            // 如果index of last new entry更大，说明当前节点的同步进度是和leader相匹配的，commitIndex以leader最新提交的为准
            long lastCommittedIndex = Math.min(appendEntriesRpcParam.getLeaderCommit(), newLogEntry.getLogIndex());
            pushStatemachineApply(lastCommittedIndex);
        }

        return appendEntriesRpcResult;
    }

    private void pushStatemachineApply(long lastCommittedIndex){
        long lastApplied = logModule.getLastApplied();

        // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
        if(lastApplied < lastCommittedIndex){
            // 作用在状态机上的日志编号低于集群中已提交的日志编号，需要把这些已提交的日志都作用到状态机上去
            logger.info("pushStatemachineApply.apply, lastApplied={},lastCommittedIndex={}",lastApplied,lastCommittedIndex);

            // 全读取出来(读取出来是按照index从小到大排好序的)
            List<LogEntry> logEntryList = logModule.readLocalLog(lastApplied+1,lastCommittedIndex);
            for(LogEntry logEntry : logEntryList){
                logger.info("kvReplicationStateMachine.apply, logEntry={}",logEntry);

                // 按照顺序依次作用到状态机中
                this.kvReplicationStateMachine.apply((SetCommand) logEntry.getCommand());
            }
        }

        this.logModule.setLastCommittedIndex(lastCommittedIndex);
        this.logModule.setLastApplied(lastCommittedIndex);
    }

    // ================================= get/set ============================================

    public int getServerId() {
        return serverId;
    }

    public RaftConfig getRaftConfig() {
        return raftConfig;
    }

    public void setServerStatusEnum(ServerStatusEnum serverStatusEnum) {
        this.serverStatusEnum = serverStatusEnum;
    }

    public ServerStatusEnum getServerStatusEnum() {
        return serverStatusEnum;
    }

    public int getCurrentTerm() {
        return this.raftServerMetaDataPersistentModule.getCurrentTerm();
    }

    public Integer getVotedFor() {
        return this.raftServerMetaDataPersistentModule.getVotedFor();
    }

    public void setCurrentTerm(int currentTerm) {
        this.raftServerMetaDataPersistentModule.setCurrentTerm(currentTerm);
    }

    public void setVotedFor(Integer votedFor) {
        this.raftServerMetaDataPersistentModule.setVotedFor(votedFor);
    }

    public Integer getCurrentLeader() {
        return currentLeader;
    }

    public void setCurrentLeader(Integer currentLeader) {
        this.currentLeader = currentLeader;
    }

    public List<RaftService> getOtherNodeInCluster() {
        return otherNodeInCluster;
    }

    public void setOtherNodeInCluster(List<RaftService> otherNodeInCluster) {
        this.otherNodeInCluster = otherNodeInCluster;
    }

    public RaftLeaderElectionModule getRaftLeaderElectionModule() {
        return raftLeaderElectionModule;
    }

    public RaftHeartBeatBroadcastModule getRaftHeartBeatBroadcastModule() {
        return raftHeartBeatBroadcastModule;
    }

    public LogModule getLogModule() {
        return logModule;
    }

    public SnapshotModule getSnapshotModule() {
        return snapshotModule;
    }

    public KVReplicationStateMachine getKvReplicationStateMachine() {
        return kvReplicationStateMachine;
    }

    public Map<RaftService, Long> getNextIndexMap() {
        return nextIndexMap;
    }

    public Map<RaftService, Long> getMatchIndexMap() {
        return matchIndexMap;
    }

    // ============================= public的业务接口 =================================

    /**
     * 清空votedFor
     * */
    public void cleanVotedFor(){
        this.raftServerMetaDataPersistentModule.setVotedFor(null);
    }

    /**
     * rpc交互时任期高于当前节点任期的处理
     * (同时包括接到的rpc请求以及得到的rpc响应，只要另一方的term高于当前节点的term，就更新当前节点的term值)
     *
     * Current terms are exchanged whenever servers communicate;
     * if one server’s current term is smaller than the other’s, then it updates its current term to the larger value.
     *
     * @return 是否转换为了follower
     * */
    public boolean processCommunicationHigherTerm(int rpcOtherTerm){
        // If RPC request or response contains term T > currentTerm:
        // set currentTerm = T, convert to follower (§5.1)
        if(rpcOtherTerm > this.getCurrentTerm()) {
            this.setCurrentTerm(rpcOtherTerm);
            this.setServerStatusEnum(ServerStatusEnum.FOLLOWER);

            return true;
        }else{
            return false;
        }
    }
}
