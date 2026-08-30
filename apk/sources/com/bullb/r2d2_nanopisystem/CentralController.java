package com.bullb.r2d2_nanopisystem;

import android.content.Context;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceRecognizer;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import org.opencv.android.JavaCameraView;

/* JADX INFO: loaded from: classes.dex */
public class CentralController {
    private static CentralController centralController;
    private final String TAG = "CentralController";
    private CameraController cameraController;
    private Context context;
    private VoiceRecognizer voiceRecognizer;

    public static synchronized CentralController getInstance(Context context) {
        if (centralController == null) {
            centralController = new CentralController(context);
        }
        return centralController;
    }

    private CentralController(Context context) {
        this.context = context;
        this.voiceRecognizer = VoiceRecognizer.getInstance(context);
    }

    public void setJavaCameraView(@NonNull JavaCameraView javaCameraView) {
        this.cameraController = new CameraController(this.context, javaCameraView);
    }

    public void setMute(boolean isMuted) {
        AudioManager audioManager = (AudioManager) this.context.getSystemService("audio");
        if (audioManager != null) {
            audioManager.setRingerMode(isMuted ? 0 : 2);
            audioManager.setStreamVolume(3, isMuted ? 0 : (int) (audioManager.getStreamMaxVolume(3) * 0.7f), 0);
        }
    }

    public void startFaceDetection() {
        if (!RobotPreference.isEnabledFaceDetection(this.context)) {
            Log.d("CentralController", "start face Detectiion when disabled");
            return;
        }
        if (ModeController.getInstance(this.context).getMode() == 4) {
            Log.d("CentralController", "cannot start face detection when patrol");
        } else if (ModeController.getInstance(this.context).getMode() == 3) {
            Log.d("CentralController", "cannot start face detection when pair");
        } else {
            this.cameraController.startFaceDetection();
        }
    }

    public boolean isFaceDetected() {
        if (this.cameraController == null || this.cameraController.getFaceDetection() == null) {
            return false;
        }
        return this.cameraController.getFaceDetection().isIsfaceDetected();
    }

    public void startQRCodeReader() {
        this.cameraController.startQRCodeScanning();
    }

    public void stopQRCodeReader() {
        this.cameraController.stopQRCodeScanning();
    }

    public void stopFaceDetection() {
        this.cameraController.stopFaceDetection();
    }

    public void stopCamera() {
        this.cameraController.stopCamera();
    }

    public void startCamera() {
        this.cameraController.startCamera();
    }

    public void stopVoiceRecognition() {
        this.voiceRecognizer.stop();
    }

    public void startVoiceRecognition() {
        if (!RobotPreference.isEnabledVoiceRecognition(this.context)) {
            Log.d("CentralController", "cannot start voice recognition when disabled");
            return;
        }
        if (ModeController.getInstance(this.context).getMode() == 4) {
            Log.d("CentralController", "cannot start voice recognition when patrol");
        } else if (ModeController.getInstance(this.context).getMode() == 3) {
            Log.d("CentralController", "cannot start voice recognition when pair");
        } else if (this.voiceRecognizer != null) {
            this.voiceRecognizer.start();
        }
    }

    public void startVideoStreaming() {
        this.cameraController.startStreaming();
    }

    public void stopVideoStreaming() {
        this.cameraController.stopStreaming();
    }

    public void stopAllControl() {
        stopCamera();
        stopVoiceRecognition();
    }
}
