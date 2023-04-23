package raft.rpc;

public class RpcRaftNode3 {

    /**
     * rpc raft
     */
    public static void main(String[] args) {
        RaftClusterGlobalConfig.initRaftRpcServer(3);
    }
}
