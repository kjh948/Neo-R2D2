package com.bullb.r2d2_nanopisystem;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Chronometer;
import com.bullb.r2d2_nanopisystem.WebSocket.StreamingServer;
import java.io.ByteArrayOutputStream;
import java.net.UnknownHostException;
import org.opencv.android.JavaCameraView;

/* JADX INFO: loaded from: classes.dex */
public class VideoStreamer implements JavaCameraView.VideoFrameCallback {
    private Context context;
    private byte[] latestData;
    private Camera.Parameters parameters;
    private int sendFrameNo;
    private Thread sendFrameThread;
    private Chronometer timer;
    private final String TAG = "VideoStreamer";
    private int frame = 0;
    private boolean isEnable = false;
    private int sendFPS = 10;

    public VideoStreamer(Context context) {
        this.context = context;
        this.timer = (Chronometer) ((Activity) context).findViewById(R.id.timer);
    }

    public void enable() {
        this.latestData = null;
        this.sendFrameNo = 0;
        this.isEnable = true;
        this.timer.setBase(SystemClock.elapsedRealtime());
        this.timer.start();
        this.sendFrameThread = createSendFrameThread();
        this.sendFrameThread.start();
    }

    public void disable() {
        this.isEnable = false;
        if (this.timer != null) {
            this.timer.stop();
        }
        if (this.sendFrameThread != null) {
            this.sendFrameThread.interrupt();
            this.sendFrameThread = null;
        }
    }

    public boolean isEnabled() {
        return this.isEnable;
    }

    public JavaCameraView.VideoFrameCallback getVideoFrameListener() {
        return this;
    }

    private Thread createSendFrameThread() {
        return new Thread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.VideoStreamer.1
            @Override // java.lang.Runnable
            public void run() {
                while (VideoStreamer.this.isEnable) {
                    long timeToSleep = 40;
                    if (VideoStreamer.this.latestData != null) {
                        VideoStreamer.this.sendFrame(VideoStreamer.this.latestData);
                        timeToSleep = 1000 / VideoStreamer.this.sendFPS;
                    }
                    try {
                        Log.d("VideoStreamer", "sleep" + timeToSleep);
                        if (timeToSleep > 0) {
                            Thread.sleep(timeToSleep);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendFrame(byte[] imageBytes) {
        try {
            StreamingServer streamingServer = StreamingServer.getInstance(this.context);
            if (this.isEnable && streamingServer != null) {
                streamingServer.send(imageBytes);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override // org.opencv.android.JavaCameraView.VideoFrameCallback
    public void onPreview(final byte[] data, Camera camera) {
        Log.d("VideoStreamer", "onPreview");
        if (this.isEnable) {
            this.frame++;
            double fps = ((double) this.frame) / ((SystemClock.elapsedRealtime() - this.timer.getBase()) / 1000.0d);
            Log.d("onPreviewFram", "Data size:" + String.valueOf(data.length) + "   fps:" + String.valueOf(fps));
            this.parameters = camera.getParameters();
            int imageFormat = this.parameters.getPreviewFormat();
            if (imageFormat == 17) {
                Log.d("onPreviewFram", "NV21");
                Thread thread = new Thread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.VideoStreamer.2
                    @Override // java.lang.Runnable
                    public void run() {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        YuvImage yuvImage = new YuvImage(data, 17, VideoStreamer.this.parameters.getPreviewSize().width, VideoStreamer.this.parameters.getPreviewSize().height, null);
                        if (VideoStreamer.this.parameters.getPreviewSize().height == 480) {
                            yuvImage.compressToJpeg(new Rect(0, 60, VideoStreamer.this.parameters.getPreviewSize().width, VideoStreamer.this.parameters.getPreviewSize().height - 60), 20, out);
                        } else {
                            yuvImage.compressToJpeg(new Rect(0, 0, VideoStreamer.this.parameters.getPreviewSize().width, VideoStreamer.this.parameters.getPreviewSize().height), 20, out);
                        }
                        byte[] imageBytes = out.toByteArray();
                        VideoStreamer.this.latestData = imageBytes;
                        Log.d("jpeg_size", String.valueOf(imageBytes.length));
                    }
                });
                thread.start();
            }
        }
    }
}
