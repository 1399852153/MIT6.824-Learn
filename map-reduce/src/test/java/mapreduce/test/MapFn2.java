package mapreduce.test;

import mapreduce.common.MapFunction;

import java.util.HashMap;
import java.util.Map;

public class MapFn2 implements MapFunction {
    @Override
    public Map<String, String> execute(String fileName, String content) {
        Map<String,String> result = new HashMap<>();

        String[] lines = content.split(System.lineSeparator());
        int i = 0;
        for(String line : lines){
            result.put(line+"-"+i,line+"-MapFn2");
            i++;
        }
        return result;
    }
}
