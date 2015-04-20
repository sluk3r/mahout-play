/**
 * Created by wangxichun on 2015/4/8.
 */
package cn.sluk3r.extractComments.domain;


public class InputInfo {
    String sourceCodeDirectory;
    DateDuration dateDuration;

    public String getSourceCodeDirectory() {
        return sourceCodeDirectory;
    }

    public void setSourceCodeDirectory(String sourceCodeDirectory) {
        this.sourceCodeDirectory = sourceCodeDirectory;
    }

    public DateDuration getDateDuration() {
        return dateDuration;
    }

    public void setDateDuration(DateDuration dateDuration) {
        this.dateDuration = dateDuration;
    }
}
