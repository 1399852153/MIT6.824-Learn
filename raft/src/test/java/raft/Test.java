package raft;

import raft.common.config.RaftConfig;
import raft.util.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Test {

    /**
     * 纯内存的raft选举功能验证
     * */
    public static void main(String[] args) {
        List<Integer> raftClusterServerIdList = Arrays.asList(1,2,3);
        int electionTimeout = 5;
        int heartbeatInterval = 1;

        List<RaftServer> raftServerList = new ArrayList<>();
        for(int i=0; i<raftClusterServerIdList.size(); i++){
            int serverId = raftClusterServerIdList.get(i);
            RaftConfig raftConfig = new RaftConfig(serverId,raftClusterServerIdList);
            raftConfig.setElectionTimeout(electionTimeout);
            raftConfig.setHeartbeatInternal(heartbeatInterval);
            // 随机化选举超时时间的范围
            raftConfig.setElectionTimeoutRandomRange(new Range<>(150,300));
            RaftServer raftServer = new RaftServer(raftConfig);

            raftServerList.add(raftServer);
        }

        for(RaftServer raftServer : raftServerList){
            raftServer.init(raftServerList);
        }
    }
}
