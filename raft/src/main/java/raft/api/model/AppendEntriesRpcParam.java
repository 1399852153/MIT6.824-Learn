package raft.api.model;

import java.util.List;

/**
 * 追加日志条目的RPC接口参数对象
 * */
public class AppendEntriesRpcParam {

    /**
     * 当前leader的任期值
     * */
    private int term;

    /**
     * leader的id
     * */
    private int leaderId;

    /**
     * 当前请求日志条目的前一个日志的索引编号
     *
     * index of log entry immediately preceding new ones
     * */
    private int prevLogIndex;

    /**
     * prevLogIndex对应日志条目的任期值
     *
     * term of prevLogIndex entry
     * */
    private int prevLogTerm;

    /**
     * 本次请求需要追加的新日志条目
     * */
    private List<LogEntry> entries;

    /**
     * leader当前已提交的日志索引
     *
     * leader’s commitIndex
     * */
    private int leaderCommit;

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public int getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(int leaderId) {
        this.leaderId = leaderId;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(int prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(int leaderCommit) {
        this.leaderCommit = leaderCommit;
    }
}
