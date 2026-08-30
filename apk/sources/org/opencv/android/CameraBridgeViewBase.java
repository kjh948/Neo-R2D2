package org.opencv.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import java.util.List;
import org.opencv.R;
import org.opencv.core.Mat;
import org.opencv.core.Size;

/* JADX INFO: loaded from: classes.dex */
public abstract class CameraBridgeViewBase extends SurfaceView implements SurfaceHolder.Callback {
    public static final int CAMERA_ID_ANY = -1;
    public static final int CAMERA_ID_BACK = 99;
    public static final int CAMERA_ID_FRONT = 98;
    public static final int GRAY = 2;
    private static final int MAX_UNSPECIFIED = -1;
    public static final int RGBA = 1;
    private static final int STARTED = 1;
    private static final int STOPPED = 0;
    private static final String TAG = "CameraBridge";
    private Bitmap mCacheBitmap;
    protected int mCameraIndex;
    protected boolean mEnabled;
    protected FpsMeter mFpsMeter;
    protected int mFrameHeight;
    protected int mFrameWidth;
    private CvCameraViewListener2 mListener;
    protected int mMaxHeight;
    protected int mMaxWidth;
    protected int mPreviewFormat;
    protected float mScale;
    private int mState;
    private boolean mSurfaceExist;
    private Object mSyncObject;

    public interface CvCameraViewFrame {
        Mat gray();

        Mat rgba();
    }

    public interface CvCameraViewListener {
        Mat onCameraFrame(Mat mat);

        void onCameraViewStarted(int i, int i2);

        void onCameraViewStopped();
    }

    public interface CvCameraViewListener2 {
        Mat onCameraFrame(CvCameraViewFrame cvCameraViewFrame);

        void onCameraViewStarted(int i, int i2);

        void onCameraViewStopped();
    }

    public interface ListItemAccessor {
        int getHeight(Object obj);

        int getWidth(Object obj);
    }

    protected abstract boolean connectCamera(int i, int i2);

    protected abstract void disconnectCamera();

    public CameraBridgeViewBase(Context context, int cameraId) {
        super(context);
        this.mState = 0;
        this.mSyncObject = new Object();
        this.mScale = 0.0f;
        this.mPreviewFormat = 1;
        this.mCameraIndex = -1;
        this.mFpsMeter = null;
        this.mCameraIndex = cameraId;
        getHolder().addCallback(this);
        this.mMaxWidth = -1;
        this.mMaxHeight = -1;
    }

    public CameraBridgeViewBase(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mState = 0;
        this.mSyncObject = new Object();
        this.mScale = 0.0f;
        this.mPreviewFormat = 1;
        this.mCameraIndex = -1;
        this.mFpsMeter = null;
        int count = attrs.getAttributeCount();
        Log.d(TAG, "Attr count: " + Integer.valueOf(count));
        TypedArray styledAttrs = getContext().obtainStyledAttributes(attrs, R.styleable.CameraBridgeViewBase);
        if (styledAttrs.getBoolean(R.styleable.CameraBridgeViewBase_show_fps, false)) {
            enableFpsMeter();
        }
        this.mCameraIndex = styledAttrs.getInt(R.styleable.CameraBridgeViewBase_camera_id, -1);
        getHolder().addCallback(this);
        this.mMaxWidth = -1;
        this.mMaxHeight = -1;
        styledAttrs.recycle();
    }

    public void setCameraIndex(int cameraIndex) {
        this.mCameraIndex = cameraIndex;
    }

    protected class CvCameraViewListenerAdapter implements CvCameraViewListener2 {
        private CvCameraViewListener mOldStyleListener;
        private int mPreviewFormat = 1;

        public CvCameraViewListenerAdapter(CvCameraViewListener oldStypeListener) {
            this.mOldStyleListener = oldStypeListener;
        }

        @Override // org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
        public void onCameraViewStarted(int width, int height) {
            this.mOldStyleListener.onCameraViewStarted(width, height);
        }

