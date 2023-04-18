package raft;

import raft.api.model.AppendEntriesRpcParam;
import raft.api.model.AppendEntriesRpcResult;
import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.common.config.RaftConfig;
import raft.common.enums.ServerStatusEnum;
import raft.api.model.LogEntry;
import raft.api.service.RaftService;

import java.util.ArrayList;
import java.util.List;

public class RaftServer implements RaftService {

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
     * 集群中的其它raft节点服务
     * */
    private List<RaftServer> otherNodeInCluster;

    /**
     * 日志条目列表
     * todo 先不考虑日志持久化的处理
     * */
    private final List<LogEntry> logEntryList = new ArrayList<>();

    private final RaftLeaderElectionModule raftLeaderElectionModule = new RaftLeaderElectionModule(this);

    public RaftServer(int serverId, RaftConfig raftConfig, List<RaftServer> otherNodeInCluster) {
        this.serverId = serverId;
        this.raftConfig = raftConfig;
        // 初始化时都是follower
        this.serverStatusEnum = ServerStatusEnum.FOLLOWER;
        // 当前任期值为0
        this.currentTerm = 0;

        // 集群中的其它节点服务
        this.otherNodeInCluster = otherNodeInCluster;
    }

    @Override
    public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        return raftLeaderElectionModule.requestVoteProcess(requestVoteRpcParam);
    }

    @Override
    public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        if(appendEntriesRpcParam.getTerm() < this.currentTerm){
            // Reply false if term < currentTerm (§5.1)
            // 拒绝处理任期低于自己的老leader的请求
            return new AppendEntriesRpcResult(this.currentTerm,false);
        }

        if(appendEntriesRpcParam.getTerm() >= this.currentTerm){
            // appendEntries请求中任期值如果大于自己，说明已经有一个更新的leader了，自己转为follower，并且以对方更大的任期为准
            this.serverStatusEnum = ServerStatusEnum.FOLLOWER;
            this.currentTerm = appendEntriesRpcParam.getTerm();
        }

        if(appendEntriesRpcParam.getEntries().isEmpty()){
            // entries为空，说明是心跳请求，刷新一下最近收到心跳的时间
            raftLeaderElectionModule.refreshLastHeartBeat();

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

    public List<RaftServer> getOtherNodeInCluster() {
        return otherNodeInCluster;
    }

    public void setOtherNodeInCluster(List<RaftServer> otherNodeInCluster) {
        this.otherNodeInCluster = otherNodeInCluster;
    }
}
