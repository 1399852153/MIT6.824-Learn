package raft.rpc;

import myrpc.registry.Registry;
import myrpc.registry.RegistryConfig;
import myrpc.registry.RegistryFactory;
import myrpc.registry.enums.RegistryCenterTypeEnum;
import raft.RaftRpcServer;
import raft.api.service.RaftService;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.util.Range;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RaftClusterGlobalConfig {

    /**
     * 简单起见注册中心统一配置
     * */
    public static Registry registry = RegistryFactory.getRegistry(
        new RegistryConfig(RegistryCenterTypeEnum.ZOOKEEPER.getCode(), "127.0.0.1:2181"));

    /**
     * raft的集群配置
     * */
    public static final List<RaftNodeConfig> raftNodeConfigList = Arrays.asList(
        new RaftNodeConfig(1,"127.0.0.1",8001)
        ,new RaftNodeConfig(2,"127.0.0.1",8002)
        ,new RaftNodeConfig(3,"127.0.0.1",8003)
//        ,new RaftNodeConfig(4,"127.0.0.1",8004)
//        ,new RaftNodeConfig(5,"127.0.0.1",8005)
    );

    public static final int electionTimeout = 3;

    public static final Integer debugElectionTimeout = 3000;

    public static final int heartbeatInterval = 1;

    /**
     * N次心跳后，leader会自动模拟出现故障(退回follow，停止心跳广播)
     * N<=0代表不出自动模拟故障
     */
    public static final int leaderAutoFailCount = 0;

    /**
     * 随机化的选举超时时间
     * */
    public static final Range<Integer> electionTimeoutRandomRange = new Range<>(150,500);

    public static void initRaftRpcServer(int serverId){
        RaftNodeConfig currentNodeConfig = RaftClusterGlobalConfig.raftNodeConfigList
            .stream().filter(item->item.getServerId() == serverId).findAny().get();

        List<RaftNodeConfig> otherNodeList = RaftClusterGlobalConfig.raftNodeConfigList
            .stream().filter(item->item.getServerId() != serverId).collect(Collectors.toList());

        RaftConfig raftConfig = new RaftConfig(
            currentNodeConfig,RaftClusterGlobalConfig.raftNodeConfigList);
        raftConfig.setElectionTimeout(RaftClusterGlobalConfig.electionTimeout);
        raftConfig.setDebugElectionTimeout(RaftClusterGlobalConfig.debugElectionTimeout);

        raftConfig.setHeartbeatInternal(RaftClusterGlobalConfig.heartbeatInterval);
        raftConfig.setLeaderAutoFailCount(RaftClusterGlobalConfig.leaderAutoFailCount);
        // 随机化选举超时时间的范围
        raftConfig.setElectionTimeoutRandomRange(RaftClusterGlobalConfig.electionTimeoutRandomRange);

        RaftRpcServer raftRpcServer = new RaftRpcServer(
            raftConfig, RaftClusterGlobalConfig.registry);

        List<RaftService> raftServiceList = raftRpcServer.getRpcProxyList(otherNodeList);
        raftRpcServer.init(raftServiceList);
    }
}
