package mapreduce.exception;

public class MapReduceException extends RuntimeException{

    public MapReduceException(String message) {
        super(message);
    }

    public MapReduceException(String message, Throwable cause) {
        super(message, cause);
    }
}
