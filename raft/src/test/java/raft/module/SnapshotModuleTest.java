package raft.module;

import org.junit.Assert;
import org.junit.Test;
import raft.RaftServer;
import raft.api.model.*;
import raft.api.service.RaftService;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.common.model.RaftSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SnapshotModuleTest {

    @Test
    public void test() {
        int serverId = 123;
        RaftNodeConfig raftNodeConfig = new RaftNodeConfig(serverId);
        RaftServer raftServer = new RaftServer(new RaftConfig(raftNodeConfig, Arrays.asList(raftNodeConfig)));

        SnapshotModule snapshotModule = new SnapshotModule(raftServer);

        Assert.assertNull(snapshotModule.readSnapshot());

        RaftSnapshot raftSnapshot = new RaftSnapshot();
        raftSnapshot.setSnapshotData("aaa".getBytes(StandardCharsets.UTF_8));
        raftSnapshot.setLastIncludedTerm(1);
        raftSnapshot.setLastIncludedIndex(10);

        snapshotModule.persistentNewSnapshotFile(raftSnapshot);

        RaftSnapshot readSnapshot = snapshotModule.readSnapshot();
        Assert.assertEquals(new String(readSnapshot.getSnapshotData(), StandardCharsets.UTF_8), "aaa");
        Assert.assertEquals(readSnapshot.getLastIncludedIndex(), 10);
        Assert.assertEquals(readSnapshot.getLastIncludedTerm(), 1);

        snapshotModule.clean();
    }

    @Test
    public void testSplitRpc() {
        int serverId = 123;
        RaftNodeConfig raftNodeConfig = new RaftNodeConfig(serverId);
        RaftConfig raftConfig = new RaftConfig(raftNodeConfig, Arrays.asList(raftNodeConfig));
        // 一次传输3个字节
        raftConfig.setInstallSnapshotBlockSize(3);
        RaftServer raftServer = new RaftServer(raftConfig);

        List<Byte> raftSnapshotReceived = new ArrayList<>();
        RaftService raftService = new RaftService() {
            @Override
            public ClientRequestResult clientRequest(ClientRequestParam clientRequestParam) {
                return null;
            }

            @Override
            public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
                return null;
            }

            @Override
            public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
                return null;
            }

            @Override
            public InstallSnapshotRpcResult installSnapshot(InstallSnapshotRpcParam installSnapshotRpcParam) {
                for(byte b : installSnapshotRpcParam.getData()){
                    raftSnapshotReceived.add(b);
                }
                return new InstallSnapshotRpcResult(installSnapshotRpcParam.getTerm());
            }
        };

        byte[] snapshotData = "aaabbbcccdddeee1".getBytes(StandardCharsets.UTF_8);
        RaftSnapshot raftSnapshot = new RaftSnapshot();
        raftSnapshot.setSnapshotData(snapshotData);
        raftSnapshot.setLastIncludedTerm(0);
        raftSnapshot.setLastIncludedIndex(10);

        LogModule.doInstallSnapshotRpc(raftService,raftSnapshot,raftServer);
        // 简单起见，就比下长度是否一致
        Assert.assertEquals(snapshotData.length,raftSnapshotReceived.size());
    }
}
