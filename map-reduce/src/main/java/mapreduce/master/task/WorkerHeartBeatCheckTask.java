package mapreduce.master.task;

import mapreduce.master.WorkerAddress;
import mapreduce.master.WorkerInfo;
import mapreduce.rpc.constants.RpcConstants;
import mapreduce.rpc.interfaces.WorkerServerService;
import myrpc.common.URLAddress;
import myrpc.consumer.context.ConsumerRpcContext;
import myrpc.consumer.context.ConsumerRpcContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

public class WorkerHeartBeatCheckTask implements Runnable{

    private static final Logger logger = LoggerFactory.getLogger(WorkerHeartBeatCheckTask.class);

    private final Map<WorkerAddress, WorkerInfo> workerInfoMap;
    private final WorkerServerService workerServerService;

    public WorkerHeartBeatCheckTask(Map<WorkerAddress, WorkerInfo> workerInfoMap, WorkerServerService workerServerService) {
        this.workerInfoMap = workerInfoMap;
        this.workerServerService = workerServerService;
    }

    @Override
    public void run() {
        logger.info("do WorkerHeartBeatCheckTask");
        for(WorkerAddress workerAddress : workerInfoMap.keySet()){
            // 强行指定服务方的ip，进行健康检查
            ConsumerRpcContext consumerRpcContext = ConsumerRpcContextHolder.getConsumerRpcContext();
            consumerRpcContext.setTargetProviderAddress(new URLAddress(workerAddress.getIp(),workerAddress.getPort()));
            try {
                String result = workerServerService.healthCheck();
                if(Objects.equals(result, RpcConstants.HEALTHY_CHECK_RESULT)){
                    // 健康检查失败（todo 优化一下，可以多调用几次避免网络波动的影响）
                    heartBeatCheckFailProcess(workerAddress);
                }else{
                    logger.info("do WorkerHeartBeatCheckTask success! workerAddress={}",workerAddress);
                }
            }catch (Exception e){
                // 健康检查失败
                heartBeatCheckFailProcess(workerAddress);
            }
        }
    }

    private void heartBeatCheckFailProcess(WorkerAddress workerAddress){
        logger.error("do WorkerHeartBeatCheckTask fail! workerAddress={}",workerAddress);

        // 健康检查失败,将当前worker移除出去
        WorkerInfo workerInfo = workerInfoMap.remove(workerAddress);

        // todo workerInfo中的任务重新分配
    }
}
