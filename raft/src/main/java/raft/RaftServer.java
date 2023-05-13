package raft;

import myrpc.common.URLAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.api.command.GetCommand;
import raft.api.command.SetCommand;
import raft.api.model.*;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.common.enums.ServerStatusEnum;
import raft.api.service.RaftService;
import raft.exception.MyRaftException;
import raft.module.LogModule;
import raft.module.RaftHeartBeatBroadcastModule;
import raft.module.RaftLeaderElectionModule;
import raft.module.SimpleReplicationStateMachine;
import raft.module.api.KVReplicationStateMachine;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 当前服务器的任期值
     * */
    private volatile int currentTerm;

    /**
     * 当前服务器在此之前投票给了谁？
     * (候选者的serverId，如果还没有投递就是null)
     * */
    private volatile Integer votedFor;

    /**
     * 当前服务认为的leader节点的Id
     * */
    private volatile Integer currentLeader;

    /**
     * 集群中的其它raft节点服务
     * */
    protected List<RaftService> otherNodeInCluster;

    private LogModule logModule;
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
        // 当前任期值为0
        this.currentTerm = 0;
    }

    public void init(List<RaftService> otherNodeInCluster){
        // 集群中的其它节点服务
        this.otherNodeInCluster = otherNodeInCluster;

        try {
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
    public synchronized RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        RequestVoteRpcResult requestVoteRpcResult = raftLeaderElectionModule.requestVoteProcess(requestVoteRpcParam);

        processCommunicationHigherTerm(requestVoteRpcParam.getTerm());

        logger.info("do requestVote requestVoteRpcParam={},requestVoteRpcResult={}, currentServerId={}",
            requestVoteRpcParam,requestVoteRpcResult,this.serverId);

        return requestVoteRpcResult;
    }

    @Override
    public synchronized AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        AppendEntriesRpcResult appendEntriesRpcResult = doAppendEntries(appendEntriesRpcParam);

        processCommunicationHigherTerm(appendEntriesRpcParam.getTerm());

        logger.info("do appendEntries appendEntriesRpcParam={},appendEntriesRpcResult={},currentServerId={}",
            appendEntriesRpcParam,appendEntriesRpcResult,this.serverId);
        return appendEntriesRpcResult;
    }

    @Override
    public synchronized ClientRequestResult clientRequest(ClientRequestParam clientRequestParam) {
        // 不是leader
        if(this.serverStatusEnum != ServerStatusEnum.LEADER){
            if(this.currentLeader == null){
                // 自己不是leader，也不知道谁是leader直接报错
                throw new MyRaftException("current node not leader，and leader is null!" + this.serverId);
            }

            RaftNodeConfig leaderConfig = this.raftConfig.getRaftNodeConfigList()
                    .stream().filter(item->item.getServerId() == this.currentLeader).findAny().get();

            // 把自己认为的leader告诉客户端
            ClientRequestResult clientRequestResult = new ClientRequestResult();
            clientRequestResult.setLeaderAddress(new URLAddress(leaderConfig.getIp(),leaderConfig.getPort()));
            return clientRequestResult;
        }

        // 是leader，处理读请求
        if(clientRequestParam.getCommand() instanceof GetCommand){
            GetCommand getCommand = (GetCommand) clientRequestParam.getCommand();

            // 直接从状态机中读取就行
            String value = this.kvReplicationStateMachine.get(getCommand.getKey());

            ClientRequestResult clientRequestResult = new ClientRequestResult();
            clientRequestResult.setSuccess(true);
            clientRequestResult.setValue(value);
            return clientRequestResult;
        }

        // 自己是leader，需要处理客户端的写请求

        // 构造新的日志条目
        LogEntry newLogEntry = new LogEntry();
        newLogEntry.setLogTerm(this.currentTerm);
        // 新日志的索引号为当前最大索引编号+1
        newLogEntry.setLogIndex(this.logModule.getLastIndex() + 1);
        newLogEntry.setCommand(clientRequestParam.getCommand());

        // 预写入日志
        logModule.writeLocalLog(newLogEntry);
        logModule.setLastIndex(newLogEntry.getLogIndex());

        List<AppendEntriesRpcResult> appendEntriesRpcResultList = logModule.replicationLogEntry(newLogEntry);

        // successNum需要加上自己的1票
        long successNum = appendEntriesRpcResultList.stream().filter(AppendEntriesRpcResult::isSuccess).count() + 1;
        if(successNum >= this.raftConfig.getMajorityNum()){
            // If command received from client: append entry to local log, respond after entry applied to state machine (§5.3)

            // 成功复制到多数节点

            // 设置最新的已提交索引编号
            logModule.setLastCommittedIndex(newLogEntry.getLogIndex());
            // 作用到状态机上
            this.kvReplicationStateMachine.apply((SetCommand) newLogEntry.getCommand());
            // todo lastApplied为什么不需要持久化？ 状态机指令的应用和更新lastApplied非原子性会产生什么问题？
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
        if(appendEntriesRpcParam.getTerm() < this.currentTerm){
            // Reply false if term < currentTerm (§5.1)
            // 拒绝处理任期低于自己的老leader的请求
            return new AppendEntriesRpcResult(this.currentTerm,false);
        }

        if(appendEntriesRpcParam.getTerm() >= this.currentTerm){
            // appendEntries请求中任期值如果大于自己，说明已经有一个更新的leader了，自己转为follower，并且以对方更大的任期为准
            this.serverStatusEnum = ServerStatusEnum.FOLLOWER;
            this.currentLeader = appendEntriesRpcParam.getLeaderId();
            this.currentTerm = appendEntriesRpcParam.getTerm();
        }

        if(appendEntriesRpcParam.getEntries() == null){
            // 来自leader的心跳处理，清理掉之前选举的votedFor
            this.cleanVotedFor();
            // entries为空，说明是心跳请求，刷新一下最近收到心跳的时间
            raftLeaderElectionModule.refreshLastHeartbeatTime();

            // 心跳请求，直接返回
            return new AppendEntriesRpcResult(this.currentTerm,true);
        }

        // logEntries不为空，是真实的日志复制rpc

        // AppendEntry可靠性校验，如果prevLogIndex和prevLogTerm不匹配，则需要返回false，让leader发更早的日志过来
        {
            LogEntry localLogEntry = logModule.readLocalLog(appendEntriesRpcParam.getPrevLogIndex());
            if(localLogEntry == null){
                // 当前节点日志条目为空(默认任期为-1，这个是约定)
                localLogEntry = new LogEntry();
                localLogEntry.setLogIndex(-1);
                localLogEntry.setLogTerm(-1);
            }

            if (localLogEntry.getLogTerm() == appendEntriesRpcParam.getPrevLogTerm()) {
                //  Reply false if log doesn’t contain an entry at prevLogIndex
                //  whose term matches prevLogTerm (§5.3)
                //  本地日志和参数中的PrevLogIndex和PrevLogTerm对不上(对应日志不存在，或者任期对不上)
                return new AppendEntriesRpcResult(this.currentTerm, false);
            }
        }

        // 简单起见，先只考虑一次rpc仅单个entry的场景
        LogEntry newLogEntry = appendEntriesRpcParam.getEntries().get(0);

        AppendEntriesRpcResult appendEntriesRpcResult;
        // 新日志的复制操作
        LogEntry localLogEntry = logModule.readLocalLog(newLogEntry.getLogIndex());
        if(localLogEntry == null){
            // 本地日志不存在，追加写入
            // Append any new entries not already in the log
            logModule.writeLocalLog(newLogEntry);
            logModule.setLastIndex(newLogEntry.getLogIndex());

            appendEntriesRpcResult = new AppendEntriesRpcResult(this.currentTerm, true);
        }else{
            if(localLogEntry.getLogTerm() == newLogEntry.getLogTerm()){
                logger.info("local log existed and term match. return success");
                // 本地日志存在，且任期一致,幂等返回成功
                appendEntriesRpcResult = new AppendEntriesRpcResult(this.currentTerm, true);
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
                logModule.setLastIndex(newLogEntry.getLogIndex());

                // 返回成功
                appendEntriesRpcResult = new AppendEntriesRpcResult(this.currentTerm, true);
            }
        }

        // If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if(appendEntriesRpcParam.getLeaderCommit() > logModule.getLastCommittedIndex()){
            // 如果leaderCommit更大，说明当前节点的同步进度慢于leader，以新的entry里的index为准(更高的index还没有在本地保存(因为上面的appendEntry有效性检查))
            // 如果index of last new entry更大，说明当前节点的同步进度是和leader相匹配的，commitIndex以leader最新提交的为准
            long lastCommittedIndex = Math.min(appendEntriesRpcParam.getLeaderCommit(), newLogEntry.getLogIndex());
            long lastApplied = logModule.getLastApplied();

            // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
            if(lastApplied < lastCommittedIndex){
                // 作用在状态机上的日志编号低于集群中已提交的日志编号，需要把这些已提交的日志都作用到状态机上去

                // 全读取出来(读取出来是按照index从小到大排好序的)
                List<LogEntry> logEntryList = logModule.readLocalLog(lastApplied+1,lastCommittedIndex);
                for(LogEntry logEntry : logEntryList){
                    // 按照顺序依次作用到状态机中
                    this.kvReplicationStateMachine.apply((SetCommand) logEntry.getCommand());
                }
            }

            this.logModule.setLastCommittedIndex(lastCommittedIndex);
            this.logModule.setLastApplied(lastCommittedIndex);
        }

        return appendEntriesRpcResult;
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
        return currentTerm;
    }

    public Integer getVotedFor() {
        return votedFor;
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
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
        this.votedFor = null;
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
    public synchronized boolean processCommunicationHigherTerm(int rpcOtherTerm){
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
