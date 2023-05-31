package raft.module;

import org.junit.Assert;
import org.junit.Test;
import raft.RaftServer;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.common.model.RaftSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SnapshotModuleTest {

    @Test
    public void test(){
        int serverId = 123;
        RaftNodeConfig raftNodeConfig = new RaftNodeConfig(serverId);
        RaftServer raftServer = new RaftServer(new RaftConfig(raftNodeConfig, Arrays.asList(raftNodeConfig)));

        SnapshotModule snapshotModule = new SnapshotModule(raftServer);

        Assert.assertNull(snapshotModule.readLatestSnapshot());

        RaftSnapshot raftSnapshot = new RaftSnapshot();
        raftSnapshot.setSnapshotData("aaa".getBytes(StandardCharsets.UTF_8));
        raftSnapshot.setLastIncludedTerm(1);
        raftSnapshot.setLastIncludedIndex(10);

        snapshotModule.persistentNewSnapshotFile(raftSnapshot);

        RaftSnapshot readSnapshot = snapshotModule.readLatestSnapshot();
        Assert.assertEquals(new String(readSnapshot.getSnapshotData(),StandardCharsets.UTF_8),"aaa");
        Assert.assertEquals(readSnapshot.getLastIncludedIndex(),10);
        Assert.assertEquals(readSnapshot.getLastIncludedTerm(),1);

        snapshotModule.clean();
    }
}
