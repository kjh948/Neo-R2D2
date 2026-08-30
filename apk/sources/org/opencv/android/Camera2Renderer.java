package org.opencv.android;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: classes.dex */
@TargetApi(21)
public class Camera2Renderer extends CameraGLRendererBase {
    protected final String LOGTAG;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private CameraDevice mCameraDevice;
    private String mCameraID;
    private Semaphore mCameraOpenCloseLock;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Size mPreviewSize;
    private final CameraDevice.StateCallback mStateCallback;

    Camera2Renderer(CameraGLSurfaceView view) {
        super(view);
        this.LOGTAG = "Camera2Renderer";
        this.mPreviewSize = new Size(-1, -1);
        this.mCameraOpenCloseLock = new Semaphore(1);
        this.mStateCallback = new CameraDevice.StateCallback() { // from class: org.opencv.android.Camera2Renderer.1
            @Override // android.hardware.camera2.CameraDevice.StateCallback
            public void onOpened(CameraDevice cameraDevice) {
                Camera2Renderer.this.mCameraDevice = cameraDevice;
                Camera2Renderer.this.mCameraOpenCloseLock.release();
                Camera2Renderer.this.createCameraPreviewSession();
            }

            @Override // android.hardware.camera2.CameraDevice.StateCallback
            public void onDisconnected(CameraDevice cameraDevice) {
                cameraDevice.close();
                Camera2Renderer.this.mCameraDevice = null;
                Camera2Renderer.this.mCameraOpenCloseLock.release();
            }

            @Override // android.hardware.camera2.CameraDevice.StateCallback
            public void onError(CameraDevice cameraDevice, int error) {
                cameraDevice.close();
                Camera2Renderer.this.mCameraDevice = null;
                Camera2Renderer.this.mCameraOpenCloseLock.release();
            }
        };
    }

    @Override // org.opencv.android.CameraGLRendererBase
    protected void doStart() {
        Log.d("Camera2Renderer", "doStart");
        startBackgroundThread();
        super.doStart();
    }

    @Override // org.opencv.android.CameraGLRendererBase
    protected void doStop() {
        Log.d("Camera2Renderer", "doStop");
        super.doStop();
        stopBackgroundThread();
    }

