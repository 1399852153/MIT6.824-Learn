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
                newSnapshotFile.writeInt(raftSnapshot.getSnapshotData().length);
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

    public RaftSnapshot readLatestSnapshot(){
        logger.info("do readLatestSnapshot");

        readLock.lock();

        try(RandomAccessFile latestSnapshotRaFile = new RandomAccessFile(this.snapshotFile, "r")) {
            logger.info("do persistentNewSnapshotFile");

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
