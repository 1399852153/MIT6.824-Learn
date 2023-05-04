package raft.module;

import com.fasterxml.jackson.core.type.TypeReference;
import myrpc.common.StringUtils;
import myrpc.serialize.json.JsonUtil;
import raft.api.command.SetCommand;
import raft.module.api.KVReplicationStateMachine;
import raft.util.FileUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 简易复制状态机(持久化到磁盘中的最基础的k/v数据库)
 * 简单起见：内存中是一个k/v Map，每次写请求都全量写入磁盘
 * */
public class SimpleReplicationStateMachine implements KVReplicationStateMachine {

    private final Map<String,String> kvMap;

    private final File persistenceFile;

    public SimpleReplicationStateMachine(File persistenceFile) {
        this.persistenceFile = persistenceFile;

        String fileContent = FileUtil.getFileContent(persistenceFile);
        if(StringUtils.hasText(fileContent)){
            kvMap = JsonUtil.json2Obj(fileContent,new TypeReference<Map<String,String>>(){});
        }else{
            kvMap = new HashMap<>();
        }
    }

    @Override
    public synchronized void apply(SetCommand setCommand) {
        kvMap.put(setCommand.getKey(),setCommand.getValue());

        // 每次写操作完都持久化一遍(简单起见，暂时不考虑性能问题)
        FileUtil.writeInFile(persistenceFile,JsonUtil.obj2Str(kvMap));
    }

    @Override
    public synchronized String get(String key) {
        return kvMap.get(key);
    }
}
