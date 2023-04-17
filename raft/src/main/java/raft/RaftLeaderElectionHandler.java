package raft;

import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;

/**
 * Raft服务器的leader选举模块
 * */
public class RaftLeaderElectionHandler {

    private final RaftServer currentServer;

    public RaftLeaderElectionHandler(RaftServer currentServer) {
        this.currentServer = currentServer;
    }

    /**
     * 处理投票请求
     * 注意：synchronized修饰防止不同candidate并发的投票申请处理，以FIFO的方式处理
     * */
    public synchronized RequestVoteRpcResult requestVoteProcess(RequestVoteRpcParam requestVoteRpcParam){
        if(this.currentServer.getCurrentTerm() > requestVoteRpcParam.getTerm()){
            // Reply false if term < currentTerm (§5.1)
            // 发起投票的candidate任期小于当前服务器任期，拒绝投票给它
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }

        if(this.currentServer.getVotedFor() != null && this.currentServer.getVotedFor() != requestVoteRpcParam.getCandidateId()){
            // If votedFor is null or candidateId（取反的卫语句）
            // 当前服务器已经把票投给了别人,拒绝投票给发起投票的candidate
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }

        // todo 先不考虑日志条目索引以及任期值是否满足条件的情况

        // 设置投票给了谁
        this.currentServer.setVotedFor(requestVoteRpcParam.getCandidateId());
        return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),true);
    }
}
