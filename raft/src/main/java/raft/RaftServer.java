package raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.api.model.AppendEntriesRpcParam;
import raft.api.model.AppendEntriesRpcResult;
import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.common.enums.ServerStatusEnum;
import raft.api.model.LogEntry;
import raft.api.service.RaftService;
import raft.module.RaftHeartBeatBroadcastModule;
import raft.module.RaftLeaderElectionModule;
import raft.util.Range;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 日志条目列表
     * todo 先不考虑日志持久化的处理
     * */
    private List<LogEntry> logEntryList = new ArrayList<>();

    private RaftLeaderElectionModule raftLeaderElectionModule;
    private RaftHeartBeatBroadcastModule raftHeartBeatBroadcastModule;

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

        raftLeaderElectionModule = new RaftLeaderElectionModule(this);
        raftHeartBeatBroadcastModule = new RaftHeartBeatBroadcastModule(this);

        logger.info("raft server init end! otherNodeInCluster={}, currentServerId={}",otherNodeInCluster,serverId);
    }

    @Override
    public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        RequestVoteRpcResult requestVoteRpcResult = raftLeaderElectionModule.requestVoteProcess(requestVoteRpcParam);

        logger.info("do requestVote requestVoteRpcParam={},requestVoteRpcResult={}, currentServerId={}",
            requestVoteRpcParam,requestVoteRpcResult,this.serverId);

        return requestVoteRpcResult;
    }

    @Override
    public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        AppendEntriesRpcResult appendEntriesRpcResult = doAppendEntries(appendEntriesRpcParam);
        logger.info("do appendEntries appendEntriesRpcParam={},appendEntriesRpcResult={},currentServerId={}",
            appendEntriesRpcParam,appendEntriesRpcResult,this.serverId);
        return appendEntriesRpcResult;
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

        // todo
        return null;
    }

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

    public void cleanVotedFor(){
        this.votedFor = null;
    }

    public RaftLeaderElectionModule getRaftLeaderElectionModule() {
        return raftLeaderElectionModule;
    }
}
