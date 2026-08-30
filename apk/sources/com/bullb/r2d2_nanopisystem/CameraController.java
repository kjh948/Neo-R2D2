package com.bullb.r2d2_nanopisystem;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.FaceDetection.FaceDetection;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import java.util.Timer;
import java.util.TimerTask;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;

/* JADX INFO: loaded from: classes.dex */
public class CameraController {
    private static final int MAX_RETRY = 20;
    private static final String TAG = "CameraController";
    private Context context;
    private FaceDetection faceDetection;
    private JavaCameraView javaCameraView;
    BaseLoaderCallback mLoaderCallBack;
    private QRCodeReader qrCodeReader;
    private Timer restartCameraTimer;
    private RestartCameraTask restartDetectionTask;
    private VideoStreamer videoStreamer;
    private int retryCount = 20;
    private boolean needOpenStreamer = false;
    private boolean needOpenReader = false;
    private boolean shouldStart = true;
    private boolean isBusy = false;
    private boolean isCameraEnabled = false;
    private final Object cameraLock = new Object();

    public CameraController(Context context, JavaCameraView javaCameraView) {
        this.mLoaderCallBack = new BaseLoaderCallback(this.context) { // from class: com.bullb.r2d2_nanopisystem.CameraController.3
            @Override // org.opencv.android.BaseLoaderCallback, org.opencv.android.LoaderCallbackInterface
            public void onManagerConnected(int status) {
                switch (status) {
                    case 0:
                        CameraController.this.faceDetection.initFaceDetectionLibrary();
                        CameraController.this.enableJavaCameraView();
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
                super.onManagerConnected(status);
            }
        };
        this.javaCameraView = javaCameraView;
        this.context = context;
        this.faceDetection = new FaceDetection(context);
        this.videoStreamer = new VideoStreamer(context);
        this.qrCodeReader = new QRCodeReader(context);
    }

    public void startCamera() {
        Log.d(TAG, "start camera");
        this.shouldStart = true;
        ((Activity) this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.CameraController.1
            @Override // java.lang.Runnable
            public void run() {
                CameraController.this.javaCameraView.setVisibility(0);
            }
        });
        if (this.javaCameraView == null) {
            Log.d(TAG, "start camera with javacameraview is null");
            return;
        }
        this.retryCount = 20;
        this.restartCameraTimer = new Timer();
        this.javaCameraView.setHasFrame(false);
        restart();
    }

