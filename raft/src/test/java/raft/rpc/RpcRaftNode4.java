package raft.rpc;

public class RpcRaftNode4 {

    /**
     * rpc raft
     * */
    public static void main(String[] args) {
        RaftClusterGlobalConfig.initRaftRpcServer(4);
    }
}
