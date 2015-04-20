package cn.sluk3r.extractComments.domain;

import java.util.Date;

/**
 * Created by wangxichun on 2015/4/8.
 */
public class DateDuration {
    Date startDate;
    Date endDate;

    public DateDuration() {
    }

    public DateDuration(Date startDate, Date endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}
