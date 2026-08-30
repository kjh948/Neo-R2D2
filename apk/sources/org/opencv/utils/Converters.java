package org.opencv.utils;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;

/* JADX INFO: loaded from: classes.dex */
public class Converters {
    public static Mat vector_Point_to_Mat(List<Point> pts) {
        return vector_Point_to_Mat(pts, 4);
    }

    public static Mat vector_Point2f_to_Mat(List<Point> pts) {
        return vector_Point_to_Mat(pts, 5);
    }

    public static Mat vector_Point2d_to_Mat(List<Point> pts) {
        return vector_Point_to_Mat(pts, 6);
    }

    public static Mat vector_Point_to_Mat(List<Point> pts, int typeDepth) {
        int count = pts != null ? pts.size() : 0;
        if (count > 0) {
            switch (typeDepth) {
                case 4:
                    Mat res = new Mat(count, 1, CvType.CV_32SC2);
                    int[] buff = new int[count * 2];
                    for (int i = 0; i < count; i++) {
                        Point p = pts.get(i);
                        buff[i * 2] = (int) p.x;
                        buff[(i * 2) + 1] = (int) p.y;
                    }
                    res.put(0, 0, buff);
                    return res;
                case 5:
                    Mat res2 = new Mat(count, 1, CvType.CV_32FC2);
                    float[] buff2 = new float[count * 2];
                    for (int i2 = 0; i2 < count; i2++) {
                        Point p2 = pts.get(i2);
                        buff2[i2 * 2] = (float) p2.x;
                        buff2[(i2 * 2) + 1] = (float) p2.y;
                    }
                    res2.put(0, 0, buff2);
                    return res2;
                case 6:
                    Mat res3 = new Mat(count, 1, CvType.CV_64FC2);
                    double[] buff3 = new double[count * 2];
                    for (int i3 = 0; i3 < count; i3++) {
                        Point p3 = pts.get(i3);
                        buff3[i3 * 2] = p3.x;
                        buff3[(i3 * 2) + 1] = p3.y;
                    }
                    res3.put(0, 0, buff3);
                    return res3;
                default:
                    throw new IllegalArgumentException("'typeDepth' can be CV_32S, CV_32F or CV_64F");
            }
        }
        return new Mat();
    }

    public static Mat vector_Point3i_to_Mat(List<Point3> pts) {
        return vector_Point3_to_Mat(pts, 4);
    }

    public static Mat vector_Point3f_to_Mat(List<Point3> pts) {
        return vector_Point3_to_Mat(pts, 5);
    }

    public static Mat vector_Point3d_to_Mat(List<Point3> pts) {
        return vector_Point3_to_Mat(pts, 6);
    }

    public static Mat vector_Point3_to_Mat(List<Point3> pts, int typeDepth) {
        int count = pts != null ? pts.size() : 0;
        if (count > 0) {
            switch (typeDepth) {
                case 4:
                    Mat res = new Mat(count, 1, CvType.CV_32SC3);
                    int[] buff = new int[count * 3];
                    for (int i = 0; i < count; i++) {
                        Point3 p = pts.get(i);
                        buff[i * 3] = (int) p.x;
                        buff[(i * 3) + 1] = (int) p.y;
                        buff[(i * 3) + 2] = (int) p.z;
                    }
                    res.put(0, 0, buff);
                    return res;
                case 5:
                    Mat res2 = new Mat(count, 1, CvType.CV_32FC3);
                    float[] buff2 = new float[count * 3];
                    for (int i2 = 0; i2 < count; i2++) {
                        Point3 p2 = pts.get(i2);
                        buff2[i2 * 3] = (float) p2.x;
                        buff2[(i2 * 3) + 1] = (float) p2.y;
                        buff2[(i2 * 3) + 2] = (float) p2.z;
                    }
                    res2.put(0, 0, buff2);
                    return res2;
                case 6:
                    Mat res3 = new Mat(count, 1, CvType.CV_64FC3);
                    double[] buff3 = new double[count * 3];
                    for (int i3 = 0; i3 < count; i3++) {
                        Point3 p3 = pts.get(i3);
                        buff3[i3 * 3] = p3.x;
                        buff3[(i3 * 3) + 1] = p3.y;
                        buff3[(i3 * 3) + 2] = p3.z;
                    }
                    res3.put(0, 0, buff3);
                    return res3;
                default:
                    throw new IllegalArgumentException("'typeDepth' can be CV_32S, CV_32F or CV_64F");
            }
        }
        return new Mat();
    }

