package mapreduce.test;

import mapreduce.common.KVPair;
import mapreduce.common.MapFunction;

import java.util.*;

public class MapFn1 implements MapFunction {
    @Override
    public List<KVPair> execute(String fileName, String content) {
        return Collections.singletonList(new KVPair(fileName, content));
    }
}
