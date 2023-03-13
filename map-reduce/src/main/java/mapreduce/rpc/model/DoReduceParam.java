package mapreduce.rpc.model;

public class DoReduceParam {

    /**
     * 需要执行的reduceFn实现类名(本来最好的是传递一个脚本，但简单起见就指定函数名，本地依赖接口实现)
     * 格式：类全名
     * */
    private String reduceFnName;

    /**
     * 所属job的名字
     * */
    private String jobName;

    /**
     * 当前reduce任务的id
     * */
    private String reduceTaskId;

    /**
     * 设定的map分片数量
     * */
    private Integer mapNum;

    /**
     * reduce结果输出文件名
     * */
    private String outputFileName;

    public String getReduceFnName() {
        return reduceFnName;
    }

    public void setReduceFnName(String reduceFnName) {
        this.reduceFnName = reduceFnName;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getReduceTaskId() {
        return reduceTaskId;
    }

    public void setReduceTaskId(String reduceTaskId) {
        this.reduceTaskId = reduceTaskId;
    }

    public Integer getMapNum() {
        return mapNum;
    }

    public void setMapNum(Integer mapNum) {
        this.mapNum = mapNum;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
    }
}
