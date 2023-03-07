package mapreduce.rpc.interfaces;

import mapreduce.rpc.model.DoMapParam;
import mapreduce.rpc.model.DoReduceParam;

public interface WorkerServerService {

    /**
     * 心跳检查
     * */
    String healthCheck();

    /**
     * 执行map任务
     * */
    void doMap(DoMapParam doMapParam);

    /**
     * 执行reduce任务
     * */
    void doReduce(DoReduceParam param);

    /**
     * 展示当前任务信息
     * */
}
