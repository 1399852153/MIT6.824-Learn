package raft.module;

import myrpc.serialize.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.command.Command;
import raft.api.model.*;
import raft.api.service.RaftService;
import raft.common.model.RaftSnapshot;
import raft.exception.MyRaftException;
import raft.util.CommonUtil;
import raft.util.MyRaftFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LogModule {

    private static final Logger logger = LoggerFactory.getLogger(LogModule.class);

    private static final int LONG_SIZE = 8;

    private final File logFile;
    private final File logMetaDataFile;

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
    private volatile long lastCommittedIndex = -1;

    /**
     * 作用到状态机上，日志就认为是已应用
     * */
    private volatile long lastApplied = -1;

    private final ExecutorService rpcThreadPool;

    private final RaftServer currentServer;

    private final ReentrantReadWriteLock reentrantLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = reentrantLock.writeLock();
    private final ReentrantReadWriteLock.ReadLock readLock = reentrantLock.readLock();

    private static final String logFileName = "raftLog.txt";
    private static final String logMetaDataFileName = "raftLogMeta.txt";
    private static final String logTempFileName = "raftLog-temp.txt";
    private static final String logMetaDataTempFileName = "raftLogMeta-temp.txt";

    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);

    public LogModule(RaftServer currentServer) throws IOException {
        this.currentServer = currentServer;

        int threadPoolSize = Math.max(currentServer.getOtherNodeInCluster().size(),1) * 2;
        this.rpcThreadPool = new ThreadPoolExecutor(threadPoolSize, threadPoolSize * 2,
            0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());

        String logFileDir = getLogFileDir();

        this.logMetaDataFile = new File(logFileDir + File.separator + logMetaDataFileName);
        MyRaftFileUtil.createFile(logMetaDataFile);

        this.logFile = new File(logFileDir + File.separator + logFileName);
        MyRaftFileUtil.createFile(logFile);


        File logMetaDataTempFile = new File(logFileDir + File.separator + logMetaDataTempFileName);

        // 临时的元数据文件存在，说明在日志压缩时宕机了(没来得及完成rename)，需要恢复(这个时候新的日志文件已经生成好了)
        if(logMetaDataTempFile.exists() && logMetaDataTempFile.length() > 0){
            File logTempFile = new File(logFileDir + File.separator + logTempFileName);
            if(logTempFile.exists()){
                // tempLog替换掉老的日志文件
                logTempFile.renameTo(logFile);
            }

            // 元数据文件也替换掉
            logMetaDataTempFile.renameTo(logMetaDataFile);
        }

        try(RandomAccessFile randomAccessLogMetaDataFile = new RandomAccessFile(logMetaDataFile, "r")) {
            if (randomAccessLogMetaDataFile.length() >= LONG_SIZE) {
                this.currentOffset = randomAccessLogMetaDataFile.readLong();
            } else {
                this.currentOffset = 0;
            }
        }

        try(RandomAccessFile randomAccessLogFile = new RandomAccessFile(logFile,"r")) {
            // 尝试读取之前已有的日志文件，找到最后一条日志的index
            if (this.currentOffset >= LONG_SIZE) {
                // 跳转到最后一个记录的offset处
                randomAccessLogFile.seek(this.currentOffset - LONG_SIZE);

                // 获得记录的offset
                long entryOffset = randomAccessLogFile.readLong();
                // 跳转至对应位置
                randomAccessLogFile.seek(entryOffset);

                this.lastIndex = randomAccessLogFile.readLong();
            }else{
                // 可能全被压缩掉了，也可能是确实没有日志

                RaftSnapshot raftSnapshot = this.currentServer.getSnapshotModule().readSnapshotMetaData();
                if(raftSnapshot == null){
                    // 确实没有日志lastIndex初始化为-1
                    this.lastIndex = -1;
                }else{
                    // 全被压缩了，从快照里获取
                    this.lastIndex = raftSnapshot.getLastIncludedIndex();
                }
            }
        }

        /*
          启动一个定时任务，检查日志文件是否超过阈值。
          如果超过了，则找状态机获得一份快照（写入快照文件中），然后删除已提交的日志(加写锁，把当前未提交的日志列表读取出来写到一份新的文件里，令日志文件为新生成的文件)
          */
        scheduledExecutorService.scheduleWithFixedDelay(()->{
            if(currentServer.getRaftConfig().getLogFileThreshold() <= 0){
                // 相当于没配置，不生成快照
                return;
            }

            buildSnapshotCheck();
        },10,10,TimeUnit.SECONDS);  // 为了测试时更快看到效果，10秒就检查一下
    }

    /**
     * 按照顺序追加写入日志
     * */
    public void writeLocalLog(LogEntry logEntry){
        boolean lockSuccess = writeLock.tryLock();
        if(!lockSuccess){
            logger.error("writeLocalLog lock error!");
            return;
        }

        try(RandomAccessFile randomAccessFile = new RandomAccessFile(logFile,"rw")){
            // 追加写入
            randomAccessFile.seek(logFile.length());

            writeLog(randomAccessFile,logEntry);
            randomAccessFile.writeLong(this.currentOffset);

            // 更新偏移量
            this.currentOffset = randomAccessFile.getFilePointer();

            // 持久化currentOffset的值，二阶段提交修改currentOffset的值，宕机恢复时以持久化的值为准
            refreshMetadata(this.logMetaDataFile,this.currentOffset);

            // 设置最后写入的索引编号，lastIndex
            this.lastIndex = logEntry.getLogIndex();
        } catch (IOException e) {
            throw new MyRaftException("logModule writeLog error!",e);
        } finally {
            writeLock.unlock();
        }
    }

    private static void writeLog(RandomAccessFile randomAccessFile,LogEntry logEntry) throws IOException {
        randomAccessFile.writeLong(logEntry.getLogIndex());
        randomAccessFile.writeInt(logEntry.getLogTerm());

        byte[] commandBytes = JsonUtil.obj2Str(logEntry.getCommand()).getBytes(StandardCharsets.UTF_8);
        randomAccessFile.writeInt(commandBytes.length);
        randomAccessFile.write(commandBytes);
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * */
    public LogEntry readLocalLog(long logIndex) {
        readLock.lock();

        try {
            List<LogEntry> logEntryList = readLocalLogNoSort(logIndex, logIndex);
            if (logEntryList.isEmpty()) {
                return null;
            } else {
                // 只会有1个
                return logEntryList.get(0);
            }
        }finally {
            readLock.unlock();
        }
    }

    /**
     * 根据日志索引号，获得对应的日志记录
     * 左右闭区间（logIndexStart <= {index} <= logIndexEnd）
     * */
    public List<LogEntry> readLocalLog(long logIndexStart, long logIndexEnd) {
        readLock.lock();

        try {
            // 读取出来的时候是index从大到小排列的
            List<LogEntry> logEntryList = readLocalLogNoSort(logIndexStart, logIndexEnd);

            // 翻转一下，令其按index从小到大排列
            Collections.reverse(logEntryList);

            return logEntryList;
        }finally {
            readLock.unlock();
        }
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

        boolean lockSuccess = readLock.tryLock();
        if(!lockSuccess){
            throw new MyRaftException("readLocalLogNoSort lock error!");
        }

        try {
            List<LogEntry> logEntryList = new ArrayList<>();
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(this.logFile, "r")) {
                // 从后往前找
                long offset = this.currentOffset;

                if (offset >= LONG_SIZE) {
                    // 跳转到最后一个记录的offset处
                    randomAccessFile.seek(offset - LONG_SIZE);
                }

                while (offset > 0) {
                    // 获得记录的offset
                    long entryOffset = randomAccessFile.readLong();
                    // 跳转至对应位置
                    randomAccessFile.seek(entryOffset);

                    long targetLogIndex = randomAccessFile.readLong();
                    if (targetLogIndex < logIndexStart) {
                        // 从下向上找到的顺序，如果已经小于参数指定的了，说明日志里根本就没有需要的日志条目，直接返回null
                        return logEntryList;
                    }

                    if (targetLogIndex <= logIndexEnd) {
                        // 找到的符合要求
                        logEntryList.add(readLocalLogByOffset(randomAccessFile, targetLogIndex));
                    } else {
                        // 不符合要求

                        // 跳过一些
                        randomAccessFile.readInt();
                        int commandLength = randomAccessFile.readInt();
                        randomAccessFile.read(new byte[commandLength]);
                    }

                    // preLogOffset
                    offset = randomAccessFile.readLong();
                    if (offset < LONG_SIZE) {
                        // 整个文件都读完了
                        return logEntryList;
                    }

                    // 跳转到记录的offset处
                    randomAccessFile.seek(offset - LONG_SIZE);
                }
            } catch (IOException e) {
                throw new MyRaftException("logModule readLog error!", e);
            }

            // 找遍了整个文件，也没找到，返回null
            return logEntryList;
        }finally {
            readLock.unlock();
        }
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

        boolean lockSuccess = writeLock.tryLock();
        if(!lockSuccess){
            logger.error("deleteLocalLog lock error!");
            return;
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
                    refreshMetadata(this.logMetaDataFile,this.currentOffset);
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
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 向集群广播，令follower复制新的日志条目
     * */
    public List<AppendEntriesRpcResult> replicationLogEntry(LogEntry lastEntry) {
        List<RaftService> otherNodeInCluster = currentServer.getOtherNodeInCluster();

        List<Future<AppendEntriesRpcResult>> futureList = new ArrayList<>(otherNodeInCluster.size());

        for(RaftService node : otherNodeInCluster){
            // 并行发送rpc，要求follower复制日志
            Future<AppendEntriesRpcResult> future = this.rpcThreadPool.submit(()->{
                logger.info("replicationLogEntry start!");

                long nextIndex = this.currentServer.getNextIndexMap().get(node);

                AppendEntriesRpcResult finallyResult = null;

                // If last log index ≥ nextIndex for a follower: send AppendEntries RPC with log entries starting at nextIndex
                while(lastEntry.getLogIndex() >= nextIndex){
                    AppendEntriesRpcParam appendEntriesRpcParam = new AppendEntriesRpcParam();
                    appendEntriesRpcParam.setLeaderId(currentServer.getServerId());
                    appendEntriesRpcParam.setTerm(currentServer.getCurrentTerm());
                    appendEntriesRpcParam.setLeaderCommit(this.lastCommittedIndex);

                    // nextIndex至少为1，所以不必担心-1会为负数
                    List<LogEntry> logEntryList = this.readLocalLog(nextIndex-1,nextIndex);
                    if(logEntryList.size() == 2){
                        // 一般情况能查出两条日志，一个是要同步的日志，一个是要同步日志的前一条记录
                        LogEntry preLogEntry = logEntryList.get(0);

                        appendEntriesRpcParam.setEntries(Collections.singletonList(logEntryList.get(1)));
                        appendEntriesRpcParam.setPrevLogIndex(preLogEntry.getLogIndex());
                        appendEntriesRpcParam.setPrevLogTerm(preLogEntry.getLogTerm());
                    }else if(logEntryList.size() == 1){
                        RaftSnapshot readSnapshotNoData = currentServer.getSnapshotModule().readSnapshotMetaData();
                        logEntryList.add(logEntryList.get(0));
                        appendEntriesRpcParam.setEntries(Collections.singletonList(logEntryList.get(0)));

                        if(readSnapshotNoData == null){
                            // 日志长度为1,且没有快照，说明恰好是第一条日志记录
                            // 第一条记录的prev的index和term都是-1
                            appendEntriesRpcParam.setPrevLogIndex(-1);
                            appendEntriesRpcParam.setPrevLogTerm(-1);
                        }else{
                            // 日志长度为1，但有快照，说明发生了日志压缩恰好把上一条记录删掉了
                            // 使用快照里保存的上一条日志信息
                            appendEntriesRpcParam.setPrevLogIndex(readSnapshotNoData.getLastIncludedIndex());
                            appendEntriesRpcParam.setPrevLogTerm(readSnapshotNoData.getLastIncludedTerm());
                        }
                    }else if(logEntryList.isEmpty()){
                        // 日志压缩把要同步的日志删除掉了，只能使用installSnapshotRpc了
                        logger.info("can not find and log entry，maybe delete for log compress");
                        // 快照压缩导致leader更早的index日志已经不存在了

                        // 应该改为使用installSnapshot来同步进度
                        RaftSnapshot raftSnapshot = currentServer.getSnapshotModule().readSnapshot();
                        doInstallSnapshotRpc(node,raftSnapshot,currentServer);

                        // 走到这里，一般是成功的完成了快照的安装。目标follower目前已经有了包括lastIncludedIndex以及之前的所有日志
                        // 如果是因为成为follower快速返回，则再循环一次就结束了
                        nextIndex = raftSnapshot.getLastIncludedIndex()+1;
                        continue;
                    }else{
                        // 不符合预期，日志模块有bug
                        throw new MyRaftException("replicationLogEntry logEntryList size error!" +
                            " nextIndex=" + nextIndex + " logEntryList.size=" + logEntryList.size());
                    }

                    logger.info("leader do appendEntries start, node={}, appendEntriesRpcParam={}",node,appendEntriesRpcParam);
                    AppendEntriesRpcResult appendEntriesRpcResult = node.appendEntries(appendEntriesRpcParam);
                    logger.info("leader do appendEntries end, node={}, appendEntriesRpcResult={}",node,appendEntriesRpcResult);

                    finallyResult = appendEntriesRpcResult;
                    // 收到更高任期的处理
                    boolean beFollower = currentServer.processCommunicationHigherTerm(appendEntriesRpcResult.getTerm());
                    if(beFollower){
                        return appendEntriesRpcResult;
                    }

                    if(appendEntriesRpcResult.isSuccess()){
                        logger.info("appendEntriesRpcResult is success, node={}",node);
                        // 同步成功了，nextIndex递增一位

                        // If successful: update nextIndex and matchIndex for follower (§5.3)

                        this.currentServer.getNextIndexMap().put(node,nextIndex+1);
                        this.currentServer.getMatchIndexMap().put(node,nextIndex);

                        nextIndex++;
                    }else{
                        // 因为日志对不上导致一致性检查没通过，同步没成功，nextIndex往后退一位

                        logger.info("appendEntriesRpcResult is false, node={}",node);

                        // If AppendEntries fails because of log inconsistency: decrement nextIndex and retry (§5.3)
                        nextIndex--;
                        this.currentServer.getNextIndexMap().put(node,nextIndex);
                    }
                }

                if(finallyResult == null){
                    // 说明有bug
                    throw new MyRaftException("replicationLogEntry finallyResult is null!");
                }

                logger.info("finallyResult={},node={}",node,finallyResult);

                return finallyResult;
            });

            futureList.add(future);
        }

        // 获得结果
        List<AppendEntriesRpcResult> appendEntriesRpcResultList = CommonUtil.concurrentGetRpcFutureResult(
                "do appendEntries", futureList,
                this.rpcThreadPool,3, TimeUnit.SECONDS);

        logger.info("leader replicationLogEntry appendEntriesRpcResultList={}",appendEntriesRpcResultList);

        return appendEntriesRpcResultList;
    }

    /**
     * discard any existing or partial snapshot with a smaller index
     * 快照整体安装完毕，清理掉index小于等于快照中lastIncludedIndex的所有日志
     * */
    public void compressLogBySnapshot(InstallSnapshotRpcParam installSnapshotRpcParam){
        this.lastCommittedIndex = installSnapshotRpcParam.getLastIncludedIndex();
        if(this.lastIndex < this.lastCommittedIndex){
            this.lastIndex = this.lastCommittedIndex;
        }

        try {
            buildNewLogFileRemoveCommittedLog();
        } catch (IOException e) {
            throw new MyRaftException("compressLogBySnapshot error",e);
        }
    }

    public LogEntry getLastLogEntry(){
        LogEntry lastLogEntry = readLocalLog(this.lastIndex);
        if(lastLogEntry != null){
            return lastLogEntry;
        }else {
            RaftSnapshot raftSnapshot = this.currentServer.getSnapshotModule().readSnapshotMetaData();
            if(raftSnapshot != null){
                return LogEntry.getLogEntryBySnapshot(raftSnapshot);
            }else{
                return LogEntry.getEmptyLogEntry();
            }
        }
    }

    // ============================= get/set ========================================

    public long getLastIndex() {
        return lastIndex;
    }

    public long getLastCommittedIndex() {
        return lastCommittedIndex;
    }

    public void setLastCommittedIndex(long lastCommittedIndex) {
        writeLock.lock();

        try {
            if (lastCommittedIndex < this.lastCommittedIndex) {
                throw new MyRaftException("set lastCommittedIndex error this.lastCommittedIndex=" + this.lastCommittedIndex
                    + " lastCommittedIndex=" + lastCommittedIndex);
            }

            this.lastCommittedIndex = lastCommittedIndex;
        }finally {
            writeLock.unlock();
        }
    }

    public long getLastApplied() {
        return lastApplied;
    }

    public void setLastApplied(long lastApplied) {
        writeLock.lock();

        try {
            if (lastApplied < this.lastApplied) {
                throw new MyRaftException("set lastApplied error this.lastApplied=" + this.lastApplied
                    + " lastApplied=" + lastApplied);
            }

            this.lastApplied = lastApplied;
        }finally {
            writeLock.unlock();
        }
    }

    /**
     * 用于单元测试
     * */
    public void clean() {
        System.out.println("log module clean!");
        this.logFile.delete();
        this.logMetaDataFile.delete();
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

    private static void refreshMetadata(File logMetaDataFile,long currentOffset) throws IOException {
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(logMetaDataFile,"rw")){
            randomAccessFile.seek(0);
            randomAccessFile.writeLong(currentOffset);
        }
    }

    private String getLogFileDir(){
        return System.getProperty("user.dir")
            + File.separator + currentServer.getServerId();
    }

    private void buildSnapshotCheck() {
        try {
            if(!readLock.tryLock(1,TimeUnit.SECONDS)){
                logger.info("buildSnapshotCheck lock fail, quick return!");
                return;
            }
        } catch (InterruptedException e) {
            throw new MyRaftException("buildSnapshotCheck tryLock error!",e);
        }

        try {
            long logFileLength = this.logFile.length();
            long logFileThreshold = currentServer.getRaftConfig().getLogFileThreshold();
            if (logFileLength < logFileThreshold) {
//                logger.info("logFileLength not reach threshold, do nothing. logFileLength={},threshold={}", logFileLength, logFileThreshold);
                return;
            }

            logger.info("logFileLength already reach threshold, start buildSnapshot! logFileLength={},threshold={}", logFileLength, logFileThreshold);

            byte[] snapshot = currentServer.getKvReplicationStateMachine().buildSnapshot();
            LogEntry lastCommittedLogEntry = readLocalLog(this.lastCommittedIndex);

            RaftSnapshot raftSnapshot = new RaftSnapshot();
            raftSnapshot.setLastIncludedTerm(lastCommittedLogEntry.getLogTerm());
            raftSnapshot.setLastIncludedIndex(lastCommittedLogEntry.getLogIndex());
            raftSnapshot.setSnapshotData(snapshot);

            // 持久化最新的一份快照
            currentServer.getSnapshotModule().persistentNewSnapshotFile(raftSnapshot);
        }finally {
            readLock.unlock();
        }

        try {
            buildNewLogFileRemoveCommittedLog();
        } catch (IOException e) {
            logger.error("buildNewLogFileRemoveCommittedLog error",e);
        }
    }

    /**
     * 构建一个删除了已提交日志的新日志文件(日志压缩到快照里了)
     * */
    private void buildNewLogFileRemoveCommittedLog() throws IOException {
        long lastCommitted = getLastCommittedIndex();
        long lastIndex = getLastIndex();

        // 暂不考虑读取太多造成内存溢出的问题
        List<LogEntry> logEntryList;
        if(lastCommitted == lastIndex){
            // (lastCommitted == lastIndex) 所有日志都提交了，创建一个空的新日志文件
            logEntryList = new ArrayList<>();
        }else{
            // 还有日志没提交，把没提交的记录到新的日志文件中
            logEntryList = readLocalLog(lastCommitted+1,lastIndex);
        }

        File tempLogFile = new File(getLogFileDir() + File.separator + logTempFileName);
        MyRaftFileUtil.createFile(tempLogFile);
        try(RandomAccessFile randomAccessTempLogFile = new RandomAccessFile(tempLogFile,"rw")) {

            long currentOffset = 0;
            for (LogEntry logEntry : logEntryList) {
                writeLog(randomAccessTempLogFile, logEntry);
                randomAccessTempLogFile.writeLong(currentOffset);

                // 写入偏移量
                currentOffset = randomAccessTempLogFile.getFilePointer();
            }

            this.currentOffset = currentOffset;
        }

        File tempLogMeteDataFile = new File(getLogFileDir() + File.separator + logMetaDataTempFileName);
        MyRaftFileUtil.createFile(tempLogMeteDataFile);

        // 临时的日志元数据文件写入数据
        refreshMetadata(tempLogMeteDataFile,currentOffset);

        writeLock.lock();
        try{
            // 先删掉原来的日志文件，然后把临时文件重名名为日志文件(delete后、重命名前可能宕机，但是没关系，重启后构造方法里做了对应处理)
            this.logFile.delete();
            boolean renameLogFileResult = tempLogFile.renameTo(this.logFile);
            if(!renameLogFileResult){
                logger.error("renameLogFile error!");
            }

            // 先删掉原来的日志元数据文件，然后把临时文件重名名为日志元数据文件(delete后、重命名前可能宕机，但是没关系，重启后构造方法里做了对应处理)
            this.logMetaDataFile.delete();
            boolean renameTempLogMeteDataFileResult = tempLogMeteDataFile.renameTo(this.logMetaDataFile);
            if(!renameTempLogMeteDataFileResult){
                logger.error("renameTempLogMeteDataFile error!");
            }
        }finally {
            writeLock.unlock();
        }
    }

    public static void doInstallSnapshotRpc(RaftService targetNode, RaftSnapshot raftSnapshot, RaftServer currentServer){
        int installSnapshotBlockSize = currentServer.getRaftConfig().getInstallSnapshotBlockSize();
        byte[] completeSnapshotData = raftSnapshot.getSnapshotData();

        int currentOffset = 0;
        while(true){
            InstallSnapshotRpcParam installSnapshotRpcParam = new InstallSnapshotRpcParam();
            installSnapshotRpcParam.setLastIncludedIndex(raftSnapshot.getLastIncludedIndex());
            installSnapshotRpcParam.setTerm(currentServer.getCurrentTerm());
            installSnapshotRpcParam.setLeaderId(currentServer.getServerId());
            installSnapshotRpcParam.setLastIncludedTerm(raftSnapshot.getLastIncludedTerm());
            installSnapshotRpcParam.setOffset(currentOffset);

            // 填充每次传输的数据块
            int blockSize = Math.min(installSnapshotBlockSize,completeSnapshotData.length-currentOffset);
            byte[] block = new byte[blockSize];
            System.arraycopy(completeSnapshotData,currentOffset,block,0,blockSize);
            installSnapshotRpcParam.setData(block);

            currentOffset += installSnapshotBlockSize;
            if(currentOffset >= completeSnapshotData.length){
                installSnapshotRpcParam.setDone(true);
            }else{
                installSnapshotRpcParam.setDone(false);
            }

            InstallSnapshotRpcResult installSnapshotRpcResult = targetNode.installSnapshot(installSnapshotRpcParam);

            boolean beFollower = currentServer.processCommunicationHigherTerm(installSnapshotRpcResult.getTerm());
            if(beFollower){
                // 传输过程中发现自己已经不再是leader了，快速结束
                logger.info("doInstallSnapshotRpc beFollower quick return!");
                return;
            }

            if(installSnapshotRpcParam.isDone()){
                logger.info("doInstallSnapshotRpc isDone!");
                return;
            }
        }
    }
}
