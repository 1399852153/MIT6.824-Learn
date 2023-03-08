package mapreduce.rpc.model;

public class DoMapParam {

    /**
     * 需要执行的map实现类名(本来最好的是传递一个脚本，但简单起见就指定函数名，本地依赖接口实现)
     * 格式：类全名
     * */
    private String mapFnName;

    /**
     * 所属job的名字
     * */
    private String jobName;

    /**
     * 当前map任务的id
     * */
    private String mapTaskId;

    /**
     * 设定的reduce分片数量
     * */
    private Integer reduceNum;

    /**
     * 输入文件的路径
     * */
    private String inputFilePath;

    public String getMapFnName() {
        return mapFnName;
    }

    public void setMapFnName(String mapFnName) {
        this.mapFnName = mapFnName;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getMapTaskId() {
        return mapTaskId;
    }

    public void setMapTaskId(String mapTaskId) {
        this.mapTaskId = mapTaskId;
    }

    public Integer getReduceNum() {
        return reduceNum;
    }

    public void setReduceNum(Integer reduceNum) {
        this.reduceNum = reduceNum;
    }

    public String getInputFilePath() {
        return inputFilePath;
    }

    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }
}
