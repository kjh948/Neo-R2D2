package com.bullb.r2d2_nanopisystem.FaceDetection;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.LEDLightController;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.R;
import com.bullb.r2d2_nanopisystem.Sound.SoundPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/* JADX INFO: loaded from: classes.dex */
public class FaceDetection implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final double FOLLOW_SPEED = 0.5d;
    private static final double MAX_DEGREE = 40.0d;
    private static final int MAX_SHIFT_ANGLE = 5;
    private static final String TAG = "FaceDetection";
    private Commander commander;
    private Context context;
    private File mCascadeFile;
    private Mat mGray;
    private CascadeClassifier mJavaDetector;
    private static final Scalar FACE_RECT_COLOR = new Scalar(0.0d, 255.0d, 0.0d, 255.0d);
    static int SCALE_DOWN_RATIO = 3;
    private int mAbsoluteFaceSize = 0;
    private float mRelativeFaceSize = 0.2f;
    private int numFrameProcessing = 0;
    private final int MAX_FRAME_PROCESS = 1;
    private int faceID = 0;
    private Face targetFace = null;
    private ArrayList<Face> storedFace = new ArrayList<>();
    private boolean showTargetFace = false;
    private boolean enabled = false;
    private boolean init = false;
    private boolean isfaceDetected = false;
    private Timer faceTrackingTimer = new Timer();
    private boolean isFaceTracking = false;
    private int width = JavaCameraView.WIDTH;
    TimerTask faceTrackingTimeoutTask = null;

    static /* synthetic */ int access$108(FaceDetection x0) {
        int i = x0.numFrameProcessing;
        x0.numFrameProcessing = i + 1;
        return i;
    }

    static /* synthetic */ int access$110(FaceDetection x0) {
        int i = x0.numFrameProcessing;
        x0.numFrameProcessing = i - 1;
        return i;
    }

    public FaceDetection(Context context) {
        this.context = context;
        this.commander = Commander.getInstance(context);
    }

    public boolean initFaceDetectionLibrary() {
        if (isInit()) {
            return true;
        }
        try {
            InputStream is = this.context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = this.context.getDir("cascade", 0);
            this.mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(this.mCascadeFile);
            byte[] buffer = new byte[4096];
            while (true) {
                int bytesRead = is.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            this.mJavaDetector = new CascadeClassifier(this.mCascadeFile.getAbsolutePath());
            if (this.mJavaDetector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                this.mJavaDetector = null;
                return false;
            }
            Log.i(TAG, "Loaded cascade classifier from " + this.mCascadeFile.getAbsolutePath());
            cascadeDir.delete();
            this.init = true;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
            return false;
        }
    }

    public boolean isInit() {
        return this.init;
    }

    public CameraBridgeViewBase.CvCameraViewListener2 getCVCamerViewListener() {
        return this;
    }

    public boolean enable() {
        if (this.enabled) {
            return true;
        }
        if (isInit() || initFaceDetectionLibrary()) {
            this.enabled = true;
            return true;
        }
        return false;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    @Override // org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
    public void onCameraViewStarted(int width, int height) {
        this.mGray = new Mat();
    }

    @Override // org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
    public void onCameraViewStopped() {
        this.mGray.release();
    }

    @Override // org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        this.mGray = inputFrame.gray();
        if (isEnabled()) {
            Point center = new Point(this.mGray.cols() / 2, this.mGray.rows() / 2);
            Mat rotateMat = Imgproc.getRotationMatrix2D(center, 270.0d, 1.0d);
            Imgproc.warpAffine(this.mGray, this.mGray, rotateMat, this.mGray.size());
            Mat resizedGray = new Mat();
            if (this.mGray.cols() > 0 && this.mGray.rows() > 0) {
                Imgproc.resize(this.mGray, resizedGray, new Size(this.mGray.cols() / SCALE_DOWN_RATIO, this.mGray.rows() / SCALE_DOWN_RATIO));
            }
            if (this.mAbsoluteFaceSize == 0) {
                int height = resizedGray.rows();
                if (Math.round(height * this.mRelativeFaceSize) > 0) {
                    this.mAbsoluteFaceSize = Math.round(height * this.mRelativeFaceSize);
                }
            }
            if (this.numFrameProcessing < 1) {
                new FaceRecognitionTask().execute(resizedGray);
            }
            if (this.showTargetFace && this.targetFace != null) {
                Imgproc.rectangle(this.mGray, this.targetFace.getRect().tl(), this.targetFace.getRect().br(), FACE_RECT_COLOR, 3);
            }
        }
        return this.mGray;
    }

    private class FaceRecognitionTask extends AsyncTask<Object, Void, ArrayList<Rect>> {
        private FaceRecognitionTask() {
        }

        @Override // android.os.AsyncTask
        protected void onPreExecute() {
            super.onPreExecute();
            FaceDetection.access$108(FaceDetection.this);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public ArrayList<Rect> doInBackground(Object... params) {
            Mat mGray = (Mat) params[0];
            MatOfRect faces = new MatOfRect();
            if (FaceDetection.this.mJavaDetector != null) {
                FaceDetection.this.mJavaDetector.detectMultiScale(mGray, faces, 1.1d, 5, 2, new Size(FaceDetection.this.mAbsoluteFaceSize, FaceDetection.this.mAbsoluteFaceSize), new Size());
            }
            Rect[] facesArray = faces.toArray();
            Log.d("FaceNum", String.valueOf(facesArray.length));
            FaceDetection.this.updateFace(facesArray);
            return null;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(ArrayList<Rect> result) {
            if (!FaceDetection.this.showTargetFace || FaceDetection.this.targetFace == null) {
                FaceDetection.this.onFaceLose();
            } else {
                FaceDetection.this.changeHeadDirection();
                FaceDetection.this.onFaceDetected();
            }
            FaceDetection.access$110(FaceDetection.this);
        }
    }

    public boolean isIsfaceDetected() {
        return this.isfaceDetected;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onFaceDetected() {
        this.isfaceDetected = true;
        if (!this.isFaceTracking) {
            onFaceTrackingStart();
        }
        if (this.faceTrackingTimeoutTask != null) {
            this.faceTrackingTimeoutTask.cancel();
        }
        this.faceTrackingTimeoutTask = new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.FaceDetection.FaceDetection.1
            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                if (!FaceDetection.this.isIsfaceDetected()) {
                    FaceDetection.this.onFaceTrackingStop();
                }
            }
        };
        this.faceTrackingTimer.schedule(this.faceTrackingTimeoutTask, 1500L);
        ModeController.getInstance(this.context).restartSleepTimer();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onFaceLose() {
        this.isfaceDetected = false;
    }

    private void onFaceTrackingStart() {
        this.isFaceTracking = true;
        LEDLightController.getInstance(this.context).faceDetectLightStart();
        SoundPlayer.getInstance(this.context).play(13, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onFaceTrackingStop() {
        this.isFaceTracking = false;
        LEDLightController.getInstance(this.context).faceDetectLightStop();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void changeHeadDirection() {
        Rect targetRect = this.targetFace.getRect();
        double faceX = (targetRect.tl().x + targetRect.br().x) / 2.0d;
        double centerX = this.width / 2;
        double diff = faceX - centerX;
        double targetAngle = (diff / ((double) this.width)) * MAX_DEGREE;
        int rotateAngle = (int) (FOLLOW_SPEED * targetAngle);
        Log.d(TAG, "Target: " + ((int) targetAngle) + ", rotate: " + rotateAngle);
        if ((targetAngle > 2.0d || targetAngle < -2.0d) && rotateAngle != 0) {
            if (rotateAngle > 5) {
                rotateAngle = 5;
            } else if (rotateAngle < -5) {
                rotateAngle = -5;
            }
            this.commander.headShift(rotateAngle);
        }
    }

    private void ScaleUpFaces(Rect[] facesArray) {
        for (Rect face : facesArray) {
            face.set(new double[]{face.x * SCALE_DOWN_RATIO, face.y * SCALE_DOWN_RATIO, face.width * SCALE_DOWN_RATIO, face.height * SCALE_DOWN_RATIO});
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateFace(Rect[] facesArray) {
        ScaleUpFaces(facesArray);
        Long now = Long.valueOf(System.currentTimeMillis());
        boolean isTargetFaceExist = false;
        for (int i = 0; i < facesArray.length; i++) {
            Face faceFound = searchPrevFace(facesArray[i]);
            if (faceFound == null) {
                if (this.storedFace.size() < 10) {
                    this.faceID++;
                    this.storedFace.add(new Face(now.longValue(), facesArray[i], this.faceID));
                }
            } else {
                int index = this.storedFace.indexOf(faceFound);
                faceFound.setLastExistTime(now.longValue());
                faceFound.setRect(facesArray[i]);
                this.storedFace.set(index, faceFound);
                if (this.targetFace != null && faceFound.getFaceId() == this.targetFace.getFaceId()) {
                    isTargetFaceExist = true;
                }
            }
        }
        ArrayList<Face> removeList = new ArrayList<>();
        for (Face face : this.storedFace) {
            if (now.longValue() - face.getLastExistTime() > 1500) {
                removeList.add(face);
            }
        }
        this.storedFace.removeAll(removeList);
        if (this.storedFace.size() <= 0) {
            this.targetFace = null;
            Log.d("TargetFace", "null");
        } else if (now.longValue() - this.storedFace.get(0).getFirstExistTime() > 1500) {
            this.targetFace = this.storedFace.get(0);
            Log.d("TargetFace", String.valueOf(this.targetFace.getFaceId()));
        } else {
            this.targetFace = null;
        }
        if (!isTargetFaceExist) {
            this.showTargetFace = false;
        } else {
            this.showTargetFace = true;
        }
    }

    private Face searchPrevFace(Rect rect) {
        double most_closer_obj_delta = ((double) this.width) * 0.35d;
        for (Face existingFace : this.storedFace) {
            Rect r = existingFace.getRect();
            Point p1 = r.tl();
            Point p2 = rect.tl();
            double delta = Math.sqrt(((p2.x - p1.x) * (p2.x - p1.x)) + ((p2.y - p1.y) * (p2.y - p1.y)));
            double sizeDelta = Math.min(rect.area(), r.area()) / Math.max(rect.area(), r.area());
            if (delta < most_closer_obj_delta && sizeDelta > 0.7d) {
                return existingFace;
            }
        }
        return null;
    }
}
