package mapreduce;

import mapreduce.common.KVPair;
import mapreduce.common.MapFunction;
import mapreduce.common.ReduceFunction;
import mapreduce.constants.WorkerConstants;
import mapreduce.rpc.model.DoMapParam;
import mapreduce.rpc.model.DoReduceParam;
import mapreduce.util.FileUtil;
import mapreduce.util.ReflectUtil;
import mapreduce.util.TestSetupUtil;
import mapreduce.worker.SimpleWorkerServer;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestMapReduce {

    @Test
    public void testMapFunctionExecute(){
        String mapFnName = "mapreduce.test.MapFn1";
        MapFunction mapFunction = ReflectUtil.getTargetMapFunction(mapFnName);
        List<KVPair> result = mapFunction.execute("fileName","123");
        System.out.println(result);
        Assert.assertEquals(result.get(0).getKey(),"fileName");
        Assert.assertEquals(result.get(0).getValue(),"123");
    }

    @Test
    public void testReduceFunctionExecute(){
        String reduceFnName = "mapreduce.test.ReduceFn1";
        ReduceFunction reduceFunction = ReflectUtil.getTargetReduceFunction(reduceFnName);
        String result = reduceFunction.execute("fileName", Arrays.asList("1","2","3"));
        System.out.println(result);
        Assert.assertEquals(result,"fileName-3");
    }

    @Test
    public void testSerializationMapReduce(){
        // 清空原目录下的所有文件
        File tempMapDir = new File(WorkerConstants.DEFAULT_MAP_TEMP_FILE_DIR);
        FileUtil.cleanFileDir(tempMapDir);

        File tempReduceOutputDir = new File(WorkerConstants.DEFAULT_REDUCE_OUTPUT_FILE_DIR);
        FileUtil.cleanFileDir(tempReduceOutputDir);


        SimpleWorkerServer simpleWorkerServer = new SimpleWorkerServer(
            WorkerConstants.DEFAULT_MAP_TEMP_FILE_DIR,WorkerConstants.DEFAULT_REDUCE_OUTPUT_FILE_DIR);

        String jobName = "testMapReduce-1";
        int mapTaskNum = 2;
        int reduceTaskNum = 3;
        String jobOutputFilePath = jobName + "-" + "reduceOutput.txt";

        List<File> inputFileList = new ArrayList<>();
        for(int i=0; i<mapTaskNum; i++){
            File inputFile = TestSetupUtil.generateSerializationMapReduceInputFile(100000,i);
            inputFileList.add(inputFile);
        }

        // 先执行map任务
        for(int i=0; i<mapTaskNum; i++){
            DoMapParam doMapParam = new DoMapParam();
            doMapParam.setJobName(jobName);
            doMapParam.setMapFnName("mapreduce.test.MapFn2");
            doMapParam.setMapTaskId(i+"");
            doMapParam.setInputFilePath(inputFileList.get(i).getPath());
            doMapParam.setReduceNum(reduceTaskNum);
            simpleWorkerServer.doMap(doMapParam);
        }

        // 然后执行reduce任务
        for(int i=0; i<reduceTaskNum; i++){
            DoReduceParam doReduceParam = new DoReduceParam();
            doReduceParam.setJobName(jobName);
            doReduceParam.setReduceFnName("mapreduce.test.ReduceFn2");
            doReduceParam.setReduceTaskId(i+"");
            doReduceParam.setOutputFileName(jobOutputFilePath);
            doReduceParam.setMapNum(mapTaskNum);
            simpleWorkerServer.doReduce(doReduceParam);
        }

        System.out.println("testSerializationMapReduce success!");
    }
}
