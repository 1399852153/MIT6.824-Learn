package raft;

import myrpc.common.URLAddress;
import myrpc.consumer.context.ConsumerRpcContextHolder;
import raft.api.model.AppendEntriesRpcParam;
import raft.api.model.AppendEntriesRpcResult;
import raft.api.model.RequestVoteRpcParam;
import raft.api.model.RequestVoteRpcResult;
import raft.api.service.RaftService;
import raft.common.config.RaftNodeConfig;

public class RaftRpcConsumer implements RaftService {

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
        return raftServiceProxy.requestVote(requestVoteRpcParam);
    }

    @Override
    public AppendEntriesRpcResult appendEntries(AppendEntriesRpcParam appendEntriesRpcParam) {
        // 强制指定rpc目标的ip/port
        setTargetProviderUrl();
        return raftServiceProxy.appendEntries(appendEntriesRpcParam);
    }

    private void setTargetProviderUrl(){
        URLAddress targetProviderAddress = ConsumerRpcContextHolder.getConsumerRpcContext().getTargetProviderAddress();
        targetProviderAddress.setHost(targetNodeConfig.getIp());
        targetProviderAddress.setPort(targetNodeConfig.getPort());
    }
}
