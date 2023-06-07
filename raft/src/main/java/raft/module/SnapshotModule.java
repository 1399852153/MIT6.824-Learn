package raft.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.RaftServer;
import raft.api.model.InstallSnapshotRpcParam;
import raft.common.model.RaftSnapshot;
import raft.exception.MyRaftException;
import raft.util.MyRaftFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SnapshotModule {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotModule.class);

    private final RaftServer currentServer;

    private final File snapshotFile;

    private final ReentrantReadWriteLock reentrantLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = reentrantLock.writeLock();
    private final ReentrantReadWriteLock.ReadLock readLock = reentrantLock.readLock();

    private static final String snapshotFileName = "snapshot.txt";
    private static final String snapshotTempFileName = "snapshot-temp.txt";

    /**
     * 存放快照实际数据的偏移量(lastIncludedIndex + lastIncludedTerm 共两个字段后存放快照)
     * */
    private static final int actualDataOffset = 4 + 8;

    public SnapshotModule(RaftServer currentServer) {
        this.currentServer = currentServer;

        // 保证目录是存在的
        String snapshotFileDir = getSnapshotFileDir();
        new File(snapshotFileDir).mkdirs();

        snapshotFile = new File(snapshotFileDir + File.separator + snapshotFileName);

        File snapshotTempFile = new File(snapshotFileDir + File.separator + snapshotTempFileName);

        if(!snapshotFile.exists() && snapshotTempFile.exists()){
            // 快照文件不存在，但是快照的临时文件存在。说明在写完临时文件并重命名之前宕机了(临时文件是最新的完整快照)

            // 将tempFile重命名为快照文件
            snapshotTempFile.renameTo(snapshotFile);

            logger.info("snapshot-temp file rename to snapshot file success!");
        }
    }

    /**
     * 持久化一个新的快照文件
     * (暂不考虑快照太大的问题)
     * */
    public void persistentNewSnapshotFile(RaftSnapshot raftSnapshot){
        logger.info("do persistentNewSnapshotFile raftSnapshot={}",raftSnapshot);
        writeLock.lock();

        try {
            String userPath = getSnapshotFileDir();

            // 新的文件名是tempFile
            String newSnapshotFilePath = userPath + File.separator + snapshotTempFileName;
            logger.info("do persistentNewSnapshotFile newSnapshotFilePath={}", newSnapshotFilePath);

            File snapshotTempFile = new File(newSnapshotFilePath);
            try (RandomAccessFile newSnapshotFile = new RandomAccessFile(snapshotTempFile, "rw")) {
                MyRaftFileUtil.createFile(snapshotTempFile);

                newSnapshotFile.writeInt(raftSnapshot.getLastIncludedTerm());
                newSnapshotFile.writeLong(raftSnapshot.getLastIncludedIndex());
                newSnapshotFile.write(raftSnapshot.getSnapshotData());

                logger.info("do persistentNewSnapshotFile success! raftSnapshot={}", raftSnapshot);
            } catch (IOException e) {
                throw new MyRaftException("persistentNewSnapshotFile error", e);
            }

            // 先删掉原来的快照文件，然后把临时文件重名名为快照文件(delete后、重命名前可能宕机，但是没关系，重启后构造方法里做了对应处理)
            snapshotFile.delete();
            snapshotTempFile.renameTo(snapshotFile);
        }finally {
            writeLock.unlock();
        }
    }

    public void appendInstallSnapshot(InstallSnapshotRpcParam installSnapshotRpcParam){
        logger.info("do appendInstallSnapshot installSnapshotRpcParam={}",installSnapshotRpcParam);
        writeLock.lock();

        // 直接覆盖当前的快照文件
        try (RandomAccessFile newSnapshotFile = new RandomAccessFile(snapshotFile, "rw")) {
            if(installSnapshotRpcParam.getOffset() == 0){
                newSnapshotFile.seek(0);
                newSnapshotFile.setLength(0);

                newSnapshotFile.writeInt(installSnapshotRpcParam.getLastIncludedTerm());
                newSnapshotFile.writeLong(installSnapshotRpcParam.getLastIncludedIndex());
            }

            // 文件指针偏移，找到实际应该写入快照数据的地方
            newSnapshotFile.seek(actualDataOffset + installSnapshotRpcParam.getOffset());
            // 写入快照数据
            newSnapshotFile.write(installSnapshotRpcParam.getData());

            logger.info("do appendInstallSnapshot success! installSnapshotRpcParam={}", installSnapshotRpcParam);
        } catch (IOException e) {
            throw new MyRaftException("appendInstallSnapshot error", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 没有实际快照数据，只有元数据
     * */
    public RaftSnapshot readSnapshotMetaData(){
        readLock.lock();

        if(this.snapshotFile.length() == 0){
            return null;
        }

        try{
            try(RandomAccessFile latestSnapshotRaFile = new RandomAccessFile(this.snapshotFile, "r")) {
                logger.info("do readSnapshotNoData");

                RaftSnapshot raftSnapshot = new RaftSnapshot();
                raftSnapshot.setLastIncludedTerm(latestSnapshotRaFile.readInt());
                raftSnapshot.setLastIncludedIndex(latestSnapshotRaFile.readLong());

                logger.info("readSnapshotNoData success! readSnapshotNoData={}",raftSnapshot);
                return raftSnapshot;
            } catch (IOException e) {
                throw new MyRaftException("readSnapshotNoData error",e);
            } finally {
                readLock.unlock();
            }
        }finally {
            readLock.unlock();
        }
    }

    public RaftSnapshot readSnapshot(){
        logger.info("do readSnapshot");

        if(this.snapshotFile.length() == 0){
            logger.info("snapshotFile is empty!");
            return null;
        }

        readLock.lock();

        try(RandomAccessFile latestSnapshotRaFile = new RandomAccessFile(this.snapshotFile, "r")) {
            logger.info("do readSnapshot");

            RaftSnapshot latestSnapshot = new RaftSnapshot();
            latestSnapshot.setLastIncludedTerm(latestSnapshotRaFile.readInt());
            latestSnapshot.setLastIncludedIndex(latestSnapshotRaFile.readLong());

            // 读取snapshot的实际数据(简单起见，暂不考虑快照太大内存溢出的问题，可以优化为按照偏移量多次读取)
            byte[] snapshotData = new byte[(int) (this.snapshotFile.length() - actualDataOffset)];
            latestSnapshotRaFile.read(snapshotData);
            latestSnapshot.setSnapshotData(snapshotData);

            logger.info("readSnapshot success! readSnapshot={}",latestSnapshot);
            return latestSnapshot;
        } catch (IOException e) {
            throw new MyRaftException("readSnapshot error",e);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 用于单元测试
     * */
    public void clean() {
        System.out.println("snapshot module clean!");
        System.out.println(this.snapshotFile.delete());
    }

    private String getSnapshotFileDir(){
        return System.getProperty("user.dir")
            + File.separator + currentServer.getServerId()
            + File.separator + "snapshot";
    }
}
