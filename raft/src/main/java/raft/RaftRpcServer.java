package raft;

import raft.common.config.RaftConfig;

/**
 * raft的rpc服务
 * */
public class RaftRpcServer extends RaftServer {

    public RaftRpcServer(RaftConfig raftConfig) {
        super(raftConfig);
    }
}
