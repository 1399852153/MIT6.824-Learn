package raft;

import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.common.enums.ServerStatusEnum;
import raft.util.CommonUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Raft服务器的leader选举模块
 * */
public class RaftLeaderElectionModule {

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
        scheduledExecutorService.scheduleAtFixedRate(new HeartBeatTimeoutCheckTask(),
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

    public void refreshLastHeartBeat(){
        // 刷新最新的接受到心跳的时间
        this.lastHeartBeatTime = new Date();
    }

    /**
     * 心跳超时检查任务
     * */
    private class HeartBeatTimeoutCheckTask implements Runnable{

        @Override
        public void run() {
            if(currentServer.getServerStatusEnum() == ServerStatusEnum.LEADER){
                // leader是不需要处理心跳超时的
                return;
            }

            int electionTimeout = currentServer.getRaftConfig().getElectionTimeout();

            // 当前时间
            Date currentDate = new Date();

            long diffTime = currentDate.getTime() - lastHeartBeatTime.getTime();
            if(diffTime > electionTimeout * 1000L){
                // 距离最近一次接到心跳已经超过了选举超时时间，触发新一轮选举

                // 当前服务器节点当前任期自增1
                currentServer.setCurrentTerm(currentServer.getCurrentTerm()+1);
                // 自己发起选举，先投票给自己
                currentServer.setVotedFor(currentServer.getServerId());
                // 角色转变为CANDIDATE候选者
                currentServer.setServerStatusEnum(ServerStatusEnum.CANDIDATE);

                // 并行的发送请求投票的rpc给集群中的其它节点
                List<RaftServer> otherNodeInCluster = currentServer.getOtherNodeInCluster();
                List<Future<RequestVoteRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());
                for(RaftServer node : otherNodeInCluster){
                    Future<RequestVoteRpcResult> future = rpcThreadPool.submit(()->{
                        RequestVoteRpcParam requestVoteRpcParam = new RequestVoteRpcParam();
                        requestVoteRpcParam.setTerm(currentServer.getCurrentTerm());
                        requestVoteRpcParam.setCandidateId(currentServer.getServerId());
                        // todo 日志复制相关的先不考虑
                        return node.requestVote(requestVoteRpcParam);
                    });

                    futureList.add(future);
                }

                List<RequestVoteRpcResult> requestVoteRpcResultList = new ArrayList<>(otherNodeInCluster.size());
                for(Future<RequestVoteRpcResult> future : futureList){
                    try {
                        RequestVoteRpcResult rpcResult = future.get();
                        requestVoteRpcResultList.add(rpcResult);
                    } catch (Exception e) {
                        // rpc异常，认为投票失败，忽略之
                    }
                }

                // 获得rpc响应中决定投票给自己的总票数
                int getRpcVoted = (int) requestVoteRpcResultList.stream().filter(RequestVoteRpcResult::isVoteGranted).count();

                // getVoted = 其它节点的票数加自己1票
                // totalNodeCount = 集群中总节点数
                boolean majorVoted = CommonUtil.hasMajorVoted(getRpcVoted+1,otherNodeInCluster.size()+1);
                if(majorVoted){
                    // 投票成功，成为leader
                    currentServer.setServerStatusEnum(ServerStatusEnum.LEADER);
                }else{
                    // 票数不过半，无法成为leader
                }
            }

            // 认定为心跳正常，无事发生
        }
    }
}
