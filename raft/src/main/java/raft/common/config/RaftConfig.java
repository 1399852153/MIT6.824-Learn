package raft.common.config;

import raft.exception.MyRaftException;

import java.util.List;

public class RaftConfig {

    /**
     * 当前服务节点的id(集群内全局唯一)
     * */
    private int serverId;

    /**
     * 整个集群所有的服务节点的id集合
     * */
    private List<Integer> raftClusterServerIdList;

    private int majorityNum;

    /**
     * 选举超时时间 单位:秒
     * */
    private int electionTimeout;

    public RaftConfig(int serverId, List<Integer> raftClusterServerIdList) {
        this.serverId = serverId;
        this.raftClusterServerIdList = raftClusterServerIdList;
        // 要求集群配置必须是奇数的，才能确保存在唯一的大多数
        if(!isOddNumber(raftClusterServerIdList.size())){
            throw new MyRaftException("cluster server size not odd number! " + raftClusterServerIdList);
        }

        this.majorityNum = this.raftClusterServerIdList.size()/2 + 1;
    }

    public int getServerId() {
        return serverId;
    }

    public List<Integer> getRaftClusterServerIdList() {
        return raftClusterServerIdList;
    }

    public int getMajorityNum() {
        return majorityNum;
    }

    public int getElectionTimeout() {
        return electionTimeout;
    }

    private boolean isOddNumber(int num){
        return num % 2 == 1;
    }
}
