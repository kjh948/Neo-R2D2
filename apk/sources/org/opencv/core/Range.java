package org.opencv.core;

import android.support.v7.widget.ActivityChooserView;

/* JADX INFO: loaded from: classes.dex */
public class Range {
    public int end;
    public int start;

    public Range(int s, int e) {
        this.start = s;
        this.end = e;
    }

    public Range() {
        this(0, 0);
    }

    public Range(double[] vals) {
        set(vals);
    }

    public void set(double[] vals) {
        if (vals != null) {
            this.start = vals.length > 0 ? (int) vals[0] : 0;
            this.end = vals.length > 1 ? (int) vals[1] : 0;
        } else {
            this.start = 0;
            this.end = 0;
        }
    }

    public int size() {
        if (empty()) {
            return 0;
        }
        return this.end - this.start;
    }

    public boolean empty() {
        return this.end <= this.start;
    }

    public static Range all() {
        return new Range(Integer.MIN_VALUE, ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
    }

    public Range intersection(Range r1) {
        Range r = new Range(Math.max(r1.start, this.start), Math.min(r1.end, this.end));
        r.end = Math.max(r.end, r.start);
        return r;
    }

    public Range shift(int delta) {
        return new Range(this.start + delta, this.end + delta);
    }

    public Range clone() {
        return new Range(this.start, this.end);
    }

    public int hashCode() {
        long temp = Double.doubleToLongBits(this.start);
        int result = ((int) ((temp >>> 32) ^ temp)) + 31;
        long temp2 = Double.doubleToLongBits(this.end);
        return (result * 31) + ((int) ((temp2 >>> 32) ^ temp2));
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Range)) {
            return false;
        }
        Range it = (Range) obj;
        return this.start == it.start && this.end == it.end;
    }

    public String toString() {
        return "[" + this.start + ", " + this.end + ")";
    }
}
