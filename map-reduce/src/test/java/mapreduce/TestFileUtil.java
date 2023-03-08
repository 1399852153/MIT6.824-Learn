package mapreduce;

import mapreduce.util.FileUtil;
import org.junit.Assert;
import org.junit.Test;

public class TestFileUtil {

    @Test
    public void testGetFileContent(){
        String userPath = System.getProperty("user.dir");
        String testFilePath = userPath + "/src/test/test.txt";
        System.out.println(testFilePath);

        String content = FileUtil.getFileContent(testFilePath);
        System.out.println(content);

        Assert.assertTrue(content.startsWith("111"));
        Assert.assertTrue(content.contains("啊啊啊啊啊"));
        Assert.assertTrue(content.contains("333"));
        Assert.assertTrue(content.contains("666"));
        Assert.assertTrue(content.endsWith("111111"));
    }
}
