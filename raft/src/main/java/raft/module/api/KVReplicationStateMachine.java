package raft.module.api;

import raft.api.command.SetCommand;
import raft.api.model.LogEntry;

/**
 * K/V 复制状态机
 * */
public interface KVReplicationStateMachine {

    /**
     * 应用日志条目到状态机中（写操作）
     * */
    void apply(SetCommand setCommand);

    /**
     * 简单起见，直接提供一个读的方法，而不是另外用别的模块来做
     * */
    String get(String key);
}
