package org.opencv.dnn;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class Dnn {
    public static final int DNN_BACKEND_DEFAULT = 0;
    public static final int DNN_BACKEND_HALIDE = 1;
    public static final int DNN_TARGET_CPU = 0;
    public static final int DNN_TARGET_OPENCL = 1;

    private static native long blobFromImage_0(long j, double d, double d2, double d3, double d4, double d5, double d6, double d7, boolean z);

    private static native long blobFromImage_1(long j);

    private static native long blobFromImages_0(long j, double d, double d2, double d3, double d4, double d5, double d6, double d7, boolean z);

    private static native long blobFromImages_1(long j);

    private static native long createCaffeImporter_0(String str, String str2);

    private static native long createCaffeImporter_1(String str);

    private static native long createTensorflowImporter_0(String str);

    private static native long createTorchImporter_0(String str, boolean z);

    private static native long createTorchImporter_1(String str);

    private static native long readNetFromCaffe_0(String str, String str2);

    private static native long readNetFromCaffe_1(String str);

    private static native long readNetFromTensorflow_0(String str);

    private static native long readNetFromTorch_0(String str, boolean z);

    private static native long readNetFromTorch_1(String str);

    private static native long readTorchBlob_0(String str, boolean z);

    private static native long readTorchBlob_1(String str);

    public static Mat blobFromImage(Mat image, double scalefactor, Size size, Scalar mean, boolean swapRB) {
        Mat retVal = new Mat(blobFromImage_0(image.nativeObj, scalefactor, size.width, size.height, mean.val[0], mean.val[1], mean.val[2], mean.val[3], swapRB));
        return retVal;
    }

    public static Mat blobFromImage(Mat image) {
        Mat retVal = new Mat(blobFromImage_1(image.nativeObj));
        return retVal;
    }

    public static Mat blobFromImages(List<Mat> images, double scalefactor, Size size, Scalar mean, boolean swapRB) {
        Mat images_mat = Converters.vector_Mat_to_Mat(images);
        Mat retVal = new Mat(blobFromImages_0(images_mat.nativeObj, scalefactor, size.width, size.height, mean.val[0], mean.val[1], mean.val[2], mean.val[3], swapRB));
        return retVal;
    }

    public static Mat blobFromImages(List<Mat> images) {
        Mat images_mat = Converters.vector_Mat_to_Mat(images);
        Mat retVal = new Mat(blobFromImages_1(images_mat.nativeObj));
        return retVal;
    }

    public static Mat readTorchBlob(String filename, boolean isBinary) {
        Mat retVal = new Mat(readTorchBlob_0(filename, isBinary));
        return retVal;
    }

    public static Mat readTorchBlob(String filename) {
        Mat retVal = new Mat(readTorchBlob_1(filename));
        return retVal;
    }

    public static Net readNetFromCaffe(String prototxt, String caffeModel) {
        Net retVal = new Net(readNetFromCaffe_0(prototxt, caffeModel));
        return retVal;
    }

    public static Net readNetFromCaffe(String prototxt) {
        Net retVal = new Net(readNetFromCaffe_1(prototxt));
        return retVal;
    }

    public static Net readNetFromTensorflow(String model) {
        Net retVal = new Net(readNetFromTensorflow_0(model));
        return retVal;
    }

    public static Net readNetFromTorch(String model, boolean isBinary) {
        Net retVal = new Net(readNetFromTorch_0(model, isBinary));
        return retVal;
    }

    public static Net readNetFromTorch(String model) {
        Net retVal = new Net(readNetFromTorch_1(model));
        return retVal;
    }

    public static Importer createCaffeImporter(String prototxt, String caffeModel) {
        Importer retVal = new Importer(createCaffeImporter_0(prototxt, caffeModel));
        return retVal;
    }

    public static Importer createCaffeImporter(String prototxt) {
        Importer retVal = new Importer(createCaffeImporter_1(prototxt));
        return retVal;
    }

    public static Importer createTensorflowImporter(String model) {
        Importer retVal = new Importer(createTensorflowImporter_0(model));
        return retVal;
    }

    public static Importer createTorchImporter(String filename, boolean isBinary) {
        Importer retVal = new Importer(createTorchImporter_0(filename, isBinary));
        return retVal;
    }

    public static Importer createTorchImporter(String filename) {
        Importer retVal = new Importer(createTorchImporter_1(filename));
        return retVal;
    }
}
