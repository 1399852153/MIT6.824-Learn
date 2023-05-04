package raft;

import myrpc.common.URLAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.api.model.*;
import raft.common.config.RaftConfig;
import raft.common.config.RaftNodeConfig;
import raft.common.enums.ServerStatusEnum;
import raft.api.service.RaftService;
import raft.exception.MyRaftException;
import raft.module.LogModule;
import raft.module.RaftHeartBeatBroadcastModule;
import raft.module.RaftLeaderElectionModule;

import java.util.ArrayList;
import java.util.List;

public class RaftServer implements RaftService {

    private static final Logger logger = LoggerFactory.getLogger(RaftServer.class);

    /**
     * 当前服务节点的id(集群内全局唯一)
     * */
    private final int serverId;

    /**
     * Raft服务端配置
     * */
    private final RaftConfig raftConfig;

    /**
     * 当前服务器的状态
     * */
    private volatile ServerStatusEnum serverStatusEnum;

    /**
     * 当前服务器的任期值
     * */
    private volatile int currentTerm;

    /**
     * 当前服务器在此之前投票给了谁？
     * (候选者的serverId，如果还没有投递就是null)
     * */
    private volatile Integer votedFor;

    /**
     * 当前服务认为的leader节点的Id
     * */
    private volatile Integer currentLeader;

    /**
     * 集群中的其它raft节点服务
     * */
    protected List<RaftService> otherNodeInCluster;

    private LogModule logModule;
    private RaftLeaderElectionModule raftLeaderElectionModule;
    private RaftHeartBeatBroadcastModule raftHeartBeatBroadcastModule;

    public RaftServer(RaftConfig raftConfig) {
        this.serverId = raftConfig.getServerId();
        this.raftConfig = raftConfig;
        // 初始化时都是follower
        this.serverStatusEnum = ServerStatusEnum.FOLLOWER;
        // 当前任期值为0
        this.currentTerm = 0;
    }

    public void init(List<RaftService> otherNodeInCluster){
        // 集群中的其它节点服务
        this.otherNodeInCluster = otherNodeInCluster;

        raftLeaderElectionModule = new RaftLeaderElectionModule(this);
        raftHeartBeatBroadcastModule = new RaftHeartBeatBroadcastModule(this);
//        logModule = new LogModule();

        logger.info("raft server init end! otherNodeInCluster={}, currentServerId={}",otherNodeInCluster,serverId);
    }

    @Override
    public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        RequestVoteRpcResult requestVoteRpcResult = raftLeaderElectionModule.requestVoteProcess(requestVoteRpcParam);

        processCommunicationHigherTerm(requestVoteRpcParam.getTerm());

        logger.info("do requestVote requestVoteRpcParam={},requestVoteRpcResult={}, currentServerId={}",
            requestVoteRpcParam,requestVoteRpcResult,this.serverId);

        return requestVoteRpcResult;
    }

    @Override
    public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        AppendEntriesRpcResult appendEntriesRpcResult = doAppendEntries(appendEntriesRpcParam);

        processCommunicationHigherTerm(appendEntriesRpcParam.getTerm());

        logger.info("do appendEntries appendEntriesRpcParam={},appendEntriesRpcResult={},currentServerId={}",
            appendEntriesRpcParam,appendEntriesRpcResult,this.serverId);
        return appendEntriesRpcResult;
    }

    @Override
    public synchronized ClientRequestResult clientRequest(ClientRequestParam clientRequestParam) {
        if(this.serverStatusEnum != ServerStatusEnum.LEADER){
            if(this.currentLeader == null){
                // 自己不是leader，也不知道谁是leader直接报错
                throw new MyRaftException("current node not leader，and leader is null!" + this.serverId);
            }

            RaftNodeConfig leaderConfig = this.raftConfig.getRaftNodeConfigList()
                    .stream().filter(item->item.getServerId() == this.currentLeader).findAny().get();

            // 把自己认为的leader告诉客户端
            ClientRequestResult clientRequestResult = new ClientRequestResult();
            clientRequestResult.setLeaderAddress(new URLAddress(leaderConfig.getIp(),leaderConfig.getPort()));
            return clientRequestResult;
        }

        // 自己是leader，处理客户端的请求

        return null;
    }

    private AppendEntriesRpcResult doAppendEntries(AppendEntriesRpcParam appendEntriesRpcParam){
        if(appendEntriesRpcParam.getTerm() < this.currentTerm){
            // Reply false if term < currentTerm (§5.1)
            // 拒绝处理任期低于自己的老leader的请求
            return new AppendEntriesRpcResult(this.currentTerm,false);
        }

        if(appendEntriesRpcParam.getTerm() >= this.currentTerm){
            // appendEntries请求中任期值如果大于自己，说明已经有一个更新的leader了，自己转为follower，并且以对方更大的任期为准
            this.serverStatusEnum = ServerStatusEnum.FOLLOWER;
            this.currentLeader = appendEntriesRpcParam.getLeaderId();
            this.currentTerm = appendEntriesRpcParam.getTerm();
        }

        if(appendEntriesRpcParam.getEntries() == null){
            // 来自leader的心跳处理，清理掉之前选举的votedFor
            this.cleanVotedFor();
            // entries为空，说明是心跳请求，刷新一下最近收到心跳的时间
            raftLeaderElectionModule.refreshLastHeartbeatTime();

            // 心跳请求，直接返回
            return new AppendEntriesRpcResult(this.currentTerm,true);
        }

        // todo
        return null;
    }

    // ================================= get/set ============================================

    public int getServerId() {
        return serverId;
    }

    public RaftConfig getRaftConfig() {
        return raftConfig;
    }

    public void setServerStatusEnum(ServerStatusEnum serverStatusEnum) {
        this.serverStatusEnum = serverStatusEnum;
    }

    public ServerStatusEnum getServerStatusEnum() {
        return serverStatusEnum;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public Integer getVotedFor() {
        return votedFor;
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
    }

    public Integer getCurrentLeader() {
        return currentLeader;
    }

    public void setCurrentLeader(Integer currentLeader) {
        this.currentLeader = currentLeader;
    }

    public List<RaftService> getOtherNodeInCluster() {
        return otherNodeInCluster;
    }

    public void setOtherNodeInCluster(List<RaftService> otherNodeInCluster) {
        this.otherNodeInCluster = otherNodeInCluster;
    }

    public RaftLeaderElectionModule getRaftLeaderElectionModule() {
        return raftLeaderElectionModule;
    }

    // ============================= public的业务接口 =================================

    /**
     * 清空votedFor
     * */
    public void cleanVotedFor(){
        this.votedFor = null;
    }

    /**
     * rpc交互时任期高于当前节点任期的处理
     * (同时包括接到的rpc请求以及得到的rpc响应，只要另一方的term高于当前节点的term，就更新当前节点的term值)
     *
     * Current terms are exchanged whenever servers communicate;
     * if one server’s current term is smaller than the other’s, then it updates its current term to the larger value.
     * */
    public void processCommunicationHigherTerm(int rpcOtherTerm){
        if(rpcOtherTerm > this.getCurrentTerm()) {
            this.setCurrentTerm(rpcOtherTerm);
            this.setServerStatusEnum(ServerStatusEnum.FOLLOWER);
        }
    }
}
