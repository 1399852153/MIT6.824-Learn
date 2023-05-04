package raft.api.model;

import raft.api.command.Command;

/**
 * 日志条目
 * */
public class LogEntry {

    /**
     * 发布日志时的leader的任期编号
     * */
    private int logTerm;

    /**
     * 日志的索引编号
     * */
    private int logIndex;

    /**
     * 具体作用在状态机上的指令
     * */
    private Command command;

    public int getLogTerm() {
        return logTerm;
    }

    public void setLogTerm(int logTerm) {
        this.logTerm = logTerm;
    }

    public int getLogIndex() {
        return logIndex;
    }

    public void setLogIndex(int logIndex) {
        this.logIndex = logIndex;
    }

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }
}