    public static void Mat_to_vector_Point2f(Mat m, List<Point> pts) {
        Mat_to_vector_Point(m, pts);
    }

    public static void Mat_to_vector_Point2d(Mat m, List<Point> pts) {
        Mat_to_vector_Point(m, pts);
    }

    public static void Mat_to_vector_Point(Mat m, List<Point> pts) {
        if (pts == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        int count = m.rows();
        int type = m.type();
        if (m.cols() != 1) {
            throw new IllegalArgumentException("Input Mat should have one column\n" + m);
        }
        pts.clear();
        if (type == CvType.CV_32SC2) {
            int[] buff = new int[count * 2];
            m.get(0, 0, buff);
            for (int i = 0; i < count; i++) {
                pts.add(new Point(buff[i * 2], buff[(i * 2) + 1]));
            }
            return;
        }
        if (type == CvType.CV_32FC2) {
            float[] buff2 = new float[count * 2];
            m.get(0, 0, buff2);
            for (int i2 = 0; i2 < count; i2++) {
                pts.add(new Point(buff2[i2 * 2], buff2[(i2 * 2) + 1]));
            }
            return;
        }
        if (type == CvType.CV_64FC2) {
            double[] buff3 = new double[count * 2];
            m.get(0, 0, buff3);
            for (int i3 = 0; i3 < count; i3++) {
                pts.add(new Point(buff3[i3 * 2], buff3[(i3 * 2) + 1]));
            }
            return;
        }
        throw new IllegalArgumentException("Input Mat should be of CV_32SC2, CV_32FC2 or CV_64FC2 type\n" + m);
    }

    public static void Mat_to_vector_Point3i(Mat m, List<Point3> pts) {
        Mat_to_vector_Point3(m, pts);
    }

    public static void Mat_to_vector_Point3f(Mat m, List<Point3> pts) {
        Mat_to_vector_Point3(m, pts);
    }

    public static void Mat_to_vector_Point3d(Mat m, List<Point3> pts) {
        Mat_to_vector_Point3(m, pts);
    }

    public static void Mat_to_vector_Point3(Mat m, List<Point3> pts) {
        if (pts == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        int count = m.rows();
        int type = m.type();
        if (m.cols() != 1) {
            throw new IllegalArgumentException("Input Mat should have one column\n" + m);
        }
        pts.clear();
        if (type == CvType.CV_32SC3) {
            int[] buff = new int[count * 3];
            m.get(0, 0, buff);
            for (int i = 0; i < count; i++) {
                pts.add(new Point3(buff[i * 3], buff[(i * 3) + 1], buff[(i * 3) + 2]));
            }
            return;
        }
        if (type == CvType.CV_32FC3) {
            float[] buff2 = new float[count * 3];
            m.get(0, 0, buff2);
            for (int i2 = 0; i2 < count; i2++) {
                pts.add(new Point3(buff2[i2 * 3], buff2[(i2 * 3) + 1], buff2[(i2 * 3) + 2]));
            }
            return;
        }
        if (type == CvType.CV_64FC3) {
            double[] buff3 = new double[count * 3];
            m.get(0, 0, buff3);
            for (int i3 = 0; i3 < count; i3++) {
                pts.add(new Point3(buff3[i3 * 3], buff3[(i3 * 3) + 1], buff3[(i3 * 3) + 2]));
            }
            return;
        }
        throw new IllegalArgumentException("Input Mat should be of CV_32SC3, CV_32FC3 or CV_64FC3 type\n" + m);
    }

    public static Mat vector_Mat_to_Mat(List<Mat> mats) {
        int count = mats != null ? mats.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_32SC2);
            int[] buff = new int[count * 2];
            for (int i = 0; i < count; i++) {
                long addr = mats.get(i).nativeObj;
                buff[i * 2] = (int) (addr >> 32);
                buff[(i * 2) + 1] = (int) ((-1) & addr);
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_Mat(Mat m, List<Mat> mats) {
        if (mats == null) {
            throw new IllegalArgumentException("mats == null");
        }
        int count = m.rows();
        if (CvType.CV_32SC2 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_32SC2 != m.type() ||  m.cols()!=1\n" + m);
        }
        mats.clear();
        int[] buff = new int[count * 2];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            long addr = (((long) buff[i * 2]) << 32) | (((long) buff[(i * 2) + 1]) & 4294967295L);
            mats.add(new Mat(addr));
        }
    }

    public static Mat vector_float_to_Mat(List<Float> fs) {
        int count = fs != null ? fs.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_32FC1);
            float[] buff = new float[count];
            for (int i = 0; i < count; i++) {
                float f = fs.get(i).floatValue();
                buff[i] = f;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_float(Mat m, List<Float> fs) {
        if (fs == null) {
            throw new IllegalArgumentException("fs == null");
        }
        int count = m.rows();
        if (CvType.CV_32FC1 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_32FC1 != m.type() ||  m.cols()!=1\n" + m);
        }
        fs.clear();
        float[] buff = new float[count];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            fs.add(Float.valueOf(buff[i]));
        }
    }

    public static Mat vector_uchar_to_Mat(List<Byte> bs) {
        int count = bs != null ? bs.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_8UC1);
            byte[] buff = new byte[count];
            for (int i = 0; i < count; i++) {
                byte b = bs.get(i).byteValue();
                buff[i] = b;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_uchar(Mat m, List<Byte> us) {
        if (us == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        int count = m.rows();
        if (CvType.CV_8UC1 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_8UC1 != m.type() ||  m.cols()!=1\n" + m);
        }
        us.clear();
        byte[] buff = new byte[count];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            us.add(Byte.valueOf(buff[i]));
        }
    }

    public static Mat vector_char_to_Mat(List<Byte> bs) {
        int count = bs != null ? bs.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_8SC1);
            byte[] buff = new byte[count];
            for (int i = 0; i < count; i++) {
                byte b = bs.get(i).byteValue();
                buff[i] = b;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static Mat vector_int_to_Mat(List<Integer> is) {
        int count = is != null ? is.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_32SC1);
            int[] buff = new int[count];
            for (int i = 0; i < count; i++) {
                int v = is.get(i).intValue();
                buff[i] = v;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_int(Mat m, List<Integer> is) {
        if (is == null) {
            throw new IllegalArgumentException("is == null");
        }
        int count = m.rows();
        if (CvType.CV_32SC1 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_32SC1 != m.type() ||  m.cols()!=1\n" + m);
        }
        is.clear();
        int[] buff = new int[count];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            is.add(Integer.valueOf(buff[i]));
        }
    }

    public static void Mat_to_vector_char(Mat m, List<Byte> bs) {
        if (bs == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        int count = m.rows();
        if (CvType.CV_8SC1 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_8SC1 != m.type() ||  m.cols()!=1\n" + m);
        }
        bs.clear();
        byte[] buff = new byte[count];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            bs.add(Byte.valueOf(buff[i]));
        }
    }

    public static Mat vector_Rect_to_Mat(List<Rect> rs) {
        int count = rs != null ? rs.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_32SC4);
            int[] buff = new int[count * 4];
            for (int i = 0; i < count; i++) {
                Rect r = rs.get(i);
                buff[i * 4] = r.x;
                buff[(i * 4) + 1] = r.y;
                buff[(i * 4) + 2] = r.width;
                buff[(i * 4) + 3] = r.height;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_Rect(Mat m, List<Rect> rs) {
        if (rs == null) {
            throw new IllegalArgumentException("rs == null");
        }
        int count = m.rows();
        if (CvType.CV_32SC4 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_32SC4 != m.type() ||  m.rows()!=1\n" + m);
        }
        rs.clear();
        int[] buff = new int[count * 4];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            rs.add(new Rect(buff[i * 4], buff[(i * 4) + 1], buff[(i * 4) + 2], buff[(i * 4) + 3]));
        }
    }

    public static Mat vector_Rect2d_to_Mat(List<Rect2d> rs) {
        int count = rs != null ? rs.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_64FC4);
            double[] buff = new double[count * 4];
            for (int i = 0; i < count; i++) {
                Rect2d r = rs.get(i);
                buff[i * 4] = r.x;
                buff[(i * 4) + 1] = r.y;
                buff[(i * 4) + 2] = r.width;
                buff[(i * 4) + 3] = r.height;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_Rect2d(Mat m, List<Rect2d> rs) {
        if (rs == null) {
            throw new IllegalArgumentException("rs == null");
        }
        int count = m.rows();
        if (CvType.CV_64FC4 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_64FC4 != m.type() ||  m.rows()!=1\n" + m);
        }
        rs.clear();
        double[] buff = new double[count * 4];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            rs.add(new Rect2d(buff[i * 4], buff[(i * 4) + 1], buff[(i * 4) + 2], buff[(i * 4) + 3]));
        }
    }

    public static Mat vector_KeyPoint_to_Mat(List<KeyPoint> kps) {
        int count = kps != null ? kps.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_64FC(7));
            double[] buff = new double[count * 7];
            for (int i = 0; i < count; i++) {
                KeyPoint kp = kps.get(i);
                buff[i * 7] = kp.pt.x;
                buff[(i * 7) + 1] = kp.pt.y;
                buff[(i * 7) + 2] = kp.size;
                buff[(i * 7) + 3] = kp.angle;
                buff[(i * 7) + 4] = kp.response;
                buff[(i * 7) + 5] = kp.octave;
                buff[(i * 7) + 6] = kp.class_id;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_KeyPoint(Mat m, List<KeyPoint> kps) {
        if (kps == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        int count = m.rows();
        if (CvType.CV_64FC(7) != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_64FC(7) != m.type() ||  m.cols()!=1\n" + m);
        }
        kps.clear();
        double[] buff = new double[count * 7];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            kps.add(new KeyPoint((float) buff[i * 7], (float) buff[(i * 7) + 1], (float) buff[(i * 7) + 2], (float) buff[(i * 7) + 3], (float) buff[(i * 7) + 4], (int) buff[(i * 7) + 5], (int) buff[(i * 7) + 6]));
        }
    }

    public static Mat vector_vector_Point_to_Mat(List<MatOfPoint> pts, List<Mat> mats) {
        int lCount = pts != null ? pts.size() : 0;
        if (lCount > 0) {
            for (MatOfPoint vpt : pts) {
                mats.add(vpt);
            }
            Mat res = vector_Mat_to_Mat(mats);
            return res;
        }
        Mat res2 = new Mat();
        return res2;
    }

    public static void Mat_to_vector_vector_Point(Mat m, List<MatOfPoint> pts) {
        if (pts == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        if (m == null) {
            throw new IllegalArgumentException("Input Mat can't be null");
        }
        List<Mat> mats = new ArrayList<>(m.rows());
        Mat_to_vector_Mat(m, mats);
        for (Mat mi : mats) {
            MatOfPoint pt = new MatOfPoint(mi);
            pts.add(pt);
            mi.release();
        }
        mats.clear();
    }

    public static void Mat_to_vector_vector_Point2f(Mat m, List<MatOfPoint2f> pts) {
        if (pts == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        if (m == null) {
            throw new IllegalArgumentException("Input Mat can't be null");
        }
        List<Mat> mats = new ArrayList<>(m.rows());
        Mat_to_vector_Mat(m, mats);
        for (Mat mi : mats) {
            MatOfPoint2f pt = new MatOfPoint2f(mi);
            pts.add(pt);
            mi.release();
        }
        mats.clear();
    }

    public static Mat vector_vector_Point2f_to_Mat(List<MatOfPoint2f> pts, List<Mat> mats) {
        int lCount = pts != null ? pts.size() : 0;
        if (lCount > 0) {
            for (MatOfPoint2f vpt : pts) {
                mats.add(vpt);
            }
            Mat res = vector_Mat_to_Mat(mats);
            return res;
        }
        Mat res2 = new Mat();
        return res2;
    }

    public static void Mat_to_vector_vector_Point3f(Mat m, List<MatOfPoint3f> pts) {
        if (pts == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        if (m == null) {
            throw new IllegalArgumentException("Input Mat can't be null");
        }
        List<Mat> mats = new ArrayList<>(m.rows());
        Mat_to_vector_Mat(m, mats);
        for (Mat mi : mats) {
            MatOfPoint3f pt = new MatOfPoint3f(mi);
            pts.add(pt);
            mi.release();
        }
        mats.clear();
    }

    public static Mat vector_vector_Point3f_to_Mat(List<MatOfPoint3f> pts, List<Mat> mats) {
        int lCount = pts != null ? pts.size() : 0;
        if (lCount > 0) {
            for (MatOfPoint3f vpt : pts) {
                mats.add(vpt);
            }
            Mat res = vector_Mat_to_Mat(mats);
            return res;
        }
        Mat res2 = new Mat();
        return res2;
    }

    public static Mat vector_vector_KeyPoint_to_Mat(List<MatOfKeyPoint> kps, List<Mat> mats) {
        int lCount = kps != null ? kps.size() : 0;
        if (lCount > 0) {
            for (MatOfKeyPoint vkp : kps) {
                mats.add(vkp);
            }
            Mat res = vector_Mat_to_Mat(mats);
            return res;
        }
        Mat res2 = new Mat();
        return res2;
    }

    public static void Mat_to_vector_vector_KeyPoint(Mat m, List<MatOfKeyPoint> kps) {
        if (kps == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        if (m == null) {
            throw new IllegalArgumentException("Input Mat can't be null");
        }
        List<Mat> mats = new ArrayList<>(m.rows());
        Mat_to_vector_Mat(m, mats);
        for (Mat mi : mats) {
            MatOfKeyPoint vkp = new MatOfKeyPoint(mi);
            kps.add(vkp);
            mi.release();
        }
        mats.clear();
    }

    public static Mat vector_double_to_Mat(List<Double> ds) {
        int count = ds != null ? ds.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_64FC1);
            double[] buff = new double[count];
            for (int i = 0; i < count; i++) {
                double v = ds.get(i).doubleValue();
                buff[i] = v;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_double(Mat m, List<Double> ds) {
        if (ds == null) {
            throw new IllegalArgumentException("ds == null");
        }
        int count = m.rows();
        if (CvType.CV_64FC1 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_64FC1 != m.type() ||  m.cols()!=1\n" + m);
        }
        ds.clear();
        double[] buff = new double[count];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            ds.add(Double.valueOf(buff[i]));
        }
    }

    public static Mat vector_DMatch_to_Mat(List<DMatch> matches) {
        int count = matches != null ? matches.size() : 0;
        if (count > 0) {
            Mat res = new Mat(count, 1, CvType.CV_64FC4);
            double[] buff = new double[count * 4];
            for (int i = 0; i < count; i++) {
                DMatch m = matches.get(i);
                buff[i * 4] = m.queryIdx;
                buff[(i * 4) + 1] = m.trainIdx;
                buff[(i * 4) + 2] = m.imgIdx;
                buff[(i * 4) + 3] = m.distance;
            }
            res.put(0, 0, buff);
            return res;
        }
        return new Mat();
    }

    public static void Mat_to_vector_DMatch(Mat m, List<DMatch> matches) {
        if (matches == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        int count = m.rows();
        if (CvType.CV_64FC4 != m.type() || m.cols() != 1) {
            throw new IllegalArgumentException("CvType.CV_64FC4 != m.type() ||  m.cols()!=1\n" + m);
        }
        matches.clear();
        double[] buff = new double[count * 4];
        m.get(0, 0, buff);
        for (int i = 0; i < count; i++) {
            matches.add(new DMatch((int) buff[i * 4], (int) buff[(i * 4) + 1], (int) buff[(i * 4) + 2], (float) buff[(i * 4) + 3]));
        }
    }

    public static Mat vector_vector_DMatch_to_Mat(List<MatOfDMatch> lvdm, List<Mat> mats) {
        int lCount = lvdm != null ? lvdm.size() : 0;
        if (lCount > 0) {
            for (MatOfDMatch vdm : lvdm) {
                mats.add(vdm);
            }
            Mat res = vector_Mat_to_Mat(mats);
            return res;
        }
        Mat res2 = new Mat();
        return res2;
    }

    public static void Mat_to_vector_vector_DMatch(Mat m, List<MatOfDMatch> lvdm) {
        if (lvdm == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        if (m == null) {
            throw new IllegalArgumentException("Input Mat can't be null");
        }
        List<Mat> mats = new ArrayList<>(m.rows());
        Mat_to_vector_Mat(m, mats);
        lvdm.clear();
        for (Mat mi : mats) {
            MatOfDMatch vdm = new MatOfDMatch(mi);
            lvdm.add(vdm);
            mi.release();
        }
        mats.clear();
    }

    public static Mat vector_vector_char_to_Mat(List<MatOfByte> lvb, List<Mat> mats) {
        int lCount = lvb != null ? lvb.size() : 0;
        if (lCount > 0) {
            for (MatOfByte vb : lvb) {
                mats.add(vb);
            }
            Mat res = vector_Mat_to_Mat(mats);
            return res;
        }
        Mat res2 = new Mat();
        return res2;
    }

    public static void Mat_to_vector_vector_char(Mat m, List<List<Byte>> llb) {
        if (llb == null) {
            throw new IllegalArgumentException("Output List can't be null");
        }
        if (m == null) {
            throw new IllegalArgumentException("Input Mat can't be null");
        }
        List<Mat> mats = new ArrayList<>(m.rows());
        Mat_to_vector_Mat(m, mats);
        for (Mat mi : mats) {
            List<Byte> lb = new ArrayList<>();
            Mat_to_vector_char(mi, lb);
            llb.add(lb);
            mi.release();
        }
        mats.clear();
    }
}
