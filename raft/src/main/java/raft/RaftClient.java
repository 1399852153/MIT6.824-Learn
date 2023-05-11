package raft;

import myrpc.balance.SimpleRoundRobinBalance;
import myrpc.common.URLAddress;
import myrpc.consumer.Consumer;
import myrpc.consumer.ConsumerBootstrap;
import myrpc.consumer.context.ConsumerRpcContext;
import myrpc.consumer.context.ConsumerRpcContextHolder;
import myrpc.registry.Registry;
import raft.api.command.Command;
import raft.api.model.ClientRequestParam;
import raft.api.model.ClientRequestResult;
import raft.api.service.RaftService;
import raft.common.config.RaftNodeConfig;

import java.util.List;

public class RaftClient {

    private List<RaftNodeConfig> raftNodeConfigList;
    private Registry registry;
    private RaftService raftServiceProxy;

    public RaftClient(List<RaftNodeConfig> raftNodeConfigList, Registry registry) {
        this.raftNodeConfigList = raftNodeConfigList;
        this.registry = registry;
    }

    public void init(){
        ConsumerBootstrap consumerBootstrap = new ConsumerBootstrap()
            .registry(registry)
            .loadBalance(new SimpleRoundRobinBalance())
            .init();

        // 注册消费者
        Consumer<RaftService> consumer = consumerBootstrap.registerConsumer(RaftService.class);
        this.raftServiceProxy = consumer.getProxy();
    }

    public String doRequest(Command command){
        ClientRequestParam clientRequestParam = new ClientRequestParam(command);
        // 先让rpc框架负载均衡随便请求一个节点
        ClientRequestResult clientRequestResult = this.raftServiceProxy.clientRequest(clientRequestParam);

        if(clientRequestResult.getLeaderAddress() == null){
            // 访问到了leader，得到结果
            return clientRequestResult.getValue();
        }else{
            // 访问到了follower，得到follower给出的leader地址
            URLAddress urlAddress = clientRequestResult.getLeaderAddress();
            // 指定下次请求
            ConsumerRpcContext consumerRpcContext = ConsumerRpcContextHolder.getConsumerRpcContext();
            consumerRpcContext.setTargetProviderAddress(
                new URLAddress(urlAddress.getHost(),urlAddress.getPort()));

            String result = doRequest(command);

            ConsumerRpcContextHolder.removeConsumerRpcContext();

            return result;
        }
    }
}
