package raft.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.task.HeartBeatTimeoutCheckTask;

import java.util.Date;
import java.util.concurrent.*;

/**
 * Raft服务器的leader选举模块
 * */
public class RaftLeaderElectionModule {

    private static final Logger logger = LoggerFactory.getLogger(RaftLeaderElectionModule.class);

    private final RaftServer currentServer;

    /**
     * 最近一次接受到心跳的时间
     * */
    private volatile Date lastHeartbeatTime;

    private final ScheduledExecutorService scheduledExecutorService;

    private final ExecutorService rpcThreadPool;

    public RaftLeaderElectionModule(RaftServer currentServer) {
        this.currentServer = currentServer;
        this.lastHeartbeatTime = new Date();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
        this.rpcThreadPool = Executors.newFixedThreadPool(Math.max(currentServer.getOtherNodeInCluster().size(),1));

        registerHeartBeatTimeoutCheckTaskWithRandomTimeout();
    }

    /**
     * 提交新的延迟任务(带有随机化的超时时间)
     * */
    public void registerHeartBeatTimeoutCheckTaskWithRandomTimeout(){
        int electionTimeout = currentServer.getRaftConfig().getElectionTimeout();
        long randomElectionTimeout = getRandomElectionTimeout();
        // 选举超时时间的基础上，加上一个随机化的时间
        long delayTime = randomElectionTimeout + electionTimeout * 1000L;
        scheduledExecutorService.schedule(
            new HeartBeatTimeoutCheckTask(currentServer,this),delayTime,TimeUnit.MILLISECONDS);
    }

    /**
     * 处理投票请求
     * 注意：synchronized修饰防止不同candidate并发的投票申请处理，以FIFO的方式处理
     * */
    public synchronized RequestVoteRpcResult requestVoteProcess(RequestVoteRpcParam requestVoteRpcParam){
        if(this.currentServer.getCurrentTerm() > requestVoteRpcParam.getTerm()){
            // Reply false if term < currentTerm (§5.1)
            // 发起投票的candidate任期小于当前服务器任期，拒绝投票给它
            logger.info("reject requestVoteProcess! term < currentTerm, currentServerId={}",currentServer.getServerId());
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }

        if(this.currentServer.getVotedFor() != null && this.currentServer.getVotedFor() != requestVoteRpcParam.getCandidateId()){
            // If votedFor is null or candidateId（取反的卫语句）
            // 当前服务器已经把票投给了别人,拒绝投票给发起投票的candidate
            logger.info("reject requestVoteProcess! votedFor={},currentServerId={}",
                currentServer.getVotedFor(),currentServer.getServerId());
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }

        // todo 先不考虑日志条目索引以及任期值是否满足条件的情况

        // 设置投票给了谁
        this.currentServer.setVotedFor(requestVoteRpcParam.getCandidateId());
        return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),true);
    }

    public void refreshLastHeartbeatTime(){
        // 刷新最新的接受到心跳的时间
        this.lastHeartbeatTime = new Date();

        logger.info("refreshLastHeartbeatTime! {}",currentServer.getServerId());
    }

    public Date getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public ExecutorService getRpcThreadPool() {
        return rpcThreadPool;
    }

    private long getRandomElectionTimeout(){
        long min = currentServer.getRaftConfig().getElectionTimeoutRandomRange().getLeft();
        long max = currentServer.getRaftConfig().getElectionTimeoutRandomRange().getRight();

        // 生成[min,max]范围内随机整数的通用公式为：n=rand.nextInt(max-min+1)+min。
        return ThreadLocalRandom.current().nextLong(max-min+1) + min;
    }
}
