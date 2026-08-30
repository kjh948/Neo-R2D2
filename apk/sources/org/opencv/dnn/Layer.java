package org.opencv.dnn;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Algorithm;
import org.opencv.core.Mat;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class Layer extends Algorithm {
    private static native void delete(long j);

    private static native long finalize_0(long j, long j2);

    private static native void finalize_1(long j, long j2, long j3);

    private static native void forward_0(long j, long j2, long j3, long j4);

    private static native long get_blobs_0(long j);

    private static native String get_name_0(long j);

    private static native String get_type_0(long j);

    private static native void run_0(long j, long j2, long j3, long j4);

    private static native void set_blobs_0(long j, long j2);

    protected Layer(long addr) {
        super(addr);
    }

    public List<Mat> finalize(List<Mat> inputs) {
        Mat inputs_mat = Converters.vector_Mat_to_Mat(inputs);
        List<Mat> retVal = new ArrayList<>();
        Mat retValMat = new Mat(finalize_0(this.nativeObj, inputs_mat.nativeObj));
        Converters.Mat_to_vector_Mat(retValMat, retVal);
        return retVal;
    }

    public void finalize(List<Mat> inputs, List<Mat> outputs) {
        Mat inputs_mat = Converters.vector_Mat_to_Mat(inputs);
        Mat outputs_mat = new Mat();
        finalize_1(this.nativeObj, inputs_mat.nativeObj, outputs_mat.nativeObj);
        Converters.Mat_to_vector_Mat(outputs_mat, outputs);
        outputs_mat.release();
    }

    public void forward(List<Mat> inputs, List<Mat> outputs, List<Mat> internals) {
        Mat inputs_mat = Converters.vector_Mat_to_Mat(inputs);
        Mat outputs_mat = Converters.vector_Mat_to_Mat(outputs);
        Mat internals_mat = Converters.vector_Mat_to_Mat(internals);
        forward_0(this.nativeObj, inputs_mat.nativeObj, outputs_mat.nativeObj, internals_mat.nativeObj);
        Converters.Mat_to_vector_Mat(outputs_mat, outputs);
        outputs_mat.release();
        Converters.Mat_to_vector_Mat(internals_mat, internals);
        internals_mat.release();
    }

    public void run(List<Mat> inputs, List<Mat> outputs, List<Mat> internals) {
        Mat inputs_mat = Converters.vector_Mat_to_Mat(inputs);
        Mat outputs_mat = new Mat();
        Mat internals_mat = Converters.vector_Mat_to_Mat(internals);
        run_0(this.nativeObj, inputs_mat.nativeObj, outputs_mat.nativeObj, internals_mat.nativeObj);
        Converters.Mat_to_vector_Mat(outputs_mat, outputs);
        outputs_mat.release();
        Converters.Mat_to_vector_Mat(internals_mat, internals);
        internals_mat.release();
    }

    public List<Mat> get_blobs() {
        List<Mat> retVal = new ArrayList<>();
        Mat retValMat = new Mat(get_blobs_0(this.nativeObj));
        Converters.Mat_to_vector_Mat(retValMat, retVal);
        return retVal;
    }

    public void set_blobs(List<Mat> blobs) {
        Mat blobs_mat = Converters.vector_Mat_to_Mat(blobs);
        set_blobs_0(this.nativeObj, blobs_mat.nativeObj);
    }

    public String get_name() {
        String retVal = get_name_0(this.nativeObj);
        return retVal;
    }

    public String get_type() {
        String retVal = get_type_0(this.nativeObj);
        return retVal;
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
