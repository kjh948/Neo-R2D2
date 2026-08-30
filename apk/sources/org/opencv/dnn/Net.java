package org.opencv.dnn;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class Net {
    protected final long nativeObj;

    private static native long Net_0();

    private static native void connect_0(long j, String str, String str2);

    private static native void delete(long j);

    private static native void deleteLayer_0(long j, long j2);

    private static native boolean empty_0(long j);

    private static native void enableFusion_0(long j, boolean z);

    private static native long forward_0(long j, String str);

    private static native long forward_1(long j);

    private static native void forward_2(long j, long j2, String str);

    private static native void forward_3(long j, long j2);

    private static native void forward_4(long j, long j2, List<String> list);

    private static native long getFLOPS_0(long j, long j2);

    private static native long getFLOPS_1(long j, int i, long j2);

    private static native long getFLOPS_2(long j, int i, List<MatOfInt> list);

    private static native long getFLOPS_3(long j, List<MatOfInt> list);

    private static native int getLayerId_0(long j, String str);

    private static native List<Layer> getLayerInputs_0(long j, long j2);

    private static native List<String> getLayerNames_0(long j);

    private static native void getLayerShapes_0(long j, long j2, int i, List<MatOfInt> list, List<MatOfInt> list2);

    private static native void getLayerShapes_1(long j, List<MatOfInt> list, int i, List<MatOfInt> list2, List<MatOfInt> list3);

    private static native void getLayerTypes_0(long j, List<String> list);

    private static native long getLayer_0(long j, long j2);

    private static native int getLayersCount_0(long j, String str);

    private static native void getMemoryConsumption_0(long j, long j2, double[] dArr, double[] dArr2);

    private static native void getMemoryConsumption_1(long j, long j2, long j3, long j4, long j5);

    private static native void getMemoryConsumption_2(long j, int i, long j2, double[] dArr, double[] dArr2);

    private static native void getMemoryConsumption_3(long j, int i, List<MatOfInt> list, double[] dArr, double[] dArr2);

    private static native void getMemoryConsumption_4(long j, List<MatOfInt> list, double[] dArr, double[] dArr2);

    private static native void getMemoryConsumption_5(long j, List<MatOfInt> list, long j2, long j3, long j4);

    private static native long getParam_0(long j, long j2, int i);

    private static native long getParam_1(long j, long j2);

    private static native long getUnconnectedOutLayers_0(long j);

    private static native void setInput_0(long j, long j2, String str);

    private static native void setInput_1(long j, long j2);

    private static native void setInputsNames_0(long j, List<String> list);

    private static native void setParam_0(long j, long j2, int i, long j3);

    protected Net(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public Net() {
        this.nativeObj = Net_0();
    }

    public Mat forward(String outputName) {
        Mat retVal = new Mat(forward_0(this.nativeObj, outputName));
        return retVal;
    }

    public Mat forward() {
        Mat retVal = new Mat(forward_1(this.nativeObj));
        return retVal;
    }

    public Mat getParam(DictValue layer, int numParam) {
        Mat retVal = new Mat(getParam_0(this.nativeObj, layer.getNativeObjAddr(), numParam));
        return retVal;
    }

    public Mat getParam(DictValue layer) {
        Mat retVal = new Mat(getParam_1(this.nativeObj, layer.getNativeObjAddr()));
        return retVal;
    }

    public Layer getLayer(DictValue layerId) {
        Layer retVal = new Layer(getLayer_0(this.nativeObj, layerId.getNativeObjAddr()));
        return retVal;
    }

    public boolean empty() {
        boolean retVal = empty_0(this.nativeObj);
        return retVal;
    }

    public int getLayerId(String layer) {
        int retVal = getLayerId_0(this.nativeObj, layer);
        return retVal;
    }

    public int getLayersCount(String layerType) {
        int retVal = getLayersCount_0(this.nativeObj, layerType);
        return retVal;
    }

    public long getFLOPS(MatOfInt netInputShape) {
        long retVal = getFLOPS_0(this.nativeObj, netInputShape.nativeObj);
        return retVal;
    }

    public long getFLOPS(int layerId, MatOfInt netInputShape) {
        long retVal = getFLOPS_1(this.nativeObj, layerId, netInputShape.nativeObj);
        return retVal;
    }

    public long getFLOPS(int layerId, List<MatOfInt> netInputShapes) {
        long retVal = getFLOPS_2(this.nativeObj, layerId, netInputShapes);
        return retVal;
    }

    public long getFLOPS(List<MatOfInt> netInputShapes) {
        long retVal = getFLOPS_3(this.nativeObj, netInputShapes);
        return retVal;
    }

    public List<Layer> getLayerInputs(DictValue layerId) {
        List<Layer> retVal = getLayerInputs_0(this.nativeObj, layerId.getNativeObjAddr());
        return retVal;
    }

    public List<String> getLayerNames() {
        List<String> retVal = getLayerNames_0(this.nativeObj);
        return retVal;
    }

    public MatOfInt getUnconnectedOutLayers() {
        MatOfInt retVal = MatOfInt.fromNativeAddr(getUnconnectedOutLayers_0(this.nativeObj));
        return retVal;
    }

    public void connect(String outPin, String inpPin) {
        connect_0(this.nativeObj, outPin, inpPin);
    }

    public void deleteLayer(DictValue layer) {
        deleteLayer_0(this.nativeObj, layer.getNativeObjAddr());
    }

    public void enableFusion(boolean fusion) {
        enableFusion_0(this.nativeObj, fusion);
    }

    public void forward(List<Mat> outputBlobs, String outputName) {
        Mat outputBlobs_mat = Converters.vector_Mat_to_Mat(outputBlobs);
        forward_2(this.nativeObj, outputBlobs_mat.nativeObj, outputName);
    }

    public void forward(List<Mat> outputBlobs) {
        Mat outputBlobs_mat = Converters.vector_Mat_to_Mat(outputBlobs);
        forward_3(this.nativeObj, outputBlobs_mat.nativeObj);
    }

    public void forward(List<Mat> outputBlobs, List<String> outBlobNames) {
        Mat outputBlobs_mat = Converters.vector_Mat_to_Mat(outputBlobs);
        forward_4(this.nativeObj, outputBlobs_mat.nativeObj, outBlobNames);
    }

    public void getLayerShapes(MatOfInt netInputShape, int layerId, List<MatOfInt> inLayerShapes, List<MatOfInt> outLayerShapes) {
        getLayerShapes_0(this.nativeObj, netInputShape.nativeObj, layerId, inLayerShapes, outLayerShapes);
    }

    public void getLayerShapes(List<MatOfInt> netInputShapes, int layerId, List<MatOfInt> inLayerShapes, List<MatOfInt> outLayerShapes) {
        getLayerShapes_1(this.nativeObj, netInputShapes, layerId, inLayerShapes, outLayerShapes);
    }

    public void getLayerTypes(List<String> layersTypes) {
        getLayerTypes_0(this.nativeObj, layersTypes);
    }

    public void getMemoryConsumption(MatOfInt netInputShape, long[] weights, long[] blobs) {
        double[] weights_out = new double[1];
        double[] blobs_out = new double[1];
        getMemoryConsumption_0(this.nativeObj, netInputShape.nativeObj, weights_out, blobs_out);
        if (weights != null) {
            weights[0] = (long) weights_out[0];
        }
        if (blobs != null) {
            blobs[0] = (long) blobs_out[0];
        }
    }

    public void getMemoryConsumption(MatOfInt netInputShape, MatOfInt layerIds, MatOfDouble weights, MatOfDouble blobs) {
        getMemoryConsumption_1(this.nativeObj, netInputShape.nativeObj, layerIds.nativeObj, weights.nativeObj, blobs.nativeObj);
    }

    public void getMemoryConsumption(int layerId, MatOfInt netInputShape, long[] weights, long[] blobs) {
        double[] weights_out = new double[1];
        double[] blobs_out = new double[1];
        getMemoryConsumption_2(this.nativeObj, layerId, netInputShape.nativeObj, weights_out, blobs_out);
        if (weights != null) {
            weights[0] = (long) weights_out[0];
        }
        if (blobs != null) {
            blobs[0] = (long) blobs_out[0];
        }
    }

    public void getMemoryConsumption(int layerId, List<MatOfInt> netInputShapes, long[] weights, long[] blobs) {
        double[] weights_out = new double[1];
        double[] blobs_out = new double[1];
        getMemoryConsumption_3(this.nativeObj, layerId, netInputShapes, weights_out, blobs_out);
        if (weights != null) {
            weights[0] = (long) weights_out[0];
        }
        if (blobs != null) {
            blobs[0] = (long) blobs_out[0];
        }
    }

    public void getMemoryConsumption(List<MatOfInt> netInputShapes, long[] weights, long[] blobs) {
        double[] weights_out = new double[1];
        double[] blobs_out = new double[1];
        getMemoryConsumption_4(this.nativeObj, netInputShapes, weights_out, blobs_out);
        if (weights != null) {
            weights[0] = (long) weights_out[0];
        }
        if (blobs != null) {
            blobs[0] = (long) blobs_out[0];
        }
    }

    public void getMemoryConsumption(List<MatOfInt> netInputShapes, MatOfInt layerIds, MatOfDouble weights, MatOfDouble blobs) {
        getMemoryConsumption_5(this.nativeObj, netInputShapes, layerIds.nativeObj, weights.nativeObj, blobs.nativeObj);
    }

    public void setInput(Mat blob, String name) {
        setInput_0(this.nativeObj, blob.nativeObj, name);
    }

    public void setInput(Mat blob) {
        setInput_1(this.nativeObj, blob.nativeObj);
    }

    public void setInputsNames(List<String> inputBlobNames) {
        setInputsNames_0(this.nativeObj, inputBlobNames);
    }

    public void setParam(DictValue layer, int numParam, Mat blob) {
        setParam_0(this.nativeObj, layer.getNativeObjAddr(), numParam, blob.nativeObj);
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
