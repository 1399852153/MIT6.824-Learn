package raft.rpc;

public class RpcRaftNode1 {

    /**
     * rpc raft
     * */
    public static void main(String[] args) {
        RaftClusterGlobalConfig.initRaftRpcServer(1);
    }
}
