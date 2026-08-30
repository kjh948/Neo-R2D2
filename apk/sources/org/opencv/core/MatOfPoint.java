package org.opencv.core;

import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MatOfPoint extends Mat {
    private static final int _channels = 2;
    private static final int _depth = 4;

    public MatOfPoint() {
    }

    protected MatOfPoint(long addr) {
        super(addr);
        if (!empty() && checkVector(2, 4) < 0) {
            throw new IllegalArgumentException("Incompatible Mat");
        }
    }

    public static MatOfPoint fromNativeAddr(long addr) {
        return new MatOfPoint(addr);
    }

    public MatOfPoint(Mat m) {
        super(m, Range.all());
        if (!empty() && checkVector(2, 4) < 0) {
            throw new IllegalArgumentException("Incompatible Mat");
        }
    }

    public MatOfPoint(Point... a) {
        fromArray(a);
    }

    public void alloc(int elemNumber) {
        if (elemNumber > 0) {
            super.create(elemNumber, 1, CvType.makeType(4, 2));
        }
    }

    public void fromArray(Point... a) {
        if (a != null && a.length != 0) {
            int num = a.length;
            alloc(num);
            int[] buff = new int[num * 2];
            for (int i = 0; i < num; i++) {
                Point p = a[i];
                buff[(i * 2) + 0] = (int) p.x;
                buff[(i * 2) + 1] = (int) p.y;
            }
            put(0, 0, buff);
        }
    }

    public Point[] toArray() {
        int num = (int) total();
        Point[] ap = new Point[num];
        if (num != 0) {
            int[] buff = new int[num * 2];
            get(0, 0, buff);
            for (int i = 0; i < num; i++) {
                ap[i] = new Point(buff[i * 2], buff[(i * 2) + 1]);
            }
        }
        return ap;
    }

    public void fromList(List<Point> lp) {
        Point[] ap = (Point[]) lp.toArray(new Point[0]);
        fromArray(ap);
    }

    public List<Point> toList() {
        Point[] ap = toArray();
        return Arrays.asList(ap);
    }
}
