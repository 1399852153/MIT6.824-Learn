package mapreduce.util;

import mapreduce.common.MapFunction;
import mapreduce.common.ReduceFunction;
import mapreduce.exception.MapReduceException;

public class ReflectUtil {

    public static MapFunction getTargetMapFunction(String mapMethodName){
        try {
            Class<?> clazz = Class.forName(mapMethodName);

            Object object = clazz.newInstance();
            if(object instanceof MapFunction){
                return (MapFunction) object;
            }else{
                throw new MapReduceException(mapMethodName + " is not a MapFunction");
            }
        }catch (Exception e){
            throw new MapReduceException("getTargetMapMethodByFnName error",e);
        }
    }

    public static ReduceFunction getTargetReduceFunction(String reduceMethodName){
        try {
            Class<?> clazz = Class.forName(reduceMethodName);

            Object object = clazz.newInstance();
            if(object instanceof ReduceFunction){
                return (ReduceFunction) object;
            }else{
                throw new MapReduceException(reduceMethodName + " is not a ReduceFunction");
            }
        }catch (Exception e){
            throw new MapReduceException("getTargetMapMethodByFnName error",e);
        }
    }
}
