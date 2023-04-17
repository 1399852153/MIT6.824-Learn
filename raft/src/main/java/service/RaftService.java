package service;

import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;

public interface RaftService {

    /**
     * 请求投票接口
     *
     * Receiver implementation:
     * 1. Reply false if term < currentTerm (§5.1)
     * 2. If votedFor is null or candidateId, and candidate’s log is at
     * least as up-to-date as receiver’s log, grant vote (§5.2, §5.4)
     *
     * 接受者需要实现以下功能：
     * 1. 如果参数中的任期值term小于当前自己的任期值currentTerm，则返回false不同意投票给调用者
     * 2. 如果自己还没有投票(FIFO)或者已经投票给了candidateId对应的节点(幂等)，
     *    并且候选人的日志至少与被调用者的日志一样新(比较日志的任期值和索引值)，则投票给调用者(返回值里voteGranted为true)
     * */
    RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam);
}
