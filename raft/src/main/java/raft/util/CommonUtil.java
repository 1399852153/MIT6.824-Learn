package raft.util;

public class CommonUtil {

    public static boolean hasMajorVoted(int getVoted, int totalNodeCount){
        int majorCount = totalNodeCount/2+1;
        return getVoted >= majorCount;
    }
}
