package raft.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.command.EmptySetCommand;
import raft.api.model.ClientRequestParam;
import raft.api.model.LogEntry;
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
import java.util.concurrent.TimeUnit;

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
        }else{
            try {
                doTask();
            }catch (Exception e){
                logger.info("do HeartBeatTimeoutCheckTask error! ignore",e);
            }

            // 注册下一个心跳检查任务
            raftLeaderElectionModule.registerHeartBeatTimeoutCheckTaskWithRandomTimeout();
        }
    }

    private void doTask(){
        logger.debug("do HeartBeatTimeoutCheck start {}",currentServer.getServerId());

        int electionTimeout = currentServer.getRaftConfig().getElectionTimeout();

        // 当前时间
        Date currentDate = new Date();
        Date lastHeartBeatTime = raftLeaderElectionModule.getLastHeartbeatTime();
        long diffTime = currentDate.getTime() - lastHeartBeatTime.getTime();

        logger.debug("currentDate={}, lastHeartBeatTime={}, diffTime={}, serverId={}",
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

            // 构造请求参数
            RequestVoteRpcParam requestVoteRpcParam = new RequestVoteRpcParam();
            requestVoteRpcParam.setTerm(currentServer.getCurrentTerm());
            requestVoteRpcParam.setCandidateId(currentServer.getServerId());

            LogEntry lastLogEntry = currentServer.getLogModule().getLastLogEntry();
            requestVoteRpcParam.setLastLogTerm(lastLogEntry.getLogTerm());
            requestVoteRpcParam.setLastLogIndex(lastLogEntry.getLogIndex());

            for(RaftService node : otherNodeInCluster){
                Future<RequestVoteRpcResult> future = raftLeaderElectionModule.getRpcThreadPool().submit(
                    ()-> {
                        RequestVoteRpcResult rpcResult = node.requestVote(requestVoteRpcParam);
                        // 收到更高任期的处理
                        currentServer.processCommunicationHigherTerm(rpcResult.getTerm());
                        return rpcResult;
                    }
                );

                futureList.add(future);
            }

            List<RequestVoteRpcResult> requestVoteRpcResultList = CommonUtil.concurrentGetRpcFutureResult(
                    "requestVote", futureList,
                    raftLeaderElectionModule.getRpcThreadPool(),1,TimeUnit.SECONDS);

            // 获得rpc响应中决定投票给自己的总票数（算上自己的1票）
            int getRpcVoted = (int) requestVoteRpcResultList.stream().filter(RequestVoteRpcResult::isVoteGranted).count()+1;
            logger.info("HeartBeatTimeoutCheck election, getRpcVoted={}, currentServerId={}",getRpcVoted,currentServer.getServerId());

            // 是否获得大多数的投票
            boolean majorVoted = getRpcVoted >= this.currentServer.getRaftConfig().getMajorityNum();
            if(majorVoted){
                logger.info("HeartBeatTimeoutCheck election result: become a leader! {}",currentServer.getServerId());

                // 票数过半成功当选为leader
                currentServer.setServerStatusEnum(ServerStatusEnum.LEADER);
                currentServer.setCurrentLeader(currentServer.getServerId());

                // 成为leader之后需要进行的一些操作
                processWhenBecomeLeader();
            }else{
                // 票数不过半，无法成为leader
                logger.info("HeartBeatTimeoutCheck election result: not become a leader! {}",currentServer.getServerId());
            }

            this.currentServer.cleanVotedFor();
        }else{
            // 认定为心跳正常，无事发生
            logger.debug("HeartBeatTimeoutCheck check success {}",currentServer.getServerId());
        }

        logger.debug("do HeartBeatTimeoutCheck end {}",currentServer.getServerId());
    }

    /**
     * 成为leader之后需要进行的一些操作
     * */
    private void processWhenBecomeLeader(){
        // 成为leader后立马发送一次心跳,抑制其它节点发起新的一轮选举
        // Upon election: send initial empty AppendEntries RPCs (heartbeat) to each server;
        // repeat during idle periods to prevent election timeouts (§5.2)
        HeartBeatBroadcastTask.doHeartBeatBroadcast(currentServer);

        long lastIndex = currentServer.getLogModule().getLastIndex();
        for(RaftService otherService : currentServer.getOtherNodeInCluster()){
            // for each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
            this.currentServer.getNextIndexMap().put(otherService,lastIndex+1);

            // for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)
            this.currentServer.getMatchIndexMap().put(otherService,0L);
        }

        // 成为leader后再发起一次no-op的日志复制操作，获得nextIndexMap和matchIndexMap的最新值
        // Raft handles this by having each leader commit a blank _no-op_ entry into the log at the start of its term.
        currentServer.clientRequest(new ClientRequestParam(new EmptySetCommand()));
    }
}
