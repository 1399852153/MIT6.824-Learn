package raft.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.LogEntry;
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
        this.scheduledExecutorService = Executors.newScheduledThreadPool(3);
        this.rpcThreadPool = Executors.newFixedThreadPool(
                Math.max(currentServer.getOtherNodeInCluster().size(),1) * 2);

        registerHeartBeatTimeoutCheckTaskWithRandomTimeout();
    }

    /**
     * 提交新的延迟任务(带有随机化的超时时间)
     * */
    public void registerHeartBeatTimeoutCheckTaskWithRandomTimeout(){
//        logger.info("registerHeartBeatTimeoutCheckTaskWithRandomTimeout!");

        int electionTimeout = currentServer.getRaftConfig().getElectionTimeout();
        if(currentServer.getCurrentTerm() > 0 && currentServer.getRaftConfig().getDebugElectionTimeout() != null){
            // debug的时候多等待一些时间
            electionTimeout = currentServer.getRaftConfig().getDebugElectionTimeout();
        }

        long randomElectionTimeout = getRandomElectionTimeout();
        // 选举超时时间的基础上，加上一个随机化的时间
        long delayTime = randomElectionTimeout + electionTimeout * 1000L;
        logger.debug("registerHeartBeatTimeoutCheckTaskWithRandomTimeout delayTime={}",delayTime);
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

        // 考虑日志条目索引以及任期值是否满足条件的情况（第5.4节中提到的安全性）
        // 保证leader必须拥有所有已提交的日志，即发起投票的candidate日志一定要比投票给它的节点更新
        LogEntry lastLogEntry = currentServer.getLogModule().getLastLogEntry();
        logger.info("requestVoteProcess lastLogEntry={}",lastLogEntry);
        if(lastLogEntry.getLogTerm() > requestVoteRpcParam.getLastLogTerm()){
            // If the logs have last entries with different terms, then the log with the later term is more up-to-date.
            // 当前节点的last日志任期比发起投票的candidate更高(比candidate更新)，不投票给它
            logger.info("lastLogEntry.term > candidate.lastLogTerm! voteGranted=false");
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }else if(lastLogEntry.getLogTerm() == requestVoteRpcParam.getLastLogTerm() &&
            lastLogEntry.getLogIndex() > requestVoteRpcParam.getLastLogIndex()){
            // If the logs end with the same term, then whichever log is longer is more up-to-date.
            // 当前节点的last日志和发起投票的candidate任期一样，但是index比candidate的高(比candidate更新)，不投票给它

            logger.info("lastLogEntry.term == candidate.lastLogTerm && " +
                "lastLogEntry.index > candidate.lastLogIndex! voteGranted=false");
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }else{
            // candidate的日志至少与当前节点一样新(或者更新)，通过检查，可以投票给它
            logger.info("candidate log at least as new as the current node, valid passed!");
        }

        // 设置投票给了谁
        this.currentServer.setVotedFor(requestVoteRpcParam.getCandidateId());
        return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),true);
    }

    public void refreshLastHeartbeatTime(){
        // 刷新最新的接受到心跳的时间
        this.lastHeartbeatTime = new Date();
        // 接受新的心跳,说明现在leader是存活的，清理掉之前的投票信息
        this.currentServer.cleanVotedFor();

        logger.debug("refreshLastHeartbeatTime! {}",currentServer.getServerId());
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
