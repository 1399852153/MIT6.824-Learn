package mapreduce.test;

import mapreduce.common.ReduceFunction;

import java.util.List;

public class ReduceFn2 implements ReduceFunction {
    @Override
    public String execute(String key, List<String> values) {
        return values.size() + "";
    }
}
