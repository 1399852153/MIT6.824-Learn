package raft.module;

import org.junit.Assert;
import org.junit.Test;
import raft.RaftServer;
import raft.api.command.SetCommand;
import raft.api.model.LogEntry;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;

import java.util.ArrayList;
import java.util.Arrays;

public class LogModuleTest {

    @Test
    public void test() throws Exception {
        int serverId = 99999;
        RaftNodeConfig raftNodeConfig = new RaftNodeConfig(serverId);
        RaftServer raftServer = new RaftServer(new RaftConfig(raftNodeConfig, Arrays.asList(raftNodeConfig)));
        LogModule logModule = new LogModule(raftServer);
        logModule.clean();

        logModule = new LogModule(raftServer);
        {
            LogEntry logEntry = logModule.readLog(1);
            Assert.assertNull(logEntry);
        }

        {
            LogEntry newLogEntry = new LogEntry();
            newLogEntry.setLogIndex(1);
            newLogEntry.setLogTerm(1);
            newLogEntry.setCommand(new SetCommand("k1","v1"));
            logModule.writeLog(newLogEntry);

            LogEntry logEntry = logModule.readLog(1);
            Assert.assertEquals(logEntry.getLogIndex(),1);
            Assert.assertEquals(logEntry.getLogTerm(),1);
        }

        {
            LogEntry newLogEntry = new LogEntry();
            newLogEntry.setLogIndex(2);
            newLogEntry.setLogTerm(1);
            newLogEntry.setCommand(new SetCommand("k1","v2"));
            logModule.writeLog(newLogEntry);

            LogEntry logEntry = logModule.readLog(2);
            Assert.assertEquals(logEntry.getLogIndex(),2);
            Assert.assertEquals(logEntry.getLogTerm(),1);

            LogEntry logEntry2 = logModule.readLog(1);
            Assert.assertEquals(logEntry2.getLogIndex(),1);
            Assert.assertEquals(logEntry2.getLogTerm(),1);
        }

        logModule.clean();
    }
}
