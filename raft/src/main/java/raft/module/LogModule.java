package raft.module;

import myrpc.serialize.json.JsonUtil;
import raft.api.command.Command;
import raft.api.model.LogEntry;
import raft.exception.MyRaftException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class LogModule {

    private static final int LONG_SIZE = 8;

    private final File logFile;

    /**
     * 每条记录后面都带上这个，用于找到
     * */
    private long currentOffset;

    /**
     * 已提交的
     * */
    private long lastIndex;
    private long lastCommittedIndex;

    public LogModule(File logFile) {
        this.logFile = logFile;
        this.currentOffset = logFile.length();
    }

    /**
     * */
    public synchronized void writeLog(LogEntry logEntry){
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(logFile,"rw")){
            // 追加写入
            randomAccessFile.seek(logFile.length());

            randomAccessFile.writeInt(logEntry.getLogIndex());
            randomAccessFile.writeInt(logEntry.getLogTerm());

            // todo 思考一下，如果写到一半宕机了，写入的数据不完整怎么办？
            // 持久化currentOffset的值，二阶段提交修改currentOffset的值，宕机恢复时以持久化的值为准

            byte[] commandBytes = JsonUtil.obj2Str(logEntry.getCommand()).getBytes(StandardCharsets.UTF_8);
            randomAccessFile.writeInt(commandBytes.length);
            randomAccessFile.write(commandBytes);
            randomAccessFile.writeLong(this.currentOffset);

            // 更新偏移量
            this.currentOffset = randomAccessFile.getFilePointer();
        } catch (IOException e) {
            throw new MyRaftException("logModule writeLog error!",e);
        }
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * */
    public LogEntry readLog(int logIndex) {
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(this.logFile,"r")) {
            // 从后往前找
            long offset = this.currentOffset;

            if(offset >= LONG_SIZE) {
                // 跳转到最后一个记录的offset处
                randomAccessFile.seek(offset - LONG_SIZE);
            }

            // 循环找，直到找到为止
            while (offset > 0) {
                // 获得记录的offset
                long entryOffset = randomAccessFile.readLong();
                // 跳转至对应位置
                randomAccessFile.seek(entryOffset);

                int targetLogIndex = randomAccessFile.readInt();
                if(targetLogIndex == logIndex){
                    // 找到了

                    LogEntry logEntry = new LogEntry();
                    logEntry.setLogIndex(logIndex);
                    logEntry.setLogTerm(randomAccessFile.readInt());

                    int commandLength = randomAccessFile.readInt();
                    byte[] commandBytes = new byte[commandLength];
                    randomAccessFile.read(commandBytes);

                    String jsonStr = new String(commandBytes,StandardCharsets.UTF_8);
                    Command command = JsonUtil.json2Obj(jsonStr, Command.class);
                    logEntry.setCommand(command);

                    return logEntry;
                }else{
                    // 没找到

                    // 跳过一些
                    randomAccessFile.readInt();
                    int commandLength = randomAccessFile.readInt();
                    randomAccessFile.read(new byte[commandLength]);

                    // preLogOffset
                    offset = randomAccessFile.readLong();
                    // 跳转到记录的offset处
                    randomAccessFile.seek(offset - LONG_SIZE);
                }
            }
        } catch (IOException e) {
            throw new MyRaftException("logModule readLog error!",e);
        }

        // 找遍了整个文件，也没找到，返回null
        return null;
    }
}
