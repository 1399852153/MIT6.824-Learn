package mapreduce;

import mapreduce.common.MapFunction;
import mapreduce.constants.WorkerConstants;
import mapreduce.rpc.model.DoMapParam;
import mapreduce.util.ReflectUtil;
import mapreduce.worker.SimpleWorkerServer;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Map;

public class TestMapExecute {

    @Test
    public void testMapFunctionExecute(){
        String mapFnName = "mapreduce.test.MapFn1";
        MapFunction mapFunction = ReflectUtil.getTargetMapFunction(mapFnName);
        Map<String,String> result = mapFunction.execute("fileName","123");
        System.out.println(result);
        Assert.assertEquals(result.get("fileName"),"123");
    }

    @Test
    public void testDoMap(){
        // 清空原目录下的所有文件
        File tempDir = new File(WorkerConstants.DEFAULT_MAP_TEMP_FILE_DIR);
        if(tempDir.listFiles() != null) {
            for (File file : tempDir.listFiles()) {
                file.delete();
            }
        }

        SimpleWorkerServer simpleWorkerServer = new SimpleWorkerServer(WorkerConstants.DEFAULT_MAP_TEMP_FILE_DIR);

        String userPath = System.getProperty("user.dir");
        String testFilePath = userPath + "/src/test/test.txt";

        DoMapParam doMapParam = new DoMapParam();
        doMapParam.setMapFnName("mapreduce.test.MapFn2");
        doMapParam.setMapTaskId("map1");
        doMapParam.setInputFilePath(testFilePath);
        doMapParam.setJobName("testDoMap1");
        doMapParam.setReduceNum(10);
        simpleWorkerServer.doMap(doMapParam);
    }
}
