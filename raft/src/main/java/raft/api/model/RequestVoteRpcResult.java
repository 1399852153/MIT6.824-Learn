package raft.api.model;

/**
 * 请求投票的RPC接口响应对象
 * */
public class RequestVoteRpcResult {

    /**
     * 被调用者当前的任期值
     * */
    private final int term;

    /**
     * 是否同意投票给调用者
     * */
    private final boolean voteGranted;

    public RequestVoteRpcResult(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public int getTerm() {
        return term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    @Override
    public String toString() {
        return "RequestVoteRpcResult{" +
            "term=" + term +
            ", voteGranted=" + voteGranted +
            '}';
    }
}
