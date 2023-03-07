package mapreduce.common;

import java.util.List;

@FunctionalInterface
public interface ReduceFunction {

    /**
     * @param key k/v对的key
     * @param values 相同key值的value集合
     * */
    String execute(String key, List<String> values);
}
