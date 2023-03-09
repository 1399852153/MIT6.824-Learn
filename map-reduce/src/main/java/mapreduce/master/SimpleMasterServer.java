package mapreduce.master;

import mapreduce.master.task.WorkerHeartBeatCheckTask;
import mapreduce.rpc.interfaces.MasterServerService;
import mapreduce.rpc.interfaces.WorkerServerService;
import mapreduce.rpc.model.RegisterWorkerParam;
import mapreduce.rpc.model.ReportTaskExecuteResultParam;
import myrpc.consumer.Consumer;
import myrpc.consumer.ConsumerBootstrap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SimpleMasterServer implements MasterServerService {

    private final Map<WorkerAddress,WorkerInfo> workerInfoMap = new ConcurrentHashMap<>();

    private final WorkerServerService workerServerService;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    public SimpleMasterServer(ConsumerBootstrap consumerBootstrap) {
        // 注册消费者
        Consumer<WorkerServerService> consumer = consumerBootstrap.registerConsumer(WorkerServerService.class);
        this.workerServerService = consumer.getProxy();

        // 周期性的进行健康检查
        scheduledThreadPoolExecutor.schedule(
            new WorkerHeartBeatCheckTask(this.workerInfoMap,this.workerServerService),1, TimeUnit.SECONDS);
    }

    @Override
    public void registerWorker(RegisterWorkerParam param) {
        WorkerAddress workerAddress = new WorkerAddress(param.getIp(),param.getPort());
        this.workerInfoMap.putIfAbsent(workerAddress,new WorkerInfo());
    }

    @Override
    public void reportTaskExecuteResult(ReportTaskExecuteResultParam param) {

    }
}
