package cn.sluk3r.extractComments.scan;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * Created by wangxichun on 2015/4/10.
 */
public class FileScanTest {
    FileScan fileScan;

    @Before
    public void setUp() {
        fileScan = new FileScan();
        fileScan.setDeleteCommentPrefix(true);
    }

    @Test
    public void testExtractSrcLine() {
        fileScan.addPatterns(Pattern.compile("//wxc pro(.*)"));

        String commentContent = "2015-4-10:8:47:18 这个方法跟前面的getPreferencesFromUser有什么区别";
        String srcLine = "FastIDSet getItemIDsFromUser(long userID) throws TasteException; //wxc pro " + commentContent;
        String comment = fileScan.extractCommentFromLine(srcLine);

        assertNotNull(comment);
        assertTrue(comment.contains(commentContent));
        assertEquals(commentContent, comment);


        fileScan.removeAllPattern();
        fileScan.addPatterns(Pattern.compile("//wxc [^pro](.*)"));

        srcLine = "FastIDSet getItemIDsFromUser(long userID) throws TasteException; //wxc pro " + commentContent;
        comment = fileScan.extractCommentFromLine(srcLine);

        assertNull(comment);
        srcLine = "FastIDSet getItemIDsFromUser(long userID) throws TasteException; //wxc " + commentContent;
    }

    @Test
    public void testExtractFile() throws IOException {
        File file = new File("J:\\code\\github\\mahout-play\\mrlegacy\\src\\main\\java\\org\\apache\\mahout\\cf\\taste\\model\\DataModel.java");

        fileScan.addPatterns(Pattern.compile("//wxc (.*)"));

        List<String> comments = fileScan.extractComments(file);
        assertTrue(! comments.isEmpty());

        int i = 1;
        for(String c: comments) {
            System.out.println(String.format("%s. %s", i++, c));
        }
    }

    @Test
    public void driver() throws IOException {
        List<File> files = new ArrayList();
        files.add(new File("J:\\code\\github\\mahout-play\\mrlegacy\\src\\main\\java\\org\\apache\\mahout\\cf\\taste\\model\\DataModel.java"));
        files.add(new File("J:\\code\\github\\mahout-play\\mrlegacy\\src\\main\\java\\org\\apache\\mahout\\cf\\taste\\similarity\\UserSimilarity.java"));
        files.add(new File("J:\\code\\github\\mahout-play\\mrlegacy\\src\\main\\java\\org\\apache\\mahout\\cf\\taste\\similarity\\ItemSimilarity.java"));
        files.add(new File("J:\\code\\github\\mahout-play\\mrlegacy\\src\\main\\java\\org\\apache\\mahout\\cf\\taste\\similarity\\PreferenceInferrer.java"));


        for (File f: files) {
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(processSingleFile(f) + "\n\n");
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
