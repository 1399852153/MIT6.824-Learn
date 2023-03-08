package mapreduce.worker;

import mapreduce.common.MapFunction;
import mapreduce.rpc.constants.RpcConstants;
import mapreduce.rpc.interfaces.WorkerServerService;
import mapreduce.rpc.model.DoMapParam;
import mapreduce.rpc.model.DoReduceParam;
import mapreduce.util.FileUtil;
import mapreduce.util.ReflectUtil;

import java.util.Map;

public class SimpleWorkerServer implements WorkerServerService {
    @Override
    public String healthCheck() {
        return RpcConstants.HEALTHY_CHECK_RESULT;
    }

    @Override
    public void doMap(DoMapParam doMapParam) {
        // 读取指定的文件(目前只支持本地磁盘文件)
        String inputFilePath = doMapParam.getInputFilePath();
        // 获得文件内容
        String inputFileContent = FileUtil.getFileContent(inputFilePath);
        // 获得mapMethod实现类
        MapFunction mapMethod = ReflectUtil.getTargetMapFunction(doMapParam.getMapFnName());
        // 调用map函数，得到对应的k/v集合
        Map<String,String> mapResult = mapMethod.execute(inputFilePath,inputFileContent);

        // 基于reduce任务的数量，将k/v集合写入对应的N个文件中
        for(int i=0; i<doMapParam.getReduceNum(); i++){
            String tempFileName = WorkerUtil.getMapTempFileName(
                doMapParam.getJobName(),doMapParam.getMapTaskId(),i
            );


        }
    }

    @Override
    public void doReduce(DoReduceParam param) {

    }

}
