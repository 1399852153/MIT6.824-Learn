package mapreduce.rpc.interfaces;

import mapreduce.rpc.model.RegisterWorkerParam;
import mapreduce.rpc.model.ReportTaskExecuteResultParam;

public interface MasterServer {

    /**
     * 注册worker
     * */
    void registerWorker(RegisterWorkerParam param);

    /**
     * worker报告任务状态
     * */
    void reportTaskExecuteResult(ReportTaskExecuteResultParam param);

    /**
     * 展示当前任务信息
     * */
}
