package raft.rpc;

public class RpcRaftNode5 {

    /**
     * rpc raft
     * */
    public static void main(String[] args) {
        RaftClusterGlobalConfig.initRaftRpcServer(5);
    }
}
