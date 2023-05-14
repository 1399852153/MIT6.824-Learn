package raft.module;

import myrpc.serialize.json.JsonUtil;
import raft.RaftServer;
import raft.api.command.Command;
import raft.api.model.*;
import raft.api.service.RaftService;
import raft.exception.MyRaftException;
import raft.util.CommonUtil;
import raft.util.MyRaftFileUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
    private volatile long lastIndex;

    /**
     * 已提交的最大日志索引号（论文中的commitIndex）
     * rpc复制到多数节点上，日志就认为是已提交
     * */
    private volatile long lastCommittedIndex;

    /**
     * 作用到状态机上，日志就认为是已应用
     * */
    private volatile long lastApplied;

    private final ExecutorService rpcThreadPool;

    private final RaftServer currentServer;

    public LogModule(RaftServer currentServer) throws IOException {
        this.currentServer = currentServer;

        int serverId = currentServer.getServerId();

        this.rpcThreadPool = Executors.newFixedThreadPool(Math.max(currentServer.getOtherNodeInCluster().size(),1) * 2);

        String userPath = System.getProperty("user.dir") + File.separator + serverId;

        this.logFile = new File(userPath + File.separator + "raftLog.txt");
        MyRaftFileUtil.createFile(logFile);

        File logMetaDataFile = new File(userPath + File.separator + "raftLogMeta" + serverId + ".txt");
        MyRaftFileUtil.createFile(logMetaDataFile);

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
                // 之前的日志为空，lastIndex初始化为-1
                this.lastIndex = -1;
            }
        }
    }

    /**
     * 按照顺序追加写入日志
     * */
    public synchronized void writeLocalLog(LogEntry logEntry){
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

            // 设置最后写入的索引编号，lastIndex
            setLastIndex(logEntry.getLogIndex());
        } catch (IOException e) {
            throw new MyRaftException("logModule writeLog error!",e);
        }
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * */
    public synchronized LogEntry readLocalLog(long logIndex) {
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
    public synchronized List<LogEntry> readLocalLog(long logIndexStart, long logIndexEnd) {
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
    private synchronized List<LogEntry> readLocalLogNoSort(long logIndexStart, long logIndexEnd) {
        if(logIndexStart > logIndexEnd){
            throw new MyRaftException("readLocalLog logIndexStart > logIndexEnd! " +
                "logIndexStart=" + logIndexStart + " logIndexEnd=" + logIndexEnd);
        }

        // 链表效率高一点
        List<LogEntry> logEntryList = new LinkedList<>();
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
    public synchronized void deleteLocalLog(long logIndexNeedDelete){
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
    public synchronized List<AppendEntriesRpcResult> replicationLogEntry(LogEntry lastEntry) {
        List<RaftService> otherNodeInCluster = currentServer.getOtherNodeInCluster();

        List<Future<AppendEntriesRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());

        for(RaftService node : otherNodeInCluster){
            // 并行发送rpc，要求follower复制日志
            Future<AppendEntriesRpcResult> future = this.rpcThreadPool.submit(()->{
                long nextIndex = this.currentServer.getNextIndexMap().get(node);

                AppendEntriesRpcResult finallyResult = null;

                // If last log index ≥ nextIndex for a follower: send AppendEntries RPC with log entries starting at nextIndex
                while(lastEntry.getLogIndex() >= nextIndex){
                    AppendEntriesRpcParam appendEntriesRpcParam = new AppendEntriesRpcParam();
                    appendEntriesRpcParam.setLeaderId(currentServer.getServerId());
                    appendEntriesRpcParam.setTerm(currentServer.getCurrentTerm());
                    appendEntriesRpcParam.setLeaderCommit(this.lastCommittedIndex);

                    // nextIndex至少为1，所以不必担心-1会找不到日志记录
                    List<LogEntry> logEntryList = this.readLocalLog(nextIndex-1,nextIndex);
                    if(logEntryList.size() == 2){
                        LogEntry preLogEntry = logEntryList.get(0);

                        appendEntriesRpcParam.setEntries(Collections.singletonList(logEntryList.get(1)));
                        appendEntriesRpcParam.setPrevLogIndex(preLogEntry.getLogIndex());
                        appendEntriesRpcParam.setPrevLogTerm(preLogEntry.getLogTerm());
                    }else if(logEntryList.size() == 1){
                        // 日志长度为1,说明是第一条日志记录
                        logEntryList.add(logEntryList.get(0));
                        appendEntriesRpcParam.setEntries(Collections.singletonList(logEntryList.get(0)));
                        // 第一条记录的prev的index和term都是-1
                        appendEntriesRpcParam.setPrevLogIndex(-1);
                        appendEntriesRpcParam.setPrevLogTerm(-1);
                    }else{
                        // 日志长度不是1也不是2，日志模块有bug
                        throw new MyRaftException("replicationLogEntry logEntryList size error!" + logEntryList.size());
                    }

                    AppendEntriesRpcResult appendEntriesRpcResult = node.appendEntries(appendEntriesRpcParam);
                    finallyResult = appendEntriesRpcResult;
                    // 收到更高任期的处理
                    boolean beFollower = currentServer.processCommunicationHigherTerm(appendEntriesRpcResult.getTerm());
                    if(beFollower){
                        return appendEntriesRpcResult;
                    }

                    if(appendEntriesRpcResult.isSuccess()){
                        // 同步成功了，nextIndex递增一位

                        // If successful: update nextIndex and matchIndex for follower (§5.3)
                        nextIndex++;
                        this.currentServer.getNextIndexMap().put(node,nextIndex);
                        this.currentServer.getMatchIndexMap().put(node,nextIndex);
                    }else{
                        // 因为日志对不上导致一致性检查没通过，同步没成功，nextIndex往后退一位

                        // If AppendEntries fails because of log inconsistency: decrement nextIndex and retry (§5.3)
                        nextIndex--;
                        this.currentServer.getNextIndexMap().put(node,nextIndex);
                    }
                }

                if(finallyResult == null){
                    // 有bug
                    throw new MyRaftException("replicationLogEntry finallyResult is null!");
                }

                return finallyResult;
            });

            futureList.add(future);
        }

        // 获得结果
        List<AppendEntriesRpcResult> appendEntriesRpcResultList = CommonUtil.concurrentGetRpcFutureResult(
                "do appendEntries", futureList,
                this.rpcThreadPool,1, TimeUnit.SECONDS);

        return appendEntriesRpcResultList;
    }

    public synchronized LogEntry getLastLogEntry(){
        return readLocalLog(this.lastIndex);
    }

    // ============================= get/set ========================================

    public long getLastIndex() {
        return lastIndex;
    }

    public synchronized void setLastIndex(long lastIndex) {
        this.lastIndex = lastIndex;
    }

    public long getLastCommittedIndex() {
        return lastCommittedIndex;
    }

    public synchronized void setLastCommittedIndex(long lastCommittedIndex) {
        if(lastCommittedIndex < this.lastCommittedIndex){
            throw new MyRaftException("set lastCommittedIndex error this.lastCommittedIndex=" + this.lastCommittedIndex
                    + " lastCommittedIndex=" + lastCommittedIndex);
        }

        this.lastCommittedIndex = lastCommittedIndex;
    }

    public synchronized long getLastApplied() {
        return lastApplied;
    }

    public synchronized void setLastApplied(long lastApplied) {
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

    private synchronized LogEntry readLocalLogByOffset(RandomAccessFile randomAccessFile, long logIndex) throws IOException {
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

    private synchronized void refreshMetadata() throws IOException {
        this.logMetaDataFile.seek(0);
        this.logMetaDataFile.writeLong(this.currentOffset);
    }
}
