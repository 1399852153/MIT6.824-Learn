package mapreduce.worker;

import mapreduce.common.MapFunction;
import mapreduce.rpc.constants.RpcConstants;
import mapreduce.rpc.interfaces.WorkerServerService;
import mapreduce.rpc.model.DoMapParam;
import mapreduce.rpc.model.DoReduceParam;
import mapreduce.util.FileUtil;
import mapreduce.util.ReflectUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleWorkerServer implements WorkerServerService {

    private final String mapTempFileDir;

    public SimpleWorkerServer(String mapTempFileDir) {
        this.mapTempFileDir = mapTempFileDir;
    }

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

        // 对kv集合按照key进行分组
        Map<Integer, List<Map.Entry<String, String>>> groupedKvPairs = mapResult.entrySet().stream().collect(Collectors.groupingBy(kvPair -> {
            String key = kvPair.getKey();
            // 使用字符串自带的hash函数生成hash值，由于所有map任务收到的reduceNum是一样的
            // 所以可以保证相同key的k/v对能被写入相同的文件中，可以由同一个reduce任务统一处理
            return Math.abs(key.hashCode() % doMapParam.getReduceNum());
        }));

        // 生成map任务临时存储文件
        List<File> mapTaskTempFileList = generateMapTaskTempFile(doMapParam);

        for(Map.Entry<Integer, List<Map.Entry<String, String>>> groupedKVItem : groupedKvPairs.entrySet()){
            System.out.println("groupedKVItem=" + groupedKVItem);
            // 基于分组获得要写入的文件
            File targetFile = mapTaskTempFileList.get(groupedKVItem.getKey());
            // 将同一分组的k/v对全部写入文件中
            FileUtil.writeInFile(targetFile,groupedKVItem.getValue());
        }
    }

    @Override
    public void doReduce(DoReduceParam param) {

    }

    private List<File> generateMapTaskTempFile(DoMapParam doMapParam){
        List<File> mapTaskTempFileList = new ArrayList<>(doMapParam.getReduceNum());

        // 基于reduce任务的数量，生成对应数量map的k/v对存储临时文件
        for(int i=0; i<doMapParam.getReduceNum(); i++){
            String tempFileName = WorkerUtil.getMapTempFileName(
                doMapParam.getJobName(),doMapParam.getMapTaskId(),i
            );

            File file = new File(this.mapTempFileDir + tempFileName);
            mapTaskTempFileList.add(file);
        }

        return mapTaskTempFileList;
    }
}
