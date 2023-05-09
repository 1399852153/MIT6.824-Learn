package raft.module;

import myrpc.serialize.json.JsonUtil;
import raft.RaftServer;
import raft.api.command.Command;
import raft.api.model.*;
import raft.api.service.RaftService;
import raft.exception.MyRaftException;
import raft.util.CommonUtil;
import raft.util.RaftFileUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LogModule {

    private static final int LONG_SIZE = 8;

    private final File logFile;
    private final File logMetaDataFileOriginal;
    private final RandomAccessFile logMetaDataFile;

    /**
     * 每条记录后面都带上这个，用于找到
     * */
    private volatile long currentOffset;

    /**
     * 已写入的当前日志索引号
     * */
    private long lastIndex;

    /**
     * 已提交的最大日志索引号（论文中的commitIndex）
     * rpc复制到多数节点上，日志就认为是已提交
     * */
    private long lastCommittedIndex;

    /**
     * 作用到状态机上，日志就认为是已应用
     * */
    private long lastApplied;

    private final ExecutorService rpcThreadPool;

    private final RaftServer currentServer;

    public LogModule(RaftServer currentServer) throws IOException {
        this.currentServer = currentServer;

        int serverId = currentServer.getServerId();

        this.rpcThreadPool = Executors.newFixedThreadPool(Math.max(currentServer.getOtherNodeInCluster().size(),1) * 2);

        String userPath = System.getProperty("user.dir");

        this.logFile = new File(userPath + File.separator + "raftLog" + serverId + ".txt");
        RaftFileUtil.createFile(logFile);

        File logMetaDataFile = new File(userPath + File.separator + "raftLogMeta" + serverId + ".txt");
        RaftFileUtil.createFile(logMetaDataFile);

        this.logMetaDataFileOriginal = logMetaDataFile;
        this.logMetaDataFile = new RandomAccessFile(logMetaDataFile,"rw");

        if(this.logMetaDataFile.length() >= LONG_SIZE){
            this.currentOffset = this.logMetaDataFile.readLong();
        }else{
            this.currentOffset = 0;
        }

        try(RandomAccessFile randomAccessFile = new RandomAccessFile(logFile,"r")) {
            // 尝试读取之前已有的日志文件，找到最后一条日志的index
            if (this.currentOffset >= LONG_SIZE) {
                // 跳转到最后一个记录的offset处
                randomAccessFile.seek(this.currentOffset - LONG_SIZE);

                // 获得记录的offset
                long entryOffset = randomAccessFile.readLong();
                // 跳转至对应位置
                randomAccessFile.seek(entryOffset);

                this.lastIndex = randomAccessFile.readInt();
            }else{
                // 之前的日志为空，lastIndex初始化为0
                this.lastIndex = 0;
            }
        }
    }

    /**
     * 按照顺序追加写入日志
     * */
    public void writeLocalLog(LogEntry logEntry){
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(logFile,"rw")){
            // 追加写入
            randomAccessFile.seek(logFile.length());

            randomAccessFile.writeLong(logEntry.getLogIndex());
            randomAccessFile.writeInt(logEntry.getLogTerm());

            byte[] commandBytes = JsonUtil.obj2Str(logEntry.getCommand()).getBytes(StandardCharsets.UTF_8);
            randomAccessFile.writeInt(commandBytes.length);
            randomAccessFile.write(commandBytes);
            randomAccessFile.writeLong(this.currentOffset);

            // 更新偏移量
            this.currentOffset = randomAccessFile.getFilePointer();

            // 持久化currentOffset的值，二阶段提交修改currentOffset的值，宕机恢复时以持久化的值为准
            refreshMetadata();
        } catch (IOException e) {
            throw new MyRaftException("logModule writeLog error!",e);
        }
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * */
    public LogEntry readLocalLog(long logIndex) {
        List<LogEntry> logEntryList = readLocalLogNoSort(logIndex,logIndex);
        if(logEntryList.isEmpty()){
            return null;
        }else{
            // 只会有1个
            return logEntryList.get(0);
        }
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * 左右闭区间（logIndexStart <= {index} <= logIndexEnd）
     * */
    public List<LogEntry> readLocalLog(long logIndexStart, long logIndexEnd) {
        // 读取出来的时候是index从大到小排列的
        List<LogEntry> logEntryList = readLocalLogNoSort(logIndexStart,logIndexEnd);

        // 翻转一下，令其按index从小到大排列
        Collections.reverse(logEntryList);

        return logEntryList;
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * 左右闭区间（logIndexStart <= {index} <= logIndexEnd）
     * */
    private List<LogEntry> readLocalLogNoSort(long logIndexStart, long logIndexEnd) {
        if(logIndexStart > logIndexEnd){
            throw new MyRaftException("readLocalLog logIndexStart > logIndexEnd! " +
                "logIndexStart=" + logIndexStart + " logIndexEnd=" + logIndexEnd);
        }

        List<LogEntry> logEntryList = new ArrayList<>();
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(this.logFile,"r")) {
            // 从后往前找
            long offset = this.currentOffset;

            if(offset >= LONG_SIZE) {
                // 跳转到最后一个记录的offset处
                randomAccessFile.seek(offset - LONG_SIZE);
            }

            while (offset > 0) {
                // 获得记录的offset
                long entryOffset = randomAccessFile.readLong();
                // 跳转至对应位置
                randomAccessFile.seek(entryOffset);

                long targetLogIndex = randomAccessFile.readLong();
                if(targetLogIndex < logIndexStart){
                    // 从下向上找到的顺序，如果已经小于参数指定的了，说明日志里根本就没有需要的日志条目，直接返回null
                    return logEntryList;
                }

                if(targetLogIndex <= logIndexEnd){
                    // 找到的符合要求
                    logEntryList.add(readLocalLogByOffset(randomAccessFile,targetLogIndex));
                }else{
                    // 不符合要求

                    // 跳过一些
                    randomAccessFile.readInt();
                    int commandLength = randomAccessFile.readInt();
                    randomAccessFile.read(new byte[commandLength]);
                }

                // preLogOffset
                offset = randomAccessFile.readLong();
                if(offset < LONG_SIZE){
                    // 整个文件都读完了
                    return logEntryList;
                }

                // 跳转到记录的offset处
                randomAccessFile.seek(offset - LONG_SIZE);
            }
        } catch (IOException e) {
            throw new MyRaftException("logModule readLog error!",e);
        }

        // 找遍了整个文件，也没找到，返回null
        return logEntryList;
    }

    /**
     * 删除包括logIndex以及更大序号的所有日志
     * */
    public void deleteLocalLog(long logIndexNeedDelete){
        // 已经确认提交的日志不能删除
        if(logIndexNeedDelete <= this.lastCommittedIndex){
            throw new MyRaftException("can not delete committed log! " +
                "logIndexNeedDelete=" + logIndexNeedDelete + ",lastCommittedIndex=" + this.lastIndex);
        }

        try(RandomAccessFile randomAccessFile = new RandomAccessFile(this.logFile,"r")) {
            // 从后往前找
            long offset = this.currentOffset;

            if(offset >= LONG_SIZE) {
                // 跳转到最后一个记录的offset处
                randomAccessFile.seek(offset - LONG_SIZE);
            }

            while (offset > 0) {
                // 获得记录的offset
                long entryOffset = randomAccessFile.readLong();
                // 跳转至对应位置
                randomAccessFile.seek(entryOffset);

                long targetLogIndex = randomAccessFile.readLong();
                if(targetLogIndex < logIndexNeedDelete){
                    // 从下向上找到的顺序，如果已经小于参数指定的了，说明日志里根本就没有需要删除的日志条目，直接返回
                    return;
                }

                // 找到了对应的日志条目
                if(targetLogIndex == logIndexNeedDelete){
                    // 把文件的偏移量刷新一下就行(相当于逻辑删除这条日志以及之后的entry)
                    this.currentOffset = entryOffset;
                    refreshMetadata();
                    return;
                }else{
                    // 没找到

                    // 跳过当前日志的剩余部分，继续向上找
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
            throw new MyRaftException("logModule deleteLog error!",e);
        }
    }

    /**
     * 向集群广播，令follower复制新的日志条目
     * */
    public List<AppendEntriesRpcResult> replicationLogEntry(LogEntry logEntry) {
        AppendEntriesRpcParam appendEntriesRpcParam = new AppendEntriesRpcParam();
        appendEntriesRpcParam.setLeaderId(currentServer.getServerId());
        appendEntriesRpcParam.setTerm(currentServer.getCurrentTerm());

        List<LogEntry> logEntryList = new ArrayList<>();
        logEntryList.add(logEntry);
        appendEntriesRpcParam.setEntries(logEntryList);
        appendEntriesRpcParam.setLeaderCommit(this.lastCommittedIndex);

        // 读取最后一条日志
        LogEntry lastLogEntry = getLastLogEntry();
        if(lastLogEntry == null){
            throw new MyRaftException("replicationLogEntry not have entry!");
        }

        appendEntriesRpcParam.setPrevLogIndex(lastLogEntry.getLogIndex());
        appendEntriesRpcParam.setPrevLogTerm(lastLogEntry.getLogTerm());

        List<RaftService> otherNodeInCluster = currentServer.getOtherNodeInCluster();

        List<Future<AppendEntriesRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());

        for(RaftService node : otherNodeInCluster){
            // 并行发送rpc，要求follower复制日志
            Future<AppendEntriesRpcResult> future = this.rpcThreadPool.submit(()->{

                // todo 如果任期不对等异常情况，额外的处理
                return node.appendEntries(appendEntriesRpcParam);
            });

            futureList.add(future);
        }

        // 获得结果
        List<AppendEntriesRpcResult> appendEntriesRpcResultList = CommonUtil.concurrentGetRpcFutureResult(
                "do appendEntries", futureList,
                this.rpcThreadPool,1, TimeUnit.SECONDS);

        for(AppendEntriesRpcResult rpcResult : appendEntriesRpcResultList){
            // 收到更高任期的处理
            currentServer.processCommunicationHigherTerm(rpcResult.getTerm());
        }

        return appendEntriesRpcResultList;
    }

    public LogEntry getLastLogEntry(){
        return readLocalLogByOffset(this.currentOffset);
    }

    // ============================= get/set ========================================

    public long getLastIndex() {
        return lastIndex;
    }

    public void setLastIndex(long lastIndex) {
        this.lastIndex = lastIndex;
    }

    public long getLastCommittedIndex() {
        return lastCommittedIndex;
    }

    public void setLastCommittedIndex(long lastCommittedIndex) {
        if(lastCommittedIndex < this.lastCommittedIndex){
            throw new MyRaftException("set lastCommittedIndex error this.lastCommittedIndex=" + this.lastCommittedIndex
                    + " lastCommittedIndex=" + lastCommittedIndex);
        }

        this.lastCommittedIndex = lastCommittedIndex;
    }

    public long getLastApplied() {
        return lastApplied;
    }

    public void setLastApplied(long lastApplied) {
        if(lastApplied < this.lastApplied){
            throw new MyRaftException("set lastApplied error this.lastApplied=" + this.lastApplied
                + " lastApplied=" + lastApplied);
        }

        this.lastApplied = lastApplied;
    }

    /**
     * 用于单元测试
     * */
    public void clean() throws IOException {
        System.out.println("log module clean!");
        this.logFile.delete();
        this.logMetaDataFile.close();
        this.logMetaDataFileOriginal.delete();
    }

    private LogEntry readLocalLogByOffset(long offset){
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(this.logFile,"r")) {
            if(offset >= LONG_SIZE) {
                // 跳转到记录的offset处
                randomAccessFile.seek(offset - LONG_SIZE);

                long logIndex = randomAccessFile.readLong();

                return readLocalLogByOffset(randomAccessFile,logIndex);
            }else{
                return null;
            }
        } catch (IOException e) {
            throw new MyRaftException("readLocalLog error!",e);
        }
    }

    private LogEntry readLocalLogByOffset(RandomAccessFile randomAccessFile, long logIndex) throws IOException {
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
    }

    private void refreshMetadata() throws IOException {
        this.logMetaDataFile.seek(0);
        this.logMetaDataFile.writeLong(this.currentOffset);
    }
}
