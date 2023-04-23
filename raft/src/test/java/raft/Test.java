package raft;

import raft.api.service.RaftService;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.util.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Test {

    /**
     * 纯内存的raft选举功能验证
     * */
    public static void main(String[] args) {
        List<RaftNodeConfig> raftNodeConfigList = Arrays.asList(
            new RaftNodeConfig(1),
            new RaftNodeConfig(2),
            new RaftNodeConfig(3),
            new RaftNodeConfig(4),
            new RaftNodeConfig(5)
        );

        int electionTimeout = 3;
        int heartbeatInterval = 1;

        List<RaftServer> raftServerList = new ArrayList<>();
        for(int i=0; i<raftNodeConfigList.size(); i++){
            RaftNodeConfig currentNodeConfig = raftNodeConfigList.get(i);
            RaftConfig raftConfig = new RaftConfig(currentNodeConfig,raftNodeConfigList);
            raftConfig.setElectionTimeout(electionTimeout);
            raftConfig.setHeartbeatInternal(heartbeatInterval);
            // 10次心跳后，leader会自动模拟出现故障(退回follow，停止心跳广播)
            raftConfig.setLeaderAutoFailCount(5);
            // 随机化选举超时时间的范围
            raftConfig.setElectionTimeoutRandomRange(new Range<>(150,300));
            RaftServer raftServer = new RaftServer(raftConfig);

            raftServerList.add(raftServer);
        }

        for(RaftServer raftServer : raftServerList){
            // 排掉自己
            List<RaftService> otherNodeInCluster = raftServerList.stream()
                .filter(item->item.getServerId() != raftServer.getServerId())
                .collect(Collectors.toList());

            raftServer.init(otherNodeInCluster);
        }
    }
}
