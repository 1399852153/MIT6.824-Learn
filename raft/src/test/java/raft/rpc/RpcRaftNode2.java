package raft.rpc;

public class RpcRaftNode2 {

    /**
     * rpc raft
     * */
    public static void main(String[] args) {
        RaftClusterGlobalConfig.initRaftRpcServer(2);
    }
}
