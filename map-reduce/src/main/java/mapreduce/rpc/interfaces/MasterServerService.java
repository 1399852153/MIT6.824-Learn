package mapreduce.rpc.interfaces;

import mapreduce.rpc.model.RegisterWorkerParam;
import mapreduce.rpc.model.ReportTaskExecuteResultParam;

public interface MasterServerService {

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
