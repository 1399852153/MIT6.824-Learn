package raft;

import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.common.config.RaftConfig;
import raft.common.enums.ServerStatusEnum;
import raft.common.model.LogEntry;
import service.RaftService;

import java.util.ArrayList;
import java.util.List;

public class RaftServer implements RaftService {

    /**
     * 当前服务节点的id(集群内全局唯一)
     * */
    private int serverId;

    /**
     * Raft服务端配置
     * */
    private RaftConfig raftConfig;

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
     * 日志条目列表
     * todo 先不考虑日志持久化的处理
     * */
    private final List<LogEntry> logEntryList = new ArrayList<>();

    private final RaftLeaderElectionHandler raftLeaderElectionHandler = new RaftLeaderElectionHandler(this);

    @Override
    public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        return raftLeaderElectionHandler.requestVoteProcess(requestVoteRpcParam);
    }

    public int getServerId() {
        return serverId;
    }

    public RaftConfig getRaftConfig() {
        return raftConfig;
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
}
