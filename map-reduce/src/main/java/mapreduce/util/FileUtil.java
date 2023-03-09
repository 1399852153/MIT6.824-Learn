package mapreduce.util;

import mapreduce.exception.MapReduceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    public static void writeInFile(File file, List<Map.Entry<String, String>> kvPairs){
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

        try(FileOutputStream fileOutputStream = new FileOutputStream(file)){
            for(Map.Entry<String,String> kvItem : kvPairs){
                // 一个k/v对，key和value各占一行
                fileOutputStream.write(kvItem.getKey().getBytes(StandardCharsets.UTF_8));
                fileOutputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                fileOutputStream.write(kvItem.getValue().getBytes(StandardCharsets.UTF_8));
                fileOutputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            }
        }catch (IOException e){
            throw new MapReduceException("FileUtil.appendFile error",e);
        }
    }
}
