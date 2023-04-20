package raft.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.AppendEntriesRpcParam;
import raft.api.model.AppendEntriesRpcResult;
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

        // 并行的发送心跳rpc给集群中的其它节点
        List<RaftServer> otherNodeInCluster = currentServer.getOtherNodeInCluster();
        List<Future<AppendEntriesRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());
        for(RaftServer node : otherNodeInCluster){
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

        logger.info("do HeartBeatBroadcast end {}",currentServer.getServerId());
    }
}