        @Override // org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
        public void onCameraViewStopped() {
            this.mOldStyleListener.onCameraViewStopped();
        }

        @Override // org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
        public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
            switch (this.mPreviewFormat) {
                case 1:
                    Mat result = this.mOldStyleListener.onCameraFrame(inputFrame.rgba());
                    return result;
                case 2:
                    Mat result2 = this.mOldStyleListener.onCameraFrame(inputFrame.gray());
                    return result2;
                default:
                    Log.e(CameraBridgeViewBase.TAG, "Invalid frame format! Only RGBA and Gray Scale are supported!");
                    return null;
            }
        }

        public void setFrameFormat(int format) {
            this.mPreviewFormat = format;
        }
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        Log.d(TAG, "call surfaceChanged event");
        synchronized (this.mSyncObject) {
            if (!this.mSurfaceExist) {
                this.mSurfaceExist = true;
                checkCurrentState();
            } else {
                this.mSurfaceExist = false;
                checkCurrentState();
                this.mSurfaceExist = true;
                checkCurrentState();
            }
        }
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (this.mSyncObject) {
            this.mSurfaceExist = false;
            checkCurrentState();
        }
    }

    public void enableView() {
        synchronized (this.mSyncObject) {
            this.mEnabled = true;
            checkCurrentState();
        }
    }

    public void disableView() {
        synchronized (this.mSyncObject) {
            this.mEnabled = false;
            checkCurrentState();
        }
    }

    public void enableFpsMeter() {
        if (this.mFpsMeter == null) {
            this.mFpsMeter = new FpsMeter();
            this.mFpsMeter.setResolution(this.mFrameWidth, this.mFrameHeight);
        }
    }

    public void disableFpsMeter() {
        this.mFpsMeter = null;
    }

    public void setCvCameraViewListener(CvCameraViewListener2 listener) {
        this.mListener = listener;
    }

    public void setCvCameraViewListener(CvCameraViewListener listener) {
        CvCameraViewListenerAdapter adapter = new CvCameraViewListenerAdapter(listener);
        adapter.setFrameFormat(this.mPreviewFormat);
        this.mListener = adapter;
    }

    public void setMaxFrameSize(int maxWidth, int maxHeight) {
        this.mMaxWidth = maxWidth;
        this.mMaxHeight = maxHeight;
    }

    public void SetCaptureFormat(int format) {
        this.mPreviewFormat = format;
        if (this.mListener instanceof CvCameraViewListenerAdapter) {
            CvCameraViewListenerAdapter adapter = (CvCameraViewListenerAdapter) this.mListener;
            adapter.setFrameFormat(this.mPreviewFormat);
        }
    }

    private void checkCurrentState() {
        int targetState;
        Log.d(TAG, "call checkCurrentState");
        if (this.mEnabled) {
            targetState = 1;
        } else {
            targetState = 0;
        }
        if (targetState != this.mState) {
            processExitState(this.mState);
            this.mState = targetState;
            processEnterState(this.mState);
        }
    }

    private void processEnterState(int state) {
        Log.d(TAG, "call processEnterState: " + state);
        switch (state) {
            case 0:
                onEnterStoppedState();
                if (this.mListener != null) {
                    this.mListener.onCameraViewStopped();
                }
                break;
            case 1:
                onEnterStartedState();
                if (this.mListener != null) {
                    this.mListener.onCameraViewStarted(this.mFrameWidth, this.mFrameHeight);
                }
                break;
        }
    }

    private void processExitState(int state) {
        Log.d(TAG, "call processExitState: " + state);
        switch (state) {
            case 0:
                onExitStoppedState();
                break;
            case 1:
                onExitStartedState();
                break;
        }
    }

    private void onEnterStoppedState() {
    }

    private void onExitStoppedState() {
    }

    private void onEnterStartedState() {
        Log.d(TAG, "call onEnterStartedState");
        if (!connectCamera(getWidth(), getHeight())) {
            ((Activity) getContext()).runOnUiThread(new Runnable() { // from class: org.opencv.android.CameraBridgeViewBase.1
                @Override // java.lang.Runnable
                public void run() {
                    Toast.makeText(CameraBridgeViewBase.this.getContext(), "It seems that you device does not support camera (or it is locked). Application will be closed.", 0).show();
                }
            });
        }
    }

    private void onExitStartedState() {
        disconnectCamera();
        if (this.mCacheBitmap != null) {
            this.mCacheBitmap.recycle();
        }
    }

    protected void deliverAndDrawFrame(CvCameraViewFrame frame) {
        Mat modified;
        Canvas canvas;
        if (this.mListener != null) {
            modified = this.mListener.onCameraFrame(frame);
        } else {
            modified = frame.rgba();
        }
        boolean bmpValid = true;
        if (modified != null) {
            try {
                Utils.matToBitmap(modified, this.mCacheBitmap);
            } catch (Exception e) {
                Log.e(TAG, "Mat type: " + modified);
                Log.e(TAG, "Bitmap type: " + this.mCacheBitmap.getWidth() + "*" + this.mCacheBitmap.getHeight());
                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
                bmpValid = false;
            }
        }
        if (bmpValid && this.mCacheBitmap != null && (canvas = getHolder().lockCanvas()) != null) {
            Matrix matrix = new Matrix();
            matrix.setRotate(90.0f);
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            if (this.mScale != 0.0f) {
                canvas.drawBitmap(this.mCacheBitmap, new Rect(0, 0, this.mCacheBitmap.getWidth(), this.mCacheBitmap.getHeight()), new Rect((int) ((canvas.getWidth() - (this.mScale * this.mCacheBitmap.getWidth())) / 2.0f), (int) ((canvas.getHeight() - (this.mScale * this.mCacheBitmap.getHeight())) / 2.0f), (int) (((canvas.getWidth() - (this.mScale * this.mCacheBitmap.getWidth())) / 2.0f) + (this.mScale * this.mCacheBitmap.getWidth())), (int) (((canvas.getHeight() - (this.mScale * this.mCacheBitmap.getHeight())) / 2.0f) + (this.mScale * this.mCacheBitmap.getHeight()))), (Paint) null);
            } else {
                canvas.drawBitmap(this.mCacheBitmap, new Rect(0, 0, this.mCacheBitmap.getWidth(), this.mCacheBitmap.getHeight()), new Rect((canvas.getWidth() - this.mCacheBitmap.getWidth()) / 2, (canvas.getHeight() - this.mCacheBitmap.getHeight()) / 2, ((canvas.getWidth() - this.mCacheBitmap.getWidth()) / 2) + this.mCacheBitmap.getWidth(), ((canvas.getHeight() - this.mCacheBitmap.getHeight()) / 2) + this.mCacheBitmap.getHeight()), (Paint) null);
            }
            if (this.mFpsMeter != null) {
                this.mFpsMeter.measure();
                this.mFpsMeter.draw(canvas, 20.0f, 30.0f);
            }
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    protected void AllocateCache() {
        this.mCacheBitmap = Bitmap.createBitmap(this.mFrameWidth, this.mFrameHeight, Bitmap.Config.ARGB_8888);
    }

    protected Size calculateCameraFrameSize(List<?> supportedSizes, ListItemAccessor accessor, int surfaceWidth, int surfaceHeight) {
        int calcWidth = 0;
        int calcHeight = 0;
        int maxAllowedWidth = (this.mMaxWidth == -1 || this.mMaxWidth >= surfaceWidth) ? surfaceWidth : this.mMaxWidth;
        int maxAllowedHeight = (this.mMaxHeight == -1 || this.mMaxHeight >= surfaceHeight) ? surfaceHeight : this.mMaxHeight;
        for (Object size : supportedSizes) {
            int width = accessor.getWidth(size);
            int height = accessor.getHeight(size);
            if (width <= maxAllowedWidth && height <= maxAllowedHeight && width >= calcWidth && height >= calcHeight) {
                calcWidth = width;
                calcHeight = height;
            }
        }
        return new Size(calcWidth, calcHeight);
    }
}
