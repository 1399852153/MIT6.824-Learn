package mapreduce.test;

import mapreduce.common.KVPair;
import mapreduce.common.MapFunction;

import java.util.ArrayList;
import java.util.List;

public class MapFn2 implements MapFunction {
    @Override
    public List<KVPair> execute(String fileName, String content) {
        List<KVPair> kvPairList = new ArrayList<>();

        String[] lines = content.split(System.lineSeparator());
        for(String line : lines){
            if(!line.isEmpty()){
                // 最后一位做key
                if(line.length() > 1){
                    String key = line.substring(line.length()-1);
                    kvPairList.add(new KVPair(key,line));
                }else{
                    kvPairList.add(new KVPair(line,line));
                }
            }
        }
        return kvPairList;
    }
}
