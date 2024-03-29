package raft.module;


import myrpc.common.StringUtils;
import myrpc.serialize.json.JsonUtil;
import raft.common.model.RaftServerMetaData;
import raft.util.MyRaftFileUtil;

import java.io.File;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RaftServerMetaDataPersistentModule {

    /**
     * 当前服务器的任期值
     * */
    private volatile int currentTerm;

    /**
     * 当前服务器在此之前投票给了谁？
     * (候选者的serverId，如果还没有投递就是null)
     * */
    private volatile Integer votedFor;

    private final File persistenceFile;

    private final ReentrantReadWriteLock reentrantLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = reentrantLock.writeLock();
    private final ReentrantReadWriteLock.ReadLock readLock = reentrantLock.readLock();

    public RaftServerMetaDataPersistentModule(int serverId) {
        String userPath = System.getProperty("user.dir") + File.separator + serverId;

        this.persistenceFile = new File(userPath + File.separator + "raftServerMetaData-" + serverId + ".txt");
        MyRaftFileUtil.createFile(persistenceFile);

        // 读取持久化在磁盘中的数据
        RaftServerMetaData raftServerMetaData = readRaftServerMetaData(persistenceFile);
        this.currentTerm = raftServerMetaData.getCurrentTerm();
        this.votedFor = raftServerMetaData.getVotedFor();
    }

    public int getCurrentTerm() {
        readLock.lock();
        try {
            return currentTerm;
        }finally {
            readLock.unlock();
        }
    }

    public void setCurrentTerm(int currentTerm) {
        writeLock.lock();
        try {
            this.currentTerm = currentTerm;

            // 更新后数据落盘
            persistentRaftServerMetaData(new RaftServerMetaData(this.currentTerm,this.votedFor),persistenceFile);
        }finally {
            writeLock.unlock();
        }
    }

    public Integer getVotedFor() {
        readLock.lock();
        try {
            return votedFor;
        }finally {
            readLock.unlock();
        }
    }

    public void setVotedFor(Integer votedFor) {
        writeLock.lock();
        try {
            this.votedFor = votedFor;

            // 更新后数据落盘
            persistentRaftServerMetaData(new RaftServerMetaData(this.currentTerm,this.votedFor),persistenceFile);
        }finally {
            writeLock.unlock();
        }
    }

    private static RaftServerMetaData readRaftServerMetaData(File persistenceFile){
        String content = MyRaftFileUtil.getFileContent(persistenceFile);
        if(StringUtils.hasText(content)){
            return JsonUtil.json2Obj(content,RaftServerMetaData.class);
        }else{
            return RaftServerMetaData.getDefault();
        }
    }

    private static void persistentRaftServerMetaData(RaftServerMetaData raftServerMetaData, File persistenceFile){
        String content = JsonUtil.obj2Str(raftServerMetaData);

        MyRaftFileUtil.writeInFile(persistenceFile,content);
    }
}
