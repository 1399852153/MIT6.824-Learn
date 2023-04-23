package raft.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.AppendEntriesRpcParam;
import raft.api.model.AppendEntriesRpcResult;
import raft.api.service.RaftService;
import raft.common.enums.ServerStatusEnum;
import raft.module.RaftHeartBeatBroadcastModule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * leader心跳广播任务
 * */
public class HeartBeatBroadcastTask implements Runnable{

    private static final Logger logger = LoggerFactory.getLogger(HeartBeatBroadcastTask.class);

    private final RaftServer currentServer;
    private final RaftHeartBeatBroadcastModule raftHeartBeatBroadcastModule;

    private int heartbeatCount = 0;

    public HeartBeatBroadcastTask(RaftServer currentServer, RaftHeartBeatBroadcastModule raftHeartBeatBroadcastModule) {
        this.currentServer = currentServer;
        this.raftHeartBeatBroadcastModule = raftHeartBeatBroadcastModule;
    }

    @Override
    public void run() {
        if(currentServer.getServerStatusEnum() != ServerStatusEnum.LEADER){
            // 只有leader才需要广播心跳
            return;
        }

        logger.info("do HeartBeatBroadcast start {}",currentServer.getServerId());

        // 先刷新自己的心跳时间
        this.currentServer.getRaftLeaderElectionModule().refreshLastHeartbeatTime();

        // 并行的发送心跳rpc给集群中的其它节点
        List<RaftService> otherNodeInCluster = currentServer.getOtherNodeInCluster();
        List<Future<AppendEntriesRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());
        for(RaftService node : otherNodeInCluster){
            Future<AppendEntriesRpcResult> future = raftHeartBeatBroadcastModule.getRpcThreadPool().submit(()->{
                AppendEntriesRpcParam appendEntriesRpcParam = new AppendEntriesRpcParam();
                // 心跳rpc，entries为空
                appendEntriesRpcParam.setEntries(null);
                appendEntriesRpcParam.setTerm(currentServer.getCurrentTerm());
                appendEntriesRpcParam.setLeaderId(currentServer.getServerId());

                // todo 日志复制相关的先不考虑
                return node.appendEntries(appendEntriesRpcParam);
            });

            futureList.add(future);
        }

        List<AppendEntriesRpcResult> heartbeatRpcResultList = new ArrayList<>(otherNodeInCluster.size());
        for(Future<AppendEntriesRpcResult> future : futureList){
            try {
                AppendEntriesRpcResult rpcResult = future.get();
                heartbeatRpcResultList.add(rpcResult);
            } catch (Exception e) {
                // 心跳rpc异常，忽略之
            }
        }

        processAutoFail();

        logger.info("do HeartBeatBroadcast end {}",currentServer.getServerId());
    }

    /**
     * 用于测试leader故障用的逻辑，和正常逻辑无关
     * */
    private void processAutoFail(){
        int leaderAutoFailCount = currentServer.getRaftConfig().getLeaderAutoFailCount();
        if(leaderAutoFailCount <= 0){
            // 没匹配leader自动故障，直接返回
            return;
        }

        this.heartbeatCount++;
        if(this.heartbeatCount % leaderAutoFailCount == 0){
            logger.info("模拟leader自动故障，转为follower状态从而终止心跳广播，触发新的一轮选举 serverId={}",currentServer.getServerId());
            currentServer.setServerStatusEnum(ServerStatusEnum.FOLLOWER);
            currentServer.setCurrentLeader(null);
        }
    }
}
