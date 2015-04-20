package cn.sluk3r.extractComments.scan;


import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by wangxichun on 2015/4/10.
 */
public class FileScan {
    Set<Pattern> patterns  = new HashSet<Pattern>(); //Pattern pattern = Pattern.compile("//(//d{3}//)//s//d{3}-//d{4}");
    boolean deleteCommentPrefix;


    public List<String> extractComments(File sourceCodefile) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(sourceCodefile));
        String line = null;
        List<String> commentLines = new ArrayList<String>();
        while((line = bufferedReader.readLine()) != null) {
            String comment = extractCommentFromLine(line);
            if (StringUtils.isNotBlank(comment)) {
                commentLines.add(comment);
            }
        }
        return commentLines;
    }

    String extractCommentFromLine(String line) {
        if (patterns != null && ! patterns.isEmpty()) {
            for (Pattern p: patterns) {
                Matcher matcher = p.matcher(line);
                if (matcher.find()) {
                    String comment =  deleteCommentPrefix ? matcher.group(1) : matcher.group(); //TODO 这一块可以抽出一个策略来。
                    return comment != null ? comment.trim() : null;
                }
            }
        }
        return null;
    }


    public Set<Pattern> getPatterns() {
        return patterns;
    }

    public void addPatterns(Pattern pattern) {
        this.patterns.add(pattern);
    }

    public boolean isDeleteCommentPrefix() {
        return deleteCommentPrefix;
    }

    public void setDeleteCommentPrefix(boolean deleteCommentPrefix) {
        this.deleteCommentPrefix = deleteCommentPrefix;
    }

    void removeAllPattern() {
        patterns.clear();
    }
}
