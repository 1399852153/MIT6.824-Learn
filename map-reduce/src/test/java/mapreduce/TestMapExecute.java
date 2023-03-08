package mapreduce;

import mapreduce.common.MapFunction;
import mapreduce.util.ReflectUtil;
import org.junit.Assert;
import org.junit.Test;

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
}
