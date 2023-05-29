package raft.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.common.model.RaftSnapshot;
import raft.exception.MyRaftException;
import raft.util.MyRaftFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SnapshotModule {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotModule.class);

    private final RaftServer currentServer;

    private final ReentrantReadWriteLock reentrantLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = reentrantLock.writeLock();
    private final ReentrantReadWriteLock.ReadLock readLock = reentrantLock.readLock();

    public SnapshotModule(RaftServer currentServer) {
        this.currentServer = currentServer;

        // 保证目录是存在的
        String snapshotFileDir = getSnapshotFileDir();
        MyRaftFileUtil.createFile(new File(snapshotFileDir));
    }

    /**
     * 持久化一个新的快照文件
     * */
    public void persistentNewSnapshotFile(RaftSnapshot raftSnapshot){
        logger.info("do persistentNewSnapshotFile raftSnapshot={}",raftSnapshot);
        writeLock.lock();

        String userPath = getSnapshotFileDir();

        // 认为时间是准确的，值更大说明是更新的快照
        long currentTime = System.currentTimeMillis();
        String newSnapshotFilePath = userPath + File.separator + "snapshot-" + currentTime + ".txt";
        logger.info("do persistentNewSnapshotFile newSnapshotFilePath={}",newSnapshotFilePath);

        try {
            RandomAccessFile newSnapshotFile = new RandomAccessFile(new File(newSnapshotFilePath), "rw");
            newSnapshotFile.writeInt(raftSnapshot.getLastIncludedTerm());
            newSnapshotFile.writeLong(raftSnapshot.getLastIncludedIndex());
            newSnapshotFile.writeInt(raftSnapshot.getSnapshotData().length);
            newSnapshotFile.write(raftSnapshot.getSnapshotData());

            logger.info("do persistentNewSnapshotFile success! raftSnapshot={}",raftSnapshot);
        }catch (IOException e){
            throw new MyRaftException("persistentNewSnapshotFile error",e);
        }finally {
            writeLock.unlock();
        }
    }

    public RaftSnapshot readLatestSnapshot(){
        logger.info("do readLatestSnapshot");

        readLock.lock();

        String userPath = getSnapshotFileDir();

        try {
            File latestSnapshotFile = findLatestSnapshot(Objects.requireNonNull(new File(userPath).listFiles()));
            logger.info("do persistentNewSnapshotFile latestSnapshotFile={}",latestSnapshotFile);

            RandomAccessFile latestSnapshotRaFile = new RandomAccessFile(latestSnapshotFile, "r");

            RaftSnapshot latestSnapshot = new RaftSnapshot();
            latestSnapshot.setLastIncludedTerm(latestSnapshotRaFile.readInt());
            latestSnapshot.setLastIncludedIndex(latestSnapshotRaFile.readLong());

            // 读取snapshot的实际数据(暂不考虑快照太大的问题)
            int snapshotSize = latestSnapshotRaFile.readInt();
            byte[] snapshotData = new byte[snapshotSize];
            latestSnapshotRaFile.read(snapshotData);
            latestSnapshot.setSnapshotData(snapshotData);

            logger.info("readLatestSnapshot success! latestSnapshot={}",latestSnapshot);
            return latestSnapshot;
        } catch (IOException e) {
            throw new MyRaftException("readLatestSnapshot error",e);
        } finally {
            readLock.unlock();
        }
    }

    private File findLatestSnapshot(File[] files){
        if(files.length == 0){
            return null;
        }
        
        File maxFile = files[0];
        for(File currentFile : files){
            String maxFileName = maxFile.getName();
            String currentFileName = currentFile.getName();
            if(currentFileName.compareTo(maxFileName) > 0){
                maxFile = currentFile;
            }
        }

        return maxFile;
    }

    private String getSnapshotFileDir(){
        return System.getProperty("user.dir")
            + File.separator + currentServer.getServerId()
            + File.separator + "snapshot";
    }
}
