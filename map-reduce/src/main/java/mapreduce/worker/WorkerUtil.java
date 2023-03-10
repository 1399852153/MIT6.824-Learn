package mapreduce.worker;

public class WorkerUtil {

    public static String getMapTempFileName(String jobName, String mapTaskId, String reduceNumId){
        return jobName + "_" + mapTaskId + "_" + reduceNumId;
    }
}
