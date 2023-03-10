package mapreduce.constants;

public class WorkerConstants {

    /**
     * 测试任务的map任务临时文件存放的目录
     * */
    public static final String DEFAULT_MAP_TEMP_FILE_DIR = System.getProperty("user.dir") + "/test/mapTemp/";

    /**
     * 测试任务的reduce任务输出文件存放的目录
     * */
    public static final String DEFAULT_REDUCE_OUTPUT_FILE_DIR = System.getProperty("user.dir") + "/test/reduceOutput/";


}
