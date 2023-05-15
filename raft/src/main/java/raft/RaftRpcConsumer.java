package raft;

import myrpc.common.URLAddress;
import myrpc.consumer.context.ConsumerRpcContext;
import myrpc.consumer.context.ConsumerRpcContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.api.model.*;
import raft.api.service.RaftService;
import raft.common.config.RaftNodeConfig;
import raft.exception.MyRaftException;
import raft.task.HeartBeatTimeoutCheckTask;

public class RaftRpcConsumer implements RaftService {

    private static final Logger logger = LoggerFactory.getLogger(HeartBeatTimeoutCheckTask.class);

    private final RaftNodeConfig targetNodeConfig;
    private final RaftService raftServiceProxy;

    public RaftRpcConsumer(RaftNodeConfig targetNodeConfig, RaftService proxyRaftService) {
        this.targetNodeConfig = targetNodeConfig;
        this.raftServiceProxy = proxyRaftService;
    }

    @Override
    public RequestVoteRpcResult requestVote(RequestVoteRpcParam requestVoteRpcParam) {
        // 强制指定rpc目标的ip/port
        setTargetProviderUrl();
        long start = System.currentTimeMillis();
        RequestVoteRpcResult result = raftServiceProxy.requestVote(requestVoteRpcParam);
        long end = System.currentTimeMillis();
        logger.info("requestVote request cost targetUrl={},{}ms",targetNodeConfig,(end-start));
        return result;
    }

    @Override
    public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        // 强制指定rpc目标的ip/port
        setTargetProviderUrl();
        AppendEntriesRpcResult result = raftServiceProxy.appendEntries(appendEntriesRpcParam);
        return result;
    }

    @Override
    public ClientRequestResult clientRequest(ClientRequestParam clientRequestParam) {
        // 只有raft的客户端才会调用这个方法，服务端是不会调用这个方法的
        throw new MyRaftException("raft server node can not be invoke clientRequest!");
    }

    private void setTargetProviderUrl(){
        ConsumerRpcContext consumerRpcContext = ConsumerRpcContextHolder.getConsumerRpcContext();
        consumerRpcContext.setTargetProviderAddress(
            new URLAddress(targetNodeConfig.getIp(),targetNodeConfig.getPort()));
    }

    @Override
    public String toString() {
        return "RaftRpcConsumer{" +
            "targetNodeConfig=" + targetNodeConfig +
            '}';
    }
}
