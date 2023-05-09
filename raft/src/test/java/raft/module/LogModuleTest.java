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
import java.util.List;

public class LogModuleTest {

    @Test
    public void test() throws Exception {
        int serverId = 99999;
        RaftNodeConfig raftNodeConfig = new RaftNodeConfig(serverId);
        RaftServer raftServer = new RaftServer(new RaftConfig(raftNodeConfig, Arrays.asList(raftNodeConfig)));
        raftServer.setOtherNodeInCluster(new ArrayList<>());
        LogModule logModule = new LogModule(raftServer);
        logModule.clean();

        logModule = new LogModule(raftServer);
        {
            LogEntry logEntry = logModule.readLocalLog(1);
            Assert.assertNull(logEntry);
        }

        {
            LogEntry newLogEntry = new LogEntry();
            newLogEntry.setLogIndex(1);
            newLogEntry.setLogTerm(1);
            newLogEntry.setCommand(new SetCommand("k1","v1"));
            logModule.writeLocalLog(newLogEntry);

            LogEntry logEntry = logModule.readLocalLog(1);
            Assert.assertEquals(logEntry.getLogIndex(),1);
            Assert.assertEquals(logEntry.getLogTerm(),1);
        }

        {
            LogEntry newLogEntry = new LogEntry();
            newLogEntry.setLogIndex(2);
            newLogEntry.setLogTerm(1);
            newLogEntry.setCommand(new SetCommand("k1","v2"));
            logModule.writeLocalLog(newLogEntry);

            LogEntry logEntry = logModule.readLocalLog(2);
            Assert.assertEquals(logEntry.getLogIndex(),2);
            Assert.assertEquals(logEntry.getLogTerm(),1);

            LogEntry logEntry2 = logModule.readLocalLog(1);
            Assert.assertEquals(logEntry2.getLogIndex(),1);
            Assert.assertEquals(logEntry2.getLogTerm(),1);
        }

        {
            LogEntry newLogEntry = new LogEntry();
            newLogEntry.setLogIndex(3);
            newLogEntry.setLogTerm(1);
            newLogEntry.setCommand(new SetCommand("k1","v3"));
            logModule.writeLocalLog(newLogEntry);

            List<LogEntry> logEntryList = logModule.readLocalLog(1,2);

            Assert.assertEquals(logEntryList.get(0).getLogIndex(),1);
            Assert.assertEquals(logEntryList.get(0).getLogTerm(),1);

            Assert.assertEquals(logEntryList.get(1).getLogIndex(),2);
            Assert.assertEquals(logEntryList.get(1).getLogTerm(),1);

        }

        {
            LogEntry newLogEntry = new LogEntry();
            newLogEntry.setLogIndex(4);
            newLogEntry.setLogTerm(1);
            newLogEntry.setCommand(new SetCommand("k1","v4"));
            logModule.writeLocalLog(newLogEntry);

            LogEntry newLogEntry2 = new LogEntry();
            newLogEntry2.setLogIndex(5);
            newLogEntry2.setLogTerm(1);
            newLogEntry2.setCommand(new SetCommand("k1","v5"));
            logModule.writeLocalLog(newLogEntry2);

            List<LogEntry> logEntryList = logModule.readLocalLog(2,5);

            Assert.assertEquals(logEntryList.get(0).getLogIndex(),2);
            Assert.assertEquals(logEntryList.get(0).getLogTerm(),1);

            Assert.assertEquals(logEntryList.get(1).getLogIndex(),3);
            Assert.assertEquals(logEntryList.get(1).getLogTerm(),1);

            Assert.assertEquals(logEntryList.get(2).getLogIndex(),4);
            Assert.assertEquals(logEntryList.get(2).getLogTerm(),1);

            Assert.assertEquals(logEntryList.get(3).getLogIndex(),5);
            Assert.assertEquals(logEntryList.get(3).getLogTerm(),1);

        }

        logModule.clean();
    }
}
