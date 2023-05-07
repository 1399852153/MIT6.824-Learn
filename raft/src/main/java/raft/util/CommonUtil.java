package raft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.exception.MyRaftException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class CommonUtil {

    private static Logger logger = LoggerFactory.getLogger(CommonUtil.class);

    public static boolean hasMajorVoted(int getVoted, int totalNodeCount){
        int majorCount = totalNodeCount/2+1;
        return getVoted >= majorCount;
    }

    /**
     * 并发的获得future列表的结果
     * */
    public static <T> List<T> concurrentGetRpcFutureResult(
            String info, List<Future<T>> futureList, ExecutorService threadPool, long timeout, TimeUnit timeUnit){
        CountDownLatch countDownLatch = new CountDownLatch(futureList.size());

        List<T> resultList = new ArrayList<>(futureList.size());

        for(Future<T> futureItem : futureList){
            threadPool.execute(()->{
                try {
                    T result = futureItem.get(timeout,timeUnit);
                    resultList.add(result);
                } catch (Exception e) {
                    // rpc异常不考虑
                    logger.error( "{} getFutureResult error!",info,e);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new MyRaftException("getFutureResult error!",e);
        }

        return resultList;
    }
}
