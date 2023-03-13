package mapreduce.worker;

import mapreduce.common.KVPair;
import mapreduce.common.MapFunction;
import mapreduce.common.ReduceFunction;
import mapreduce.rpc.constants.RpcConstants;
import mapreduce.rpc.interfaces.WorkerServerService;
import mapreduce.rpc.model.DoMapParam;
import mapreduce.rpc.model.DoReduceParam;
import mapreduce.util.FileUtil;
import mapreduce.util.ReflectUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleWorkerServer implements WorkerServerService {

    private final String mapTempFileDir;
    private final String reduceOutputFileDir;

    public SimpleWorkerServer(String mapTempFileDir, String reduceOutputFileDir) {
        this.mapTempFileDir = mapTempFileDir;
        this.reduceOutputFileDir = reduceOutputFileDir;
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
        List<KVPair> mapResult = mapMethod.execute(inputFilePath,inputFileContent);

        // 对kv集合按照key进行分组
        Map<Integer, List<KVPair>> groupedKvPairs = mapResult.stream().collect(Collectors.groupingBy(kvPair -> {
            String key = kvPair.getKey();
            // 使用字符串自带的hash函数生成hash值，由于所有map任务收到的reduceNum是一样的
            // 所以可以保证相同key的k/v对能被写入相同的文件中，可以由同一个reduce任务统一处理
            return Math.abs(key.hashCode() % doMapParam.getReduceNum());
        }));

        // 生成map任务临时存储文件
        List<File> mapTaskTempFileList = generateMapTaskTempFile(doMapParam);

        for(Map.Entry<Integer, List<KVPair>> groupedKVItem : groupedKvPairs.entrySet()){
            // 基于分组获得要写入的文件
            File targetFile = mapTaskTempFileList.get(groupedKVItem.getKey());
            // 将同一分组的k/v对全部写入文件中
            FileUtil.writeInKVPairsInFile(targetFile,groupedKVItem.getValue());
        }
    }

    @Override
    public void doReduce(DoReduceParam doReduceParam) {
        // 获取reduce任务对应的所有临时文件
        List<File> mapTempFileList = getMapTaskTempFile(doReduceParam);

        // 读取所有文件的内容，转化为k/v对列表，内存内进行分组(暂时不考虑外排序)，相同key的value值聚合起来
        Map<String,List<String>> mergedKVGroupMap = new HashMap<>();
        for(File mapTempFileItem : mapTempFileList){
            List<KVPair> kvPairList = FileUtil.readKvPairsFromMapTempFile(mapTempFileItem);
            for(KVPair kvPair : kvPairList){
                List<String> targetKeyValueList = mergedKVGroupMap.computeIfAbsent(kvPair.getKey(), s -> new ArrayList<>());
                targetKeyValueList.add(kvPair.getValue());
            }
        }

        // 传递给用户指定的reduce函数，获得reduce的结果
        // 获得reduce实现类
        ReduceFunction reduceFunction = ReflectUtil.getTargetReduceFunction(doReduceParam.getReduceFnName());
        List<KVPair> totalReduceResult = new ArrayList<>();
        for(Map.Entry<String,List<String>> entry : mergedKVGroupMap.entrySet()){
            // 调用reduce函数，得到归约后的结果
            String reduceResult = reduceFunction.execute(entry.getKey(),entry.getValue());
            // 加入总的reduce结果集中
            totalReduceResult.add(new KVPair(entry.getKey(),reduceResult));
        }

        // 将reduce的结果写入指定的reduce任务输出文件中
        File reduceTaskOutputFile = new File(this.reduceOutputFileDir + doReduceParam.getOutputFileName() + "-" + doReduceParam.getReduceTaskId());
        FileUtil.writeInKVPairsInFile(reduceTaskOutputFile,totalReduceResult);
    }

    private List<File> generateMapTaskTempFile(DoMapParam doMapParam){
        List<File> mapTaskTempFileList = new ArrayList<>(doMapParam.getReduceNum());

        // 基于reduce任务的数量，生成对应数量map的k/v对存储临时文件
        for(int i=0; i<doMapParam.getReduceNum(); i++){
            String tempFileName = WorkerUtil.getMapTempFileName(
                doMapParam.getJobName(),doMapParam.getMapTaskId(),i+""
            );

            File file = new File(this.mapTempFileDir + tempFileName);
            mapTaskTempFileList.add(file);
        }

        return mapTaskTempFileList;
    }

    private List<File> getMapTaskTempFile(DoReduceParam doReduceParam){
        List<File> mapTaskTempFileList = new ArrayList<>(doReduceParam.getMapNum());

        // 基于reduce任务的数量，生成对应数量map的k/v对存储临时文件
        for(int i=0; i<doReduceParam.getMapNum(); i++){
            String tempFileName = WorkerUtil.getMapTempFileName(
                doReduceParam.getJobName(),i+"",doReduceParam.getReduceTaskId()
            );

            File file = new File(this.mapTempFileDir + tempFileName);
            mapTaskTempFileList.add(file);
        }

        return mapTaskTempFileList;
    }
}
