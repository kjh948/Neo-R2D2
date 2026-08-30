package org.opencv.features2d;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Algorithm;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class DescriptorMatcher extends Algorithm {
    public static final int BRUTEFORCE = 2;
    public static final int BRUTEFORCE_HAMMING = 4;
    public static final int BRUTEFORCE_HAMMINGLUT = 5;
    public static final int BRUTEFORCE_L1 = 3;
    public static final int BRUTEFORCE_SL2 = 6;
    public static final int FLANNBASED = 1;

    private static native void add_0(long j, long j2);

    private static native void clear_0(long j);

    private static native long clone_0(long j, boolean z);

    private static native long clone_1(long j);

    private static native long create_0(String str);

    private static native long create_1(int i);

    private static native void delete(long j);

    private static native boolean empty_0(long j);

    private static native long getTrainDescriptors_0(long j);

    private static native boolean isMaskSupported_0(long j);

    private static native void knnMatch_0(long j, long j2, long j3, long j4, int i, long j5, boolean z);

    private static native void knnMatch_1(long j, long j2, long j3, long j4, int i);

    private static native void knnMatch_2(long j, long j2, long j3, int i, long j4, boolean z);

    private static native void knnMatch_3(long j, long j2, long j3, int i);

    private static native void match_0(long j, long j2, long j3, long j4, long j5);

    private static native void match_1(long j, long j2, long j3, long j4);

    private static native void match_2(long j, long j2, long j3, long j4);

    private static native void match_3(long j, long j2, long j3);

    private static native void radiusMatch_0(long j, long j2, long j3, long j4, float f, long j5, boolean z);

    private static native void radiusMatch_1(long j, long j2, long j3, long j4, float f);

    private static native void radiusMatch_2(long j, long j2, long j3, float f, long j4, boolean z);

    private static native void radiusMatch_3(long j, long j2, long j3, float f);

    private static native void read_0(long j, String str);

    private static native void train_0(long j);

    private static native void write_0(long j, String str);

    protected DescriptorMatcher(long addr) {
        super(addr);
    }

    public DescriptorMatcher clone(boolean emptyTrainData) {
        DescriptorMatcher retVal = new DescriptorMatcher(clone_0(this.nativeObj, emptyTrainData));
        return retVal;
    }

    public DescriptorMatcher clone() {
        DescriptorMatcher retVal = new DescriptorMatcher(clone_1(this.nativeObj));
        return retVal;
    }

    public static DescriptorMatcher create(String descriptorMatcherType) {
        DescriptorMatcher retVal = new DescriptorMatcher(create_0(descriptorMatcherType));
        return retVal;
    }

    public static DescriptorMatcher create(int matcherType) {
        DescriptorMatcher retVal = new DescriptorMatcher(create_1(matcherType));
        return retVal;
    }

    public boolean empty() {
        boolean retVal = empty_0(this.nativeObj);
        return retVal;
    }

    public boolean isMaskSupported() {
        boolean retVal = isMaskSupported_0(this.nativeObj);
        return retVal;
    }

    public List<Mat> getTrainDescriptors() {
        List<Mat> retVal = new ArrayList<>();
        Mat retValMat = new Mat(getTrainDescriptors_0(this.nativeObj));
        Converters.Mat_to_vector_Mat(retValMat, retVal);
        return retVal;
    }

    public void add(List<Mat> descriptors) {
        Mat descriptors_mat = Converters.vector_Mat_to_Mat(descriptors);
        add_0(this.nativeObj, descriptors_mat.nativeObj);
    }

    @Override // org.opencv.core.Algorithm
    public void clear() {
        clear_0(this.nativeObj);
    }

    public void knnMatch(Mat queryDescriptors, Mat trainDescriptors, List<MatOfDMatch> matches, int k, Mat mask, boolean compactResult) {
        Mat matches_mat = new Mat();
        knnMatch_0(this.nativeObj, queryDescriptors.nativeObj, trainDescriptors.nativeObj, matches_mat.nativeObj, k, mask.nativeObj, compactResult);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void knnMatch(Mat queryDescriptors, Mat trainDescriptors, List<MatOfDMatch> matches, int k) {
        Mat matches_mat = new Mat();
        knnMatch_1(this.nativeObj, queryDescriptors.nativeObj, trainDescriptors.nativeObj, matches_mat.nativeObj, k);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void knnMatch(Mat queryDescriptors, List<MatOfDMatch> matches, int k, List<Mat> masks, boolean compactResult) {
        Mat matches_mat = new Mat();
        Mat masks_mat = Converters.vector_Mat_to_Mat(masks);
        knnMatch_2(this.nativeObj, queryDescriptors.nativeObj, matches_mat.nativeObj, k, masks_mat.nativeObj, compactResult);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void knnMatch(Mat queryDescriptors, List<MatOfDMatch> matches, int k) {
        Mat matches_mat = new Mat();
        knnMatch_3(this.nativeObj, queryDescriptors.nativeObj, matches_mat.nativeObj, k);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void match(Mat queryDescriptors, Mat trainDescriptors, MatOfDMatch matches, Mat mask) {
        match_0(this.nativeObj, queryDescriptors.nativeObj, trainDescriptors.nativeObj, matches.nativeObj, mask.nativeObj);
    }

    public void match(Mat queryDescriptors, Mat trainDescriptors, MatOfDMatch matches) {
        match_1(this.nativeObj, queryDescriptors.nativeObj, trainDescriptors.nativeObj, matches.nativeObj);
    }

    public void match(Mat queryDescriptors, MatOfDMatch matches, List<Mat> masks) {
        Mat masks_mat = Converters.vector_Mat_to_Mat(masks);
        match_2(this.nativeObj, queryDescriptors.nativeObj, matches.nativeObj, masks_mat.nativeObj);
    }

    public void match(Mat queryDescriptors, MatOfDMatch matches) {
        match_3(this.nativeObj, queryDescriptors.nativeObj, matches.nativeObj);
    }

    public void radiusMatch(Mat queryDescriptors, Mat trainDescriptors, List<MatOfDMatch> matches, float maxDistance, Mat mask, boolean compactResult) {
        Mat matches_mat = new Mat();
        radiusMatch_0(this.nativeObj, queryDescriptors.nativeObj, trainDescriptors.nativeObj, matches_mat.nativeObj, maxDistance, mask.nativeObj, compactResult);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void radiusMatch(Mat queryDescriptors, Mat trainDescriptors, List<MatOfDMatch> matches, float maxDistance) {
        Mat matches_mat = new Mat();
        radiusMatch_1(this.nativeObj, queryDescriptors.nativeObj, trainDescriptors.nativeObj, matches_mat.nativeObj, maxDistance);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void radiusMatch(Mat queryDescriptors, List<MatOfDMatch> matches, float maxDistance, List<Mat> masks, boolean compactResult) {
        Mat matches_mat = new Mat();
        Mat masks_mat = Converters.vector_Mat_to_Mat(masks);
        radiusMatch_2(this.nativeObj, queryDescriptors.nativeObj, matches_mat.nativeObj, maxDistance, masks_mat.nativeObj, compactResult);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void radiusMatch(Mat queryDescriptors, List<MatOfDMatch> matches, float maxDistance) {
        Mat matches_mat = new Mat();
        radiusMatch_3(this.nativeObj, queryDescriptors.nativeObj, matches_mat.nativeObj, maxDistance);
        Converters.Mat_to_vector_vector_DMatch(matches_mat, matches);
        matches_mat.release();
    }

    public void read(String fileName) {
        read_0(this.nativeObj, fileName);
    }

    public void train() {
        train_0(this.nativeObj);
    }

    public void write(String fileName) {
        write_0(this.nativeObj, fileName);
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
