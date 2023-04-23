package raft.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.api.service.RaftService;
import raft.common.enums.ServerStatusEnum;
import raft.module.RaftLeaderElectionModule;
import raft.util.CommonUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 心跳超时检查任务
 * */
public class HeartBeatTimeoutCheckTask implements Runnable{

    private static final Logger logger = LoggerFactory.getLogger(HeartBeatTimeoutCheckTask.class);

    private final RaftServer currentServer;
    private final RaftLeaderElectionModule raftLeaderElectionModule;

    public HeartBeatTimeoutCheckTask(RaftServer currentServer, RaftLeaderElectionModule raftLeaderElectionModule) {
        this.currentServer = currentServer;
        this.raftLeaderElectionModule = raftLeaderElectionModule;
    }

    @Override
    public void run() {
        if(currentServer.getServerStatusEnum() == ServerStatusEnum.LEADER){
            // leader是不需要处理心跳超时的
            // 注册下一个心跳检查任务
            raftLeaderElectionModule.registerHeartBeatTimeoutCheckTaskWithRandomTimeout();
            return;
        }

        logger.info("do HeartBeatTimeoutCheck start {}",currentServer.getServerId());

        int electionTimeout = currentServer.getRaftConfig().getElectionTimeout();

        // 当前时间
        Date currentDate = new Date();
        Date lastHeartBeatTime = raftLeaderElectionModule.getLastHeartbeatTime();
        long diffTime = currentDate.getTime() - lastHeartBeatTime.getTime();

        logger.info("currentDate={}, lastHeartBeatTime={}, diffTime={}, serverId={}",
            currentDate,lastHeartBeatTime,diffTime,currentServer.getServerId());
        // 心跳超时判断
        if(diffTime > (electionTimeout * 1000L)){
            logger.info("HeartBeatTimeoutCheck check fail, trigger new election! serverId={}",currentServer.getServerId());

            // 距离最近一次接到心跳已经超过了选举超时时间，触发新一轮选举

            // 当前服务器节点当前任期自增1
            currentServer.setCurrentTerm(currentServer.getCurrentTerm()+1);
            // 自己发起选举，先投票给自己
            currentServer.setVotedFor(currentServer.getServerId());
            // 角色转变为CANDIDATE候选者
            currentServer.setServerStatusEnum(ServerStatusEnum.CANDIDATE);

            // 并行的发送请求投票的rpc给集群中的其它节点
            List<RaftService> otherNodeInCluster = currentServer.getOtherNodeInCluster();
            List<Future<RequestVoteRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());
            for(RaftService node : otherNodeInCluster){
                Future<RequestVoteRpcResult> future = raftLeaderElectionModule.getRpcThreadPool().submit(()->{
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
            logger.info("HeartBeatTimeoutCheck election, getRpcVoted={}, currentServerId={}",getRpcVoted,currentServer.getServerId());

            // getVoted = 其它节点的票数加自己1票
            // totalNodeCount = 集群中总节点数
            boolean majorVoted = CommonUtil.hasMajorVoted(getRpcVoted+1,otherNodeInCluster.size()+1);
            if(majorVoted){
                logger.info("HeartBeatTimeoutCheck election, become a leader! {}",currentServer.getServerId());

                // 投票成功，成为leader
                currentServer.setServerStatusEnum(ServerStatusEnum.LEADER);
                currentServer.setCurrentLeader(currentServer.getServerId());
            }else{
                // 票数不过半，无法成为leader
                logger.info("HeartBeatTimeoutCheck election, not become a leader! {}",currentServer.getServerId());
            }
        }else{
            // 认定为心跳正常，无事发生
            logger.info("HeartBeatTimeoutCheck check success {}",currentServer.getServerId());
        }

        // 注册下一个心跳检查任务
        raftLeaderElectionModule.registerHeartBeatTimeoutCheckTaskWithRandomTimeout();

        logger.info("do HeartBeatTimeoutCheck end {}",currentServer.getServerId());
    }
}
