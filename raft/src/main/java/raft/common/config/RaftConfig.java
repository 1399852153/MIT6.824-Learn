package raft.common.config;

import raft.exception.MyRaftException;
import raft.util.Range;

import java.util.List;

public class RaftConfig {

    /**
     * 当前服务节点的id(集群内全局唯一)
     * */
    private final int serverId;

    /**
     * 自己的配置
     * */
    private final RaftNodeConfig currentNodeConfig;

    /**
     * 整个集群所有的服务节点的id集合
     * */
    private final List<RaftNodeConfig> raftNodeConfigList;

    private final int majorityNum;

    /**
     * 选举超时时间 单位:秒
     * */
    private int electionTimeout;

    /**
     * 选举超时时间的随机化区间 单位：毫秒
     * */
    private Range<Integer> electionTimeoutRandomRange;

    /**
     * 心跳间隔时间 单位：秒
     * */
    private int heartbeatInternal;

    /**
     * leader自动故障计数(用于触发自动选举的调试，和正常逻辑无关)
     * */
    private int leaderAutoFailCount;

    public RaftConfig(RaftNodeConfig currentNodeConfig,List<RaftNodeConfig> raftNodeConfigList) {
        this.serverId = currentNodeConfig.getServerId();
        this.currentNodeConfig = currentNodeConfig;
        this.raftNodeConfigList = raftNodeConfigList;
        // 要求集群配置必须是奇数的，偶数的节点个数容错率更差
        // 例如：5个节点的集群可以容忍2个节点故障，而6个节点的集群也只能容忍2个节点故障
        if(!isOddNumber(raftNodeConfigList.size())){
            throw new MyRaftException("cluster server size not odd number! " + raftNodeConfigList.size());
        }

        this.majorityNum = this.raftNodeConfigList.size()/2 + 1;
    }

    public int getServerId() {
        return serverId;
    }

    public List<RaftNodeConfig> getRaftNodeConfigList() {
        return raftNodeConfigList;
    }

    public int getMajorityNum() {
        return majorityNum;
    }

    public void setElectionTimeout(int electionTimeout) {
        this.electionTimeout = electionTimeout;
    }

    public int getElectionTimeout() {
        return electionTimeout;
    }

    public int getHeartbeatInternal() {
        return heartbeatInternal;
    }

    public void setHeartbeatInternal(int heartbeatInternal) {
        this.heartbeatInternal = heartbeatInternal;
    }

    public Range<Integer> getElectionTimeoutRandomRange() {
        return electionTimeoutRandomRange;
    }

    public void setElectionTimeoutRandomRange(Range<Integer> electionTimeoutRandomRange) {
        this.electionTimeoutRandomRange = electionTimeoutRandomRange;
    }

    public int getLeaderAutoFailCount() {
        return leaderAutoFailCount;
    }

    public void setLeaderAutoFailCount(int leaderAutoFailCount) {
        this.leaderAutoFailCount = leaderAutoFailCount;
    }

    private boolean isOddNumber(int num){
        return num % 2 == 1;
    }
}
