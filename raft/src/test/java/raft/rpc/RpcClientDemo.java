package raft.rpc;

import org.junit.Assert;
import raft.RaftClient;
import raft.api.command.GetCommand;
import raft.api.command.SetCommand;
import raft.rpc.config.RaftClusterGlobalConfig;

public class RpcClientDemo {


    public static void main(String[] args) {
        RaftClient raftClient = new RaftClient(RaftClusterGlobalConfig.registry);

        raftClient.init();

        {
            raftClient.doRequest(new SetCommand("k1", "v1"));

            String result = raftClient.doRequest(new GetCommand("k1"));
            Assert.assertEquals(result, "v1");
        }

        {
            raftClient.doRequest(new SetCommand("k2", "v2"));

            String result = raftClient.doRequest(new GetCommand("k2"));
            Assert.assertEquals(result, "v2");
        }

        {
            raftClient.doRequest(new SetCommand("k3", "v3"));

            String result = raftClient.doRequest(new GetCommand("k3"));
            Assert.assertEquals(result, "v3");
        }

        System.out.println("all finished!");

    }
}