    public void restart() {
        this.retryCount--;
        if (this.retryCount > 0) {
            this.isBusy = true;
            Log.d(TAG, "retry:" + String.valueOf(this.retryCount));
            this.restartDetectionTask = new RestartCameraTask();
            this.restartCameraTimer.schedule(this.restartDetectionTask, 1000L, 1000L);
            ((Activity) this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.CameraController.2
                @Override // java.lang.Runnable
                public void run() {
                    CameraController.this.javaCameraView.setVisibility(0);
                }
            });
            this.javaCameraView.setCvCameraViewListener(this.faceDetection.getCVCamerViewListener());
            this.javaCameraView.setVideoFrameCallback(this.videoStreamer.getVideoFrameListener());
            this.javaCameraView.setQRCodeFrameCallback(this.qrCodeReader.getQRCodeFrameListener());
            if (!this.isCameraEnabled) {
                if (OpenCVLoader.initDebug()) {
                    Log.d(TAG, "Opencv successfully loaded.");
                    this.mLoaderCallBack.onManagerConnected(0);
                    return;
                } else {
                    Log.d(TAG, "Opencv not loaded");
                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this.context, this.mLoaderCallBack);
                    return;
                }
            }
            return;
        }
        this.isBusy = false;
    }

    public boolean startFaceDetection() {
        this.shouldStart = true;
        Log.d(TAG, "busy:" + String.valueOf(this.isBusy));
        if (!this.javaCameraView.hasFrame() && !this.isBusy) {
            startCamera();
            return true;
        }
        if (this.faceDetection == null) {
            Log.d(TAG, "start face detection with null");
            return false;
        }
        Log.d(TAG, "start face detection");
        this.shouldStart = true;
        this.javaCameraView.enableFaceDetection(true);
        return this.faceDetection.enable();
    }

    public void stopFaceDetection() {
        this.shouldStart = false;
        if (this.faceDetection == null) {
            Log.d(TAG, "stop face detection with null");
        }
        Log.d(TAG, "stop face detection");
        this.javaCameraView.enableFaceDetection(false);
        this.faceDetection.disable();
        checkAllDisabledAndCloseCamera();
    }

    public void startStreaming() {
        if (!this.javaCameraView.hasFrame()) {
            this.needOpenStreamer = true;
            startCamera();
        }
        if (this.videoStreamer == null) {
            Log.d(TAG, "start video streamer with null");
        }
        this.javaCameraView.enableVideoStreaming(true);
        this.videoStreamer.enable();
        if (isWorking()) {
            Log.d(TAG, "video streamer working");
        } else {
            Log.d(TAG, "video streamer not working");
        }
    }

    public void stopStreaming() {
        if (this.videoStreamer == null) {
            Log.d(TAG, "stop video streamer with null");
        }
        this.javaCameraView.enableVideoStreaming(false);
        this.videoStreamer.disable();
        checkAllDisabledAndCloseCamera();
    }

    public void startQRCodeScanning() {
        if (!this.javaCameraView.hasFrame()) {
            this.needOpenReader = true;
            startCamera();
        }
        if (this.qrCodeReader == null) {
            Log.d(TAG, "start qrcode reader with null");
        }
        this.javaCameraView.enableQRCodeReading(true);
        this.qrCodeReader.enable();
        if (isWorking()) {
            Log.d(TAG, "qrcode reader working");
        } else {
            Log.d(TAG, "qrcode reader not working");
        }
    }

    public void stopQRCodeScanning() {
        if (this.qrCodeReader == null) {
            Log.d(TAG, "stop qrcode reader with null");
        }
        this.javaCameraView.enableQRCodeReading(false);
        this.qrCodeReader.disable();
        checkAllDisabledAndCloseCamera();
    }

    public void checkAllDisabledAndCloseCamera() {
        if (!this.faceDetection.isEnabled() && !this.videoStreamer.isEnabled() && !this.qrCodeReader.isEnabled() && this.javaCameraView != null) {
            disableJavaCameraView();
        }
    }

    public boolean isWorking() {
        return this.javaCameraView.hasFrame();
    }

    public void stopCamera() {
        stopFaceDetection();
        stopStreaming();
        if (this.javaCameraView != null) {
            disableJavaCameraView();
        }
        ((Activity) this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.CameraController.4
            @Override // java.lang.Runnable
            public void run() {
                CameraController.this.javaCameraView.setVisibility(4);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enableJavaCameraView() {
        synchronized (this.cameraLock) {
            Log.d(TAG, "enable");
            this.javaCameraView.enableView();
            this.isCameraEnabled = true;
        }
    }

    private void disableJavaCameraView() {
        synchronized (this.cameraLock) {
            Log.d(TAG, "disable");
            this.javaCameraView.setHasFrame(false);
            this.javaCameraView.disableView();
            this.isCameraEnabled = false;
        }
    }

    public FaceDetection getFaceDetection() {
        return this.faceDetection;
    }

    private class RestartCameraTask extends TimerTask {
        int retryInternal;

        private RestartCameraTask() {
            this.retryInternal = 4;
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            ((Activity) CameraController.this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.CameraController.RestartCameraTask.1
                @Override // java.lang.Runnable
                public void run() {
                    RestartCameraTask restartCameraTask = RestartCameraTask.this;
                    restartCameraTask.retryInternal--;
                    Log.d(CameraController.TAG, "retryInternal: " + RestartCameraTask.this.retryInternal);
                    if (!CameraController.this.javaCameraView.hasFrame()) {
                        if ((CameraController.this.javaCameraView != null && !CameraController.this.isCameraEnabled) || RestartCameraTask.this.retryInternal <= 0) {
                            RestartCameraTask.this.cancel();
                            CameraController.this.restart();
                            return;
                        }
                        return;
                    }
                    Log.d(CameraController.TAG, "hasFrame");
                    CameraController.this.isBusy = false;
                    if (RobotPreference.isEnabledFaceDetection(CameraController.this.context) && CameraController.this.shouldStart) {
                        CameraController.this.startFaceDetection();
                    }
                    if (CameraController.this.needOpenStreamer) {
                        CameraController.this.startStreaming();
                        CameraController.this.needOpenStreamer = false;
                    }
                    if (CameraController.this.needOpenReader) {
                        CameraController.this.startQRCodeScanning();
                        CameraController.this.needOpenReader = false;
                    }
                    RestartCameraTask.this.cancel();
                }
            });
        }
    }
}
