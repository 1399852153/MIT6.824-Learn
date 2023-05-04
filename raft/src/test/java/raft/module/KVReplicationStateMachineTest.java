package raft.module;

import org.junit.Assert;
import org.junit.Test;
import raft.api.command.SetCommand;

import java.io.File;
import java.io.IOException;

public class KVReplicationStateMachineTest {

    @Test
    public void TestSimpleReplicationStateMachine() throws IOException {
        String path = System.getProperty("user.dir") + File.separator + "testSimpleReplicationStateMachine.txt";
        File file = new File(path);
        file.delete();
        file.createNewFile();

        {
            SimpleReplicationStateMachine simpleReplicationStateMachine = new SimpleReplicationStateMachine(file);

            Assert.assertNull(simpleReplicationStateMachine.get("k1"));
            simpleReplicationStateMachine.apply(new SetCommand("k1", "v1"));
            Assert.assertEquals("v1", simpleReplicationStateMachine.get("k1"));
            Assert.assertNull(simpleReplicationStateMachine.get("kn"));

            simpleReplicationStateMachine.apply(new SetCommand("k2", "v2"));
        }

        {
            SimpleReplicationStateMachine simpleReplicationStateMachine = new SimpleReplicationStateMachine(file);

            Assert.assertEquals("v1", simpleReplicationStateMachine.get("k1"));
            Assert.assertEquals("v2", simpleReplicationStateMachine.get("k2"));
            Assert.assertNull(simpleReplicationStateMachine.get("kn"));
        }

        file.delete();
    }
}
