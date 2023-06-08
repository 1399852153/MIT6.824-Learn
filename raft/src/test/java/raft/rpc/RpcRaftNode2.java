package raft.rpc;

import raft.rpc.config.RaftClusterGlobalConfig;

public class RpcRaftNode2 {

    /**
     * rpc raft
     * */
    public static void main(String[] args) {
        RaftClusterGlobalConfig.initRaftRpcServer(2);
    }
}
