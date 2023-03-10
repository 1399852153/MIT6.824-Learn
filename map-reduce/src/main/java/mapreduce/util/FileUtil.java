package mapreduce.util;

import mapreduce.common.KVPair;
import mapreduce.exception.MapReduceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    /**
     * 基于文件名，读取整个文件 (不考虑大文件内存不够的问题)
     * */
    public static String getFileContent(String filePath){
        File file = new File(filePath);
        byte[] bytes = new byte[1024];

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        try(FileInputStream fileInputStream = new FileInputStream(file)) {
            int actualRead;
            while((actualRead = fileInputStream.read(bytes)) != -1){
                byteArrayOutputStream.write(bytes,0,actualRead);
            }

            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new MapReduceException("FileUtil.getFileContent error",e);
        }
    }

    public static void cleanFileDir(File fileDir){
        if(!fileDir.isDirectory()){
            throw new MapReduceException("fileDir is not a directory：" + fileDir.getName());
        }

        if(fileDir.listFiles() != null) {
            for (File file : fileDir.listFiles()) {
                file.delete();
            }
        }
    }

    public static void writeInKVPairsInFile(File file, List<KVPair> kvPairs){
        if(file.exists()){
            // 保证幂等性，如果之前文件已经存在了，直接删掉(避免写了一半然后宕机，或者重复执行之类的问题)
            file.delete();
            logger.info("file exists before write! delete it, fileName={}",file.getName());
        }

        try {
            file.getParentFile().mkdirs();
            // 创建文件
            file.createNewFile();
        } catch (IOException e) {
            throw new MapReduceException("writeInFile error! fileName=" + file.getName(), e);
        }

        try(BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))){
            for(KVPair kvItem : kvPairs){
                // 一个k/v对，key和value各占一行
                bufferedWriter.write(kvItem.getKey());
                bufferedWriter.write(System.lineSeparator());
                bufferedWriter.write(kvItem.getValue());
                bufferedWriter.write(System.lineSeparator());
            }
        }catch (IOException e){
            throw new MapReduceException("FileUtil.appendFile error",e);
        }
    }

    public static List<KVPair> readKvPairsFromMapTempFile(File file) {
        if (!file.exists()) {
            throw new MapReduceException("readKvPairsFromMapTempFile file not exists" + file.getName());
        }

        List<KVPair> kvPairList = new ArrayList<>();
        try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))){
            String line;
            while((line = bufferedReader.readLine()) != null){
                String key = line;
                // 要求k/v的行数是成对的，直接readLine
                String value = bufferedReader.readLine();

                kvPairList.add(new KVPair(key,value));
            }

            return kvPairList;
        } catch (IOException e) {
            throw new MapReduceException("FileUtil.readKvPairsFromMapTempFile read file error",e);
        }
    }
}
