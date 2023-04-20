package raft.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.task.HeartBeatTimeoutCheckTask;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Raft服务器的leader选举模块
 * */
public class RaftLeaderElectionModule {

    private static final Logger logger = LoggerFactory.getLogger(RaftLeaderElectionModule.class);

    private final RaftServer currentServer;

    /**
     * 最近一次接受到心跳的时间
     * */
    private Date lastHeartBeatTime;

    private final ScheduledExecutorService scheduledExecutorService;

    private final ExecutorService rpcThreadPool;


    public RaftLeaderElectionModule(RaftServer currentServer) {
        this.currentServer = currentServer;
        this.lastHeartBeatTime = new Date();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
        this.rpcThreadPool = Executors.newFixedThreadPool(currentServer.getOtherNodeInCluster().size());

        int electionTimeout = currentServer.getRaftConfig().getElectionTimeout();

        // 心跳超时检查
        // 初始的延迟，用于控制服务启动后多久开始第一次选举
        // 执行的周期为选举超时时间的一半，保证(当前时间-最后一次接受到心跳)>选举超时时间时，可以即时的发起新的选举
        scheduledExecutorService.scheduleAtFixedRate(new HeartBeatTimeoutCheckTask(currentServer,this),
            electionTimeout,electionTimeout/2, TimeUnit.SECONDS);
    }

    /**
     * 处理投票请求
     * 注意：synchronized修饰防止不同candidate并发的投票申请处理，以FIFO的方式处理
     * */
    public synchronized RequestVoteRpcResult requestVoteProcess(RequestVoteRpcParam requestVoteRpcParam){
        if(this.currentServer.getCurrentTerm() > requestVoteRpcParam.getTerm()){
            // Reply false if term < currentTerm (§5.1)
            // 发起投票的candidate任期小于当前服务器任期，拒绝投票给它
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }

        if(this.currentServer.getVotedFor() != null && this.currentServer.getVotedFor() != requestVoteRpcParam.getCandidateId()){
            // If votedFor is null or candidateId（取反的卫语句）
            // 当前服务器已经把票投给了别人,拒绝投票给发起投票的candidate
            return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),false);
        }

        // todo 先不考虑日志条目索引以及任期值是否满足条件的情况

        // 设置投票给了谁
        this.currentServer.setVotedFor(requestVoteRpcParam.getCandidateId());
        return new RequestVoteRpcResult(this.currentServer.getCurrentTerm(),true);
    }

    public void refreshLastHeartBeatTime(){
        // 刷新最新的接受到心跳的时间
        this.lastHeartBeatTime = new Date();

        logger.info("refreshLastHeartBeatTime! {}",currentServer.getServerId());
    }

    public Date getLastHeartBeatTime() {
        return lastHeartBeatTime;
    }

    public ExecutorService getRpcThreadPool() {
        return rpcThreadPool;
    }
}
