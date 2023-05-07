package raft.module;

import org.junit.Assert;
import org.junit.Test;
import raft.api.command.SetCommand;

public class KVReplicationStateMachineTest {

    @Test
    public void TestSimpleReplicationStateMachine() {
        int serverId = 8888;

        {
            SimpleReplicationStateMachine simpleReplicationStateMachine = new SimpleReplicationStateMachine(serverId);

            Assert.assertNull(simpleReplicationStateMachine.get("k1"));
            simpleReplicationStateMachine.apply(new SetCommand("k1", "v1"));
            Assert.assertEquals("v1", simpleReplicationStateMachine.get("k1"));
            Assert.assertNull(simpleReplicationStateMachine.get("kn"));

            simpleReplicationStateMachine.apply(new SetCommand("k2", "v2"));
        }

        {
            SimpleReplicationStateMachine simpleReplicationStateMachine = new SimpleReplicationStateMachine(serverId);

            Assert.assertEquals("v1", simpleReplicationStateMachine.get("k1"));
            Assert.assertEquals("v2", simpleReplicationStateMachine.get("k2"));
            Assert.assertNull(simpleReplicationStateMachine.get("kn"));
        }

        SimpleReplicationStateMachine simpleReplicationStateMachine = new SimpleReplicationStateMachine(serverId);
        simpleReplicationStateMachine.clean();
    }
}
