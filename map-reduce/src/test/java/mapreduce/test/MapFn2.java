package mapreduce.test;

import mapreduce.common.KVPair;
import mapreduce.common.MapFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFn2 implements MapFunction {
    @Override
    public List<KVPair> execute(String fileName, String content) {
        List<KVPair> kvPairList = new ArrayList<>();

        String[] lines = content.split(System.lineSeparator());
        int i = 0;
        for(String line : lines){
            kvPairList.add(new KVPair(line+"-"+i,line+"-MapFn2"));
            i++;
        }
        return kvPairList;
    }
}
