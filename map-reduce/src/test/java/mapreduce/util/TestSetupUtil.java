package mapreduce.util;

import mapreduce.exception.MapReduceException;

import java.io.*;

public class TestSetupUtil {

    public static File generateSerializationMapReduceInputFile(int totalNum){
        String userPath = System.getProperty("user.dir");
        String fileName = userPath + "/src/test/SerializationMapReduceInputFile.txt";

        File file = new File(fileName);
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new MapReduceException("TestSetupUtil.generateSerializationMapReduceInputFile error",e);
            }
        }

        try(BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))){
            for(int i=0; i<totalNum; i++){
                bufferedWriter.write(i+"");
                bufferedWriter.write(System.lineSeparator());
            }
        }catch (IOException e){
            throw new MapReduceException("TestSetupUtil.generateSerializationMapReduceInputFile error",e);
        }

        return file;
    }
}
