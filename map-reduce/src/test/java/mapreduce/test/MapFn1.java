package mapreduce.test;

import mapreduce.common.MapFunction;

import java.util.HashMap;
import java.util.Map;

public class MapFn1 implements MapFunction {
    @Override
    public Map<String, String> execute(String fileName, String content) {
        Map<String,String> map = new HashMap<>();
        map.put(fileName,content);
        return map;
    }
}
