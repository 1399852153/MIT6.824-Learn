package raft.module;

import com.fasterxml.jackson.core.type.TypeReference;
import myrpc.common.StringUtils;
import myrpc.serialize.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.api.command.EmptySetCommand;
import raft.api.command.SetCommand;
import raft.module.api.KVReplicationStateMachine;
import raft.util.MyRaftFileUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易复制状态机(持久化到磁盘中的最基础的k/v数据库)
 * 简单起见：内存中是一个k/v Map，每次写请求都全量写入磁盘
 * */
public class SimpleReplicationStateMachine implements KVReplicationStateMachine {
    private static final Logger logger = LoggerFactory.getLogger(SimpleReplicationStateMachine.class);

    private volatile ConcurrentHashMap<String,String> kvMap;

    private final File persistenceFile;

    public SimpleReplicationStateMachine(int serverId){
        String userPath = System.getProperty("user.dir") + File.separator + serverId;

        this.persistenceFile = new File(userPath + File.separator + "raftReplicationStateMachine" + serverId + ".txt");
        MyRaftFileUtil.createFile(persistenceFile);

        String fileContent = MyRaftFileUtil.getFileContent(persistenceFile);
        if(StringUtils.hasText(fileContent)){
            kvMap = JsonUtil.json2Obj(fileContent,new TypeReference<ConcurrentHashMap<String,String>>(){});
        }else{
            kvMap = new ConcurrentHashMap<>();
        }
    }

    @Override
    public void apply(SetCommand setCommand) {
        if(setCommand instanceof EmptySetCommand){
            // no-op，状态机无需做任何操作
            logger.info("apply EmptySetCommand quick return!");
            return;
        }

        logger.info("apply setCommand start,{}",setCommand);
        kvMap.put(setCommand.getKey(),setCommand.getValue());

        // 每次写操作完都持久化一遍(简单起见，暂时不考虑性能问题)
        MyRaftFileUtil.writeInFile(persistenceFile,JsonUtil.obj2Str(kvMap));
        logger.info("apply setCommand end");
    }

    @Override
    public String get(String key) {
        return kvMap.get(key);
    }

    @Override
    public void installSnapshot(byte[] snapshot) {
        String mapJson = new String(snapshot,StandardCharsets.UTF_8);
        this.kvMap = JsonUtil.json2Obj(mapJson, new TypeReference<ConcurrentHashMap<String, String>>() {});
    }

    @Override
    public byte[] buildSnapshot() {
        String mapJson = JsonUtil.obj2Str(kvMap);
        return mapJson.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 用于单元测试
     * */
    public void clean() {
        System.out.println("SimpleReplicationStateMachine clean!");
        this.persistenceFile.delete();
    }
}
