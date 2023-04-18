package raft.api.model;

/**
 * 追加日志条目的RPC接口响应对象
 * */
public class AppendEntriesRpcResult {

    /**
     * 被调用者当前的任期值
     * */
    private final int term;

    /**
     * 是否处理成功
     * */
    private final boolean success;

    public AppendEntriesRpcResult(int term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public int getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }
}
