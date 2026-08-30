package org.opencv.photo;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Point;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class Photo {
    private static final int CV_INPAINT_NS = 0;
    private static final int CV_INPAINT_TELEA = 1;
    public static final int INPAINT_NS = 0;
    public static final int INPAINT_TELEA = 1;
    public static final int LDR_SIZE = 256;
    public static final int MIXED_CLONE = 2;
    public static final int MONOCHROME_TRANSFER = 3;
    public static final int NORMAL_CLONE = 1;
    public static final int NORMCONV_FILTER = 2;
    public static final int RECURS_FILTER = 1;

    private static native void colorChange_0(long j, long j2, long j3, float f, float f2, float f3);

    private static native void colorChange_1(long j, long j2, long j3);

    private static native long createAlignMTB_0(int i, int i2, boolean z);

    private static native long createAlignMTB_1();

    private static native long createCalibrateDebevec_0(int i, float f, boolean z);

    private static native long createCalibrateDebevec_1();

    private static native long createCalibrateRobertson_0(int i, float f);

    private static native long createCalibrateRobertson_1();

    private static native long createMergeDebevec_0();

    private static native long createMergeMertens_0(float f, float f2, float f3);

    private static native long createMergeMertens_1();

    private static native long createMergeRobertson_0();

    private static native long createTonemapDrago_0(float f, float f2, float f3);

    private static native long createTonemapDrago_1();

    private static native long createTonemapDurand_0(float f, float f2, float f3, float f4, float f5);

    private static native long createTonemapDurand_1();

    private static native long createTonemapMantiuk_0(float f, float f2, float f3);

    private static native long createTonemapMantiuk_1();

    private static native long createTonemapReinhard_0(float f, float f2, float f3, float f4);

    private static native long createTonemapReinhard_1();

    private static native long createTonemap_0(float f);

    private static native long createTonemap_1();

    private static native void decolor_0(long j, long j2, long j3);

    private static native void denoise_TVL1_0(long j, long j2, double d, int i);

    private static native void denoise_TVL1_1(long j, long j2);

    private static native void detailEnhance_0(long j, long j2, float f, float f2);

    private static native void detailEnhance_1(long j, long j2);

    private static native void edgePreservingFilter_0(long j, long j2, int i, float f, float f2);

    private static native void edgePreservingFilter_1(long j, long j2);

    private static native void fastNlMeansDenoisingColoredMulti_0(long j, long j2, int i, int i2, float f, float f2, int i3, int i4);

    private static native void fastNlMeansDenoisingColoredMulti_1(long j, long j2, int i, int i2);

    private static native void fastNlMeansDenoisingColored_0(long j, long j2, float f, float f2, int i, int i2);

    private static native void fastNlMeansDenoisingColored_1(long j, long j2);

    private static native void fastNlMeansDenoisingMulti_0(long j, long j2, int i, int i2, float f, int i3, int i4);

    private static native void fastNlMeansDenoisingMulti_1(long j, long j2, int i, int i2);

    private static native void fastNlMeansDenoisingMulti_2(long j, long j2, int i, int i2, long j3, int i3, int i4, int i5);

    private static native void fastNlMeansDenoisingMulti_3(long j, long j2, int i, int i2, long j3);

    private static native void fastNlMeansDenoising_0(long j, long j2, float f, int i, int i2);

    private static native void fastNlMeansDenoising_1(long j, long j2);

    private static native void fastNlMeansDenoising_2(long j, long j2, long j3, int i, int i2, int i3);

    private static native void fastNlMeansDenoising_3(long j, long j2, long j3);

    private static native void illuminationChange_0(long j, long j2, long j3, float f, float f2);

    private static native void illuminationChange_1(long j, long j2, long j3);

    private static native void inpaint_0(long j, long j2, long j3, double d, int i);

    private static native void pencilSketch_0(long j, long j2, long j3, float f, float f2, float f3);

    private static native void pencilSketch_1(long j, long j2, long j3);

    private static native void seamlessClone_0(long j, long j2, long j3, double d, double d2, long j4, int i);

    private static native void stylization_0(long j, long j2, float f, float f2);

    private static native void stylization_1(long j, long j2);

    private static native void textureFlattening_0(long j, long j2, long j3, float f, float f2, int i);

    private static native void textureFlattening_1(long j, long j2, long j3);

    public static AlignMTB createAlignMTB(int max_bits, int exclude_range, boolean cut) {
        AlignMTB retVal = new AlignMTB(createAlignMTB_0(max_bits, exclude_range, cut));
        return retVal;
    }

    public static AlignMTB createAlignMTB() {
        AlignMTB retVal = new AlignMTB(createAlignMTB_1());
        return retVal;
    }

    public static CalibrateDebevec createCalibrateDebevec(int samples, float lambda, boolean random) {
        CalibrateDebevec retVal = new CalibrateDebevec(createCalibrateDebevec_0(samples, lambda, random));
        return retVal;
    }

    public static CalibrateDebevec createCalibrateDebevec() {
        CalibrateDebevec retVal = new CalibrateDebevec(createCalibrateDebevec_1());
        return retVal;
    }

    public static CalibrateRobertson createCalibrateRobertson(int max_iter, float threshold) {
        CalibrateRobertson retVal = new CalibrateRobertson(createCalibrateRobertson_0(max_iter, threshold));
        return retVal;
    }

    public static CalibrateRobertson createCalibrateRobertson() {
        CalibrateRobertson retVal = new CalibrateRobertson(createCalibrateRobertson_1());
        return retVal;
    }

    public static MergeDebevec createMergeDebevec() {
        MergeDebevec retVal = new MergeDebevec(createMergeDebevec_0());
        return retVal;
    }

    public static MergeMertens createMergeMertens(float contrast_weight, float saturation_weight, float exposure_weight) {
        MergeMertens retVal = new MergeMertens(createMergeMertens_0(contrast_weight, saturation_weight, exposure_weight));
        return retVal;
    }

    public static MergeMertens createMergeMertens() {
        MergeMertens retVal = new MergeMertens(createMergeMertens_1());
        return retVal;
    }

    public static MergeRobertson createMergeRobertson() {
        MergeRobertson retVal = new MergeRobertson(createMergeRobertson_0());
        return retVal;
    }

    public static Tonemap createTonemap(float gamma) {
        Tonemap retVal = new Tonemap(createTonemap_0(gamma));
        return retVal;
    }

    public static Tonemap createTonemap() {
        Tonemap retVal = new Tonemap(createTonemap_1());
        return retVal;
    }

    public static TonemapDrago createTonemapDrago(float gamma, float saturation, float bias) {
        TonemapDrago retVal = new TonemapDrago(createTonemapDrago_0(gamma, saturation, bias));
        return retVal;
    }

    public static TonemapDrago createTonemapDrago() {
        TonemapDrago retVal = new TonemapDrago(createTonemapDrago_1());
        return retVal;
    }

    public static TonemapDurand createTonemapDurand(float gamma, float contrast, float saturation, float sigma_space, float sigma_color) {
        TonemapDurand retVal = new TonemapDurand(createTonemapDurand_0(gamma, contrast, saturation, sigma_space, sigma_color));
        return retVal;
    }

    public static TonemapDurand createTonemapDurand() {
        TonemapDurand retVal = new TonemapDurand(createTonemapDurand_1());
        return retVal;
    }

    public static TonemapMantiuk createTonemapMantiuk(float gamma, float scale, float saturation) {
        TonemapMantiuk retVal = new TonemapMantiuk(createTonemapMantiuk_0(gamma, scale, saturation));
        return retVal;
    }

    public static TonemapMantiuk createTonemapMantiuk() {
        TonemapMantiuk retVal = new TonemapMantiuk(createTonemapMantiuk_1());
        return retVal;
    }

    public static TonemapReinhard createTonemapReinhard(float gamma, float intensity, float light_adapt, float color_adapt) {
        TonemapReinhard retVal = new TonemapReinhard(createTonemapReinhard_0(gamma, intensity, light_adapt, color_adapt));
        return retVal;
    }

    public static TonemapReinhard createTonemapReinhard() {
        TonemapReinhard retVal = new TonemapReinhard(createTonemapReinhard_1());
        return retVal;
    }

    public static void colorChange(Mat src, Mat mask, Mat dst, float red_mul, float green_mul, float blue_mul) {
        colorChange_0(src.nativeObj, mask.nativeObj, dst.nativeObj, red_mul, green_mul, blue_mul);
    }

    public static void colorChange(Mat src, Mat mask, Mat dst) {
        colorChange_1(src.nativeObj, mask.nativeObj, dst.nativeObj);
    }

    public static void decolor(Mat src, Mat grayscale, Mat color_boost) {
        decolor_0(src.nativeObj, grayscale.nativeObj, color_boost.nativeObj);
    }

    public static void denoise_TVL1(List<Mat> observations, Mat result, double lambda, int niters) {
        Mat observations_mat = Converters.vector_Mat_to_Mat(observations);
        denoise_TVL1_0(observations_mat.nativeObj, result.nativeObj, lambda, niters);
    }

    public static void denoise_TVL1(List<Mat> observations, Mat result) {
        Mat observations_mat = Converters.vector_Mat_to_Mat(observations);
        denoise_TVL1_1(observations_mat.nativeObj, result.nativeObj);
    }

    public static void detailEnhance(Mat src, Mat dst, float sigma_s, float sigma_r) {
        detailEnhance_0(src.nativeObj, dst.nativeObj, sigma_s, sigma_r);
    }

    public static void detailEnhance(Mat src, Mat dst) {
        detailEnhance_1(src.nativeObj, dst.nativeObj);
    }

    public static void edgePreservingFilter(Mat src, Mat dst, int flags, float sigma_s, float sigma_r) {
        edgePreservingFilter_0(src.nativeObj, dst.nativeObj, flags, sigma_s, sigma_r);
    }

    public static void edgePreservingFilter(Mat src, Mat dst) {
        edgePreservingFilter_1(src.nativeObj, dst.nativeObj);
    }

    public static void fastNlMeansDenoising(Mat src, Mat dst, float h, int templateWindowSize, int searchWindowSize) {
        fastNlMeansDenoising_0(src.nativeObj, dst.nativeObj, h, templateWindowSize, searchWindowSize);
    }

    public static void fastNlMeansDenoising(Mat src, Mat dst) {
        fastNlMeansDenoising_1(src.nativeObj, dst.nativeObj);
    }

    public static void fastNlMeansDenoising(Mat src, Mat dst, MatOfFloat h, int templateWindowSize, int searchWindowSize, int normType) {
        fastNlMeansDenoising_2(src.nativeObj, dst.nativeObj, h.nativeObj, templateWindowSize, searchWindowSize, normType);
    }

    public static void fastNlMeansDenoising(Mat src, Mat dst, MatOfFloat h) {
        fastNlMeansDenoising_3(src.nativeObj, dst.nativeObj, h.nativeObj);
    }

    public static void fastNlMeansDenoisingColored(Mat src, Mat dst, float h, float hColor, int templateWindowSize, int searchWindowSize) {
        fastNlMeansDenoisingColored_0(src.nativeObj, dst.nativeObj, h, hColor, templateWindowSize, searchWindowSize);
    }

    public static void fastNlMeansDenoisingColored(Mat src, Mat dst) {
        fastNlMeansDenoisingColored_1(src.nativeObj, dst.nativeObj);
    }

    public static void fastNlMeansDenoisingColoredMulti(List<Mat> srcImgs, Mat dst, int imgToDenoiseIndex, int temporalWindowSize, float h, float hColor, int templateWindowSize, int searchWindowSize) {
        Mat srcImgs_mat = Converters.vector_Mat_to_Mat(srcImgs);
        fastNlMeansDenoisingColoredMulti_0(srcImgs_mat.nativeObj, dst.nativeObj, imgToDenoiseIndex, temporalWindowSize, h, hColor, templateWindowSize, searchWindowSize);
    }

    public static void fastNlMeansDenoisingColoredMulti(List<Mat> srcImgs, Mat dst, int imgToDenoiseIndex, int temporalWindowSize) {
        Mat srcImgs_mat = Converters.vector_Mat_to_Mat(srcImgs);
        fastNlMeansDenoisingColoredMulti_1(srcImgs_mat.nativeObj, dst.nativeObj, imgToDenoiseIndex, temporalWindowSize);
    }

    public static void fastNlMeansDenoisingMulti(List<Mat> srcImgs, Mat dst, int imgToDenoiseIndex, int temporalWindowSize, float h, int templateWindowSize, int searchWindowSize) {
        Mat srcImgs_mat = Converters.vector_Mat_to_Mat(srcImgs);
        fastNlMeansDenoisingMulti_0(srcImgs_mat.nativeObj, dst.nativeObj, imgToDenoiseIndex, temporalWindowSize, h, templateWindowSize, searchWindowSize);
    }

    public static void fastNlMeansDenoisingMulti(List<Mat> srcImgs, Mat dst, int imgToDenoiseIndex, int temporalWindowSize) {
        Mat srcImgs_mat = Converters.vector_Mat_to_Mat(srcImgs);
        fastNlMeansDenoisingMulti_1(srcImgs_mat.nativeObj, dst.nativeObj, imgToDenoiseIndex, temporalWindowSize);
    }

    public static void fastNlMeansDenoisingMulti(List<Mat> srcImgs, Mat dst, int imgToDenoiseIndex, int temporalWindowSize, MatOfFloat h, int templateWindowSize, int searchWindowSize, int normType) {
        Mat srcImgs_mat = Converters.vector_Mat_to_Mat(srcImgs);
        fastNlMeansDenoisingMulti_2(srcImgs_mat.nativeObj, dst.nativeObj, imgToDenoiseIndex, temporalWindowSize, h.nativeObj, templateWindowSize, searchWindowSize, normType);
    }

    public static void fastNlMeansDenoisingMulti(List<Mat> srcImgs, Mat dst, int imgToDenoiseIndex, int temporalWindowSize, MatOfFloat h) {
        Mat srcImgs_mat = Converters.vector_Mat_to_Mat(srcImgs);
        fastNlMeansDenoisingMulti_3(srcImgs_mat.nativeObj, dst.nativeObj, imgToDenoiseIndex, temporalWindowSize, h.nativeObj);
    }

    public static void illuminationChange(Mat src, Mat mask, Mat dst, float alpha, float beta) {
        illuminationChange_0(src.nativeObj, mask.nativeObj, dst.nativeObj, alpha, beta);
    }

    public static void illuminationChange(Mat src, Mat mask, Mat dst) {
        illuminationChange_1(src.nativeObj, mask.nativeObj, dst.nativeObj);
    }

    public static void inpaint(Mat src, Mat inpaintMask, Mat dst, double inpaintRadius, int flags) {
        inpaint_0(src.nativeObj, inpaintMask.nativeObj, dst.nativeObj, inpaintRadius, flags);
    }

    public static void pencilSketch(Mat src, Mat dst1, Mat dst2, float sigma_s, float sigma_r, float shade_factor) {
        pencilSketch_0(src.nativeObj, dst1.nativeObj, dst2.nativeObj, sigma_s, sigma_r, shade_factor);
    }

    public static void pencilSketch(Mat src, Mat dst1, Mat dst2) {
        pencilSketch_1(src.nativeObj, dst1.nativeObj, dst2.nativeObj);
    }

    public static void seamlessClone(Mat src, Mat dst, Mat mask, Point p, Mat blend, int flags) {
        seamlessClone_0(src.nativeObj, dst.nativeObj, mask.nativeObj, p.x, p.y, blend.nativeObj, flags);
    }

    public static void stylization(Mat src, Mat dst, float sigma_s, float sigma_r) {
        stylization_0(src.nativeObj, dst.nativeObj, sigma_s, sigma_r);
    }

    public static void stylization(Mat src, Mat dst) {
        stylization_1(src.nativeObj, dst.nativeObj);
    }

    public static void textureFlattening(Mat src, Mat mask, Mat dst, float low_threshold, float high_threshold, int kernel_size) {
        textureFlattening_0(src.nativeObj, mask.nativeObj, dst.nativeObj, low_threshold, high_threshold, kernel_size);
    }

    public static void textureFlattening(Mat src, Mat mask, Mat dst) {
        textureFlattening_1(src.nativeObj, mask.nativeObj, dst.nativeObj);
    }
}
