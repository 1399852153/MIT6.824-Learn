package mapreduce.common;

import java.util.Map;

@FunctionalInterface
public interface MapFunction {

    /**
     * @param fileName 输入的文件名
     * @param content 输入文件的内容
     * */
    Map<String,String> execute(String fileName, String content);
}
