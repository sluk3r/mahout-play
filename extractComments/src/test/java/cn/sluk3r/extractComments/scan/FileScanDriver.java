package cn.sluk3r.extractComments.scan;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineReader;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertNotNull;

/**
 * Created by wangxichun on 2015/4/10.
 */
public class FileScanDriver {
    FileScan fileScan;

    @Before
    public void setUp() {
        fileScan = new FileScan();
        fileScan.setDeleteCommentPrefix(true);
    }

    @Test
    public void driver() throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("./extractFileList.txt");

        InputStreamReader reader = new InputStreamReader(is);
        BufferedReader lineReader = new BufferedReader(reader);

        String filePath = null;
        while((filePath = lineReader.readLine()) != null ) {
            File srcFile = new File(filePath);
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(processSingleFile(srcFile) + "\n\n");
        }
    }


    private String processSingleFile(File file) throws IOException {
        fileScan.addPatterns(Pattern.compile("//wxc (.*)"));

        List<String> comments = fileScan.extractComments(file);
        assertTrue(! comments.isEmpty());

        String filePath = file.getAbsolutePath();
        StringBuilder content = new StringBuilder(filePath.split("mahout-play")[1] + "\n");

        int i = 1;
        for(String c: comments) {
            content.append(String.format("%s. %s\n", i++, c));
        }
        return content.toString();
    }
}