    boolean cacPreviewSize(int width, int height) {
        Log.i("Camera2Renderer", "cacPreviewSize: " + width + "x" + height);
        if (this.mCameraID == null) {
            Log.e("Camera2Renderer", "Camera isn't initialized!");
            return false;
        }
        CameraManager manager = (CameraManager) this.mView.getContext().getSystemService("camera");
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(this.mCameraID);
            StreamConfigurationMap map = (StreamConfigurationMap) characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int bestWidth = 0;
            int bestHeight = 0;
            float aspect = width / height;
            for (Size psize : map.getOutputSizes(SurfaceTexture.class)) {
                int w = psize.getWidth();
                int h = psize.getHeight();
                Log.d("Camera2Renderer", "trying size: " + w + "x" + h);
                if (width >= w && height >= h && bestWidth <= w && bestHeight <= h && Math.abs(aspect - (w / h)) < 0.2d) {
                    bestWidth = w;
                    bestHeight = h;
                }
            }
            Log.i("Camera2Renderer", "best size: " + bestWidth + "x" + bestHeight);
            if (bestWidth == 0 || bestHeight == 0 || (this.mPreviewSize.getWidth() == bestWidth && this.mPreviewSize.getHeight() == bestHeight)) {
                return false;
            }
            this.mPreviewSize = new Size(bestWidth, bestHeight);
            return true;
        } catch (CameraAccessException e) {
            Log.e("Camera2Renderer", "cacPreviewSize - Camera Access Exception");
            return false;
        } catch (IllegalArgumentException e2) {
            Log.e("Camera2Renderer", "cacPreviewSize - Illegal Argument Exception");
            return false;
        } catch (SecurityException e3) {
            Log.e("Camera2Renderer", "cacPreviewSize - Security Exception");
            return false;
        }
    }

    @Override // org.opencv.android.CameraGLRendererBase
    protected void openCamera(int id) {
        Log.i("Camera2Renderer", "openCamera");
        CameraManager manager = (CameraManager) this.mView.getContext().getSystemService("camera");
        try {
            String[] camList = manager.getCameraIdList();
            if (camList.length == 0) {
                Log.e("Camera2Renderer", "Error: camera isn't detected.");
                return;
            }
            if (id != -1) {
                for (String cameraID : camList) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
                    if ((id == 99 && ((Integer) characteristics.get(CameraCharacteristics.LENS_FACING)).intValue() == 1) || (id == 98 && ((Integer) characteristics.get(CameraCharacteristics.LENS_FACING)).intValue() == 0)) {
                        this.mCameraID = cameraID;
                        break;
                    }
                }
            } else {
                this.mCameraID = camList[0];
            }
            if (this.mCameraID != null) {
                if (!this.mCameraOpenCloseLock.tryAcquire(2500L, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }
                Log.i("Camera2Renderer", "Opening camera: " + this.mCameraID);
                manager.openCamera(this.mCameraID, this.mStateCallback, this.mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e("Camera2Renderer", "OpenCamera - Camera Access Exception");
        } catch (IllegalArgumentException e2) {
            Log.e("Camera2Renderer", "OpenCamera - Illegal Argument Exception");
        } catch (InterruptedException e3) {
            Log.e("Camera2Renderer", "OpenCamera - Interrupted Exception");
        } catch (SecurityException e4) {
            Log.e("Camera2Renderer", "OpenCamera - Security Exception");
        }
    }

    @Override // org.opencv.android.CameraGLRendererBase
    protected void closeCamera() {
        Log.i("Camera2Renderer", "closeCamera");
        try {
            try {
                this.mCameraOpenCloseLock.acquire();
                if (this.mCaptureSession != null) {
                    this.mCaptureSession.close();
                    this.mCaptureSession = null;
                }
                if (this.mCameraDevice != null) {
                    this.mCameraDevice.close();
                    this.mCameraDevice = null;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
            }
        } finally {
            this.mCameraOpenCloseLock.release();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void createCameraPreviewSession() {
        int w = this.mPreviewSize.getWidth();
        int h = this.mPreviewSize.getHeight();
        Log.i("Camera2Renderer", "createCameraPreviewSession(" + w + "x" + h + ")");
        if (w >= 0) {
            if (h >= 0) {
                try {
                    this.mCameraOpenCloseLock.acquire();
                    if (this.mCameraDevice == null) {
                        this.mCameraOpenCloseLock.release();
                        Log.e("Camera2Renderer", "createCameraPreviewSession: camera isn't opened");
                    } else if (this.mCaptureSession != null) {
                        this.mCameraOpenCloseLock.release();
                        Log.e("Camera2Renderer", "createCameraPreviewSession: mCaptureSession is already started");
                    } else if (this.mSTexture == null) {
                        this.mCameraOpenCloseLock.release();
                        Log.e("Camera2Renderer", "createCameraPreviewSession: preview SurfaceTexture is null");
                    } else {
                        this.mSTexture.setDefaultBufferSize(w, h);
                        Surface surface = new Surface(this.mSTexture);
                        this.mPreviewRequestBuilder = this.mCameraDevice.createCaptureRequest(1);
                        this.mPreviewRequestBuilder.addTarget(surface);
                        this.mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() { // from class: org.opencv.android.Camera2Renderer.2
                            @Override // android.hardware.camera2.CameraCaptureSession.StateCallback
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                Camera2Renderer.this.mCaptureSession = cameraCaptureSession;
                                try {
                                    Camera2Renderer.this.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 4);
                                    Camera2Renderer.this.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, 2);
                                    Camera2Renderer.this.mCaptureSession.setRepeatingRequest(Camera2Renderer.this.mPreviewRequestBuilder.build(), null, Camera2Renderer.this.mBackgroundHandler);
                                    Log.i("Camera2Renderer", "CameraPreviewSession has been started");
                                } catch (CameraAccessException e) {
                                    Log.e("Camera2Renderer", "createCaptureSession failed");
                                }
                                Camera2Renderer.this.mCameraOpenCloseLock.release();
                            }

                            @Override // android.hardware.camera2.CameraCaptureSession.StateCallback
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                                Log.e("Camera2Renderer", "createCameraPreviewSession failed");
                                Camera2Renderer.this.mCameraOpenCloseLock.release();
                            }
                        }, this.mBackgroundHandler);
                    }
                } catch (CameraAccessException e) {
                    Log.e("Camera2Renderer", "createCameraPreviewSession");
                } catch (InterruptedException e2) {
                    throw new RuntimeException("Interrupted while createCameraPreviewSession", e2);
                }
            }
        }
    }

    private void startBackgroundThread() {
        Log.i("Camera2Renderer", "startBackgroundThread");
        stopBackgroundThread();
        this.mBackgroundThread = new HandlerThread("CameraBackground");
        this.mBackgroundThread.start();
        this.mBackgroundHandler = new Handler(this.mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        Log.i("Camera2Renderer", "stopBackgroundThread");
        if (this.mBackgroundThread != null) {
            this.mBackgroundThread.quitSafely();
            try {
                this.mBackgroundThread.join();
                this.mBackgroundThread = null;
                this.mBackgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e("Camera2Renderer", "stopBackgroundThread");
            }
        }
    }

    @Override // org.opencv.android.CameraGLRendererBase
    protected void setCameraPreviewSize(int width, int height) {
        Log.i("Camera2Renderer", "setCameraPreviewSize(" + width + "x" + height + ")");
        if (this.mMaxCameraWidth > 0 && this.mMaxCameraWidth < width) {
            width = this.mMaxCameraWidth;
        }
        if (this.mMaxCameraHeight > 0 && this.mMaxCameraHeight < height) {
            height = this.mMaxCameraHeight;
        }
        try {
            this.mCameraOpenCloseLock.acquire();
            boolean needReconfig = cacPreviewSize(width, height);
            this.mCameraWidth = this.mPreviewSize.getWidth();
            this.mCameraHeight = this.mPreviewSize.getHeight();
            if (!needReconfig) {
                this.mCameraOpenCloseLock.release();
                return;
            }
            if (this.mCaptureSession != null) {
                Log.d("Camera2Renderer", "closing existing previewSession");
                this.mCaptureSession.close();
                this.mCaptureSession = null;
            }
            this.mCameraOpenCloseLock.release();
            createCameraPreviewSession();
        } catch (InterruptedException e) {
            this.mCameraOpenCloseLock.release();
            throw new RuntimeException("Interrupted while setCameraPreviewSize.", e);
        }
    }
}
