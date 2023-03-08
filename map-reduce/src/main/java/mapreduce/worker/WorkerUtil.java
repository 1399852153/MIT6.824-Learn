package mapreduce.worker;

public class WorkerUtil {

    public static String getMapTempFileName(String jobName, String mapTaskId, int targetReduceNum){
        return jobName + "_" + mapTaskId + "_" + targetReduceNum;
    }
}
