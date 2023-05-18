package raft.rpc;

import org.junit.Assert;
import raft.RaftClient;
import raft.api.command.GetCommand;
import raft.api.command.SetCommand;

public class RpcClientTest {


    public static void main(String[] args) {
        RaftClient raftClient = new RaftClient(RaftClusterGlobalConfig.raftNodeConfigList,RaftClusterGlobalConfig.registry);

        raftClient.init();

//        {
//            raftClient.doRequest(new SetCommand("k1", "v1"));
//
//            String result = raftClient.doRequest(new GetCommand("k1"));
//            Assert.assertEquals(result, "v1");
//        }

        {
            raftClient.doRequest(new SetCommand("k2", "v2"));

            String result = raftClient.doRequest(new GetCommand("k2"));
            Assert.assertEquals(result, "v2");
        }

        System.out.println("all finished!");

    }
}
