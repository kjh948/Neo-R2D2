package org.opencv.android;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.util.Log;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
@TargetApi(15)
public class CameraRenderer extends CameraGLRendererBase {
    public static final String LOGTAG = "CameraRenderer";
    private Camera mCamera;
    private boolean mPreviewStarted;

    CameraRenderer(CameraGLSurfaceView view) {
        super(view);
        this.mPreviewStarted = false;
    }

    @Override // org.opencv.android.CameraGLRendererBase
    protected synchronized void closeCamera() {
        Log.i(LOGTAG, "closeCamera");
        if (this.mCamera != null) {
            this.mCamera.stopPreview();
            this.mPreviewStarted = false;
            this.mCamera.release();
            this.mCamera = null;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:19:0x0058 A[Catch: all -> 0x0083, EDGE_INSN: B:82:0x0058->B:19:0x0058 BREAK  A[LOOP:0: B:13:0x002b->B:32:0x00ae], TRY_ENTER, TryCatch #2 {, blocks: (B:4:0x0007, B:6:0x0014, B:7:0x001b, B:8:0x0021, B:10:0x0025, B:13:0x002b, B:15:0x0031, B:16:0x004f, B:32:0x00ae, B:31:0x0087, B:19:0x0058, B:21:0x005c, B:62:0x0163, B:64:0x016f, B:66:0x0177, B:67:0x017c, B:68:0x0181, B:71:0x018b, B:25:0x0066, B:33:0x00b2, B:35:0x00b6, B:37:0x00bc, B:38:0x00c9, B:40:0x00cf, B:45:0x00e2, B:44:0x00d9, B:56:0x010b, B:57:0x0114, B:58:0x0132, B:61:0x013b, B:46:0x00e5, B:48:0x00e9, B:49:0x00f6, B:51:0x00fc, B:54:0x0106), top: B:77:0x0007, inners: #0, #1, #3, #4 }] */
    /* JADX WARN: Removed duplicated region for block: B:21:0x005c A[Catch: all -> 0x0083, TRY_LEAVE, TryCatch #2 {, blocks: (B:4:0x0007, B:6:0x0014, B:7:0x001b, B:8:0x0021, B:10:0x0025, B:13:0x002b, B:15:0x0031, B:16:0x004f, B:32:0x00ae, B:31:0x0087, B:19:0x0058, B:21:0x005c, B:62:0x0163, B:64:0x016f, B:66:0x0177, B:67:0x017c, B:68:0x0181, B:71:0x018b, B:25:0x0066, B:33:0x00b2, B:35:0x00b6, B:37:0x00bc, B:38:0x00c9, B:40:0x00cf, B:45:0x00e2, B:44:0x00d9, B:56:0x010b, B:57:0x0114, B:58:0x0132, B:61:0x013b, B:46:0x00e5, B:48:0x00e9, B:49:0x00f6, B:51:0x00fc, B:54:0x0106), top: B:77:0x0007, inners: #0, #1, #3, #4 }] */
    /* JADX WARN: Removed duplicated region for block: B:62:0x0163 A[Catch: all -> 0x0083, TryCatch #2 {, blocks: (B:4:0x0007, B:6:0x0014, B:7:0x001b, B:8:0x0021, B:10:0x0025, B:13:0x002b, B:15:0x0031, B:16:0x004f, B:32:0x00ae, B:31:0x0087, B:19:0x0058, B:21:0x005c, B:62:0x0163, B:64:0x016f, B:66:0x0177, B:67:0x017c, B:68:0x0181, B:71:0x018b, B:25:0x0066, B:33:0x00b2, B:35:0x00b6, B:37:0x00bc, B:38:0x00c9, B:40:0x00cf, B:45:0x00e2, B:44:0x00d9, B:56:0x010b, B:57:0x0114, B:58:0x0132, B:61:0x013b, B:46:0x00e5, B:48:0x00e9, B:49:0x00f6, B:51:0x00fc, B:54:0x0106), top: B:77:0x0007, inners: #0, #1, #3, #4 }] */
    @Override // org.opencv.android.CameraGLRendererBase
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    protected synchronized void openCamera(int r14) {
        /*
            Method dump skipped, instruction units count: 425
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: org.opencv.android.CameraRenderer.openCamera(int):void");
    }

    @Override // org.opencv.android.CameraGLRendererBase
    public synchronized void setCameraPreviewSize(int width, int height) {
        Log.i(LOGTAG, "setCameraPreviewSize: " + width + "x" + height);
        if (this.mCamera == null) {
            Log.e(LOGTAG, "Camera isn't initialized!");
        } else {
            if (this.mMaxCameraWidth > 0 && this.mMaxCameraWidth < width) {
                width = this.mMaxCameraWidth;
            }
            if (this.mMaxCameraHeight > 0 && this.mMaxCameraHeight < height) {
                height = this.mMaxCameraHeight;
            }
            Camera.Parameters param = this.mCamera.getParameters();
            List<Camera.Size> psize = param.getSupportedPreviewSizes();
            int bestWidth = 0;
            int bestHeight = 0;
            if (psize.size() > 0) {
                float aspect = width / height;
                for (Camera.Size size : psize) {
                    int w = size.width;
                    int h = size.height;
                    Log.d(LOGTAG, "checking camera preview size: " + w + "x" + h);
                    if (w <= width && h <= height && w >= bestWidth && h >= bestHeight && Math.abs(aspect - (w / h)) < 0.2d) {
                        bestWidth = w;
                        bestHeight = h;
                    }
                }
                if (bestWidth <= 0 || bestHeight <= 0) {
                    bestWidth = psize.get(0).width;
                    bestHeight = psize.get(0).height;
                    Log.e(LOGTAG, "Error: best size was not selected, using " + bestWidth + " x " + bestHeight);
                } else {
                    Log.i(LOGTAG, "Selected best size: " + bestWidth + " x " + bestHeight);
                }
                if (this.mPreviewStarted) {
                    this.mCamera.stopPreview();
                    this.mPreviewStarted = false;
                }
                this.mCameraWidth = bestWidth;
                this.mCameraHeight = bestHeight;
                param.setPreviewSize(bestWidth, bestHeight);
            }
            param.set("orientation", "landscape");
            this.mCamera.setParameters(param);
            this.mCamera.startPreview();
            this.mPreviewStarted = true;
        }
    }
}
