package com.bullb.r2d2_nanopisystem;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import org.opencv.android.JavaCameraView;

/* JADX INFO: loaded from: classes.dex */
public class QRCodeReader implements JavaCameraView.QRCodeFrameCallback {
    private Context context;
    private final String TAG = "QRCodeReader";
    private boolean isEnable = false;
    private int processingNum = 0;

    static /* synthetic */ int access$008(QRCodeReader x0) {
        int i = x0.processingNum;
        x0.processingNum = i + 1;
        return i;
    }

    static /* synthetic */ int access$010(QRCodeReader x0) {
        int i = x0.processingNum;
        x0.processingNum = i - 1;
        return i;
    }

    public QRCodeReader(Context context) {
        this.context = context;
    }

    public void enable() {
        this.isEnable = true;
    }

    public void disable() {
        this.isEnable = false;
    }

    public boolean isEnabled() {
        return this.isEnable;
    }

    public JavaCameraView.QRCodeFrameCallback getQRCodeFrameListener() {
        return this;
    }

    @Override // org.opencv.android.JavaCameraView.QRCodeFrameCallback
    public void onPreview(byte[] data, Camera camera) {
        if (this.isEnable) {
            Log.d("QRCodeReader", "ProcessingNum:" + String.valueOf(this.processingNum));
            if (this.processingNum < 2) {
                Log.d("QRCodeReader", "working");
                new QRCodeReadingTask(data, camera).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Object[0]);
            } else {
                Log.d("QRCodeReader", "drop qrcode frame");
            }
        }
    }

    public String zxingDecode(Bitmap bitmap) throws ChecksumException, FormatException {
        int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        LuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        Reader reader = new QRCodeMultiReader();
        try {
            Result result = reader.decode(binaryBitmap);
            Log.i("QRCodeReader", "Found something: " + result.getText());
            return result.getText();
        } catch (NotFoundException e) {
            Log.i("QRCodeReader", "Code Not Found");
            e.printStackTrace();
            return null;
        }
    }

    private class QRCodeReadingTask extends AsyncTask<Object, String, String> {
        private Camera camera;
        private byte[] data;

        public QRCodeReadingTask(byte[] data, Camera camera) {
            this.data = data;
            this.camera = camera;
        }

        @Override // android.os.AsyncTask
        protected void onPreExecute() {
            super.onPreExecute();
            QRCodeReader.access$008(QRCodeReader.this);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public String doInBackground(Object... params) {
            String result = null;
            long startTime = System.currentTimeMillis();
            if (QRCodeReader.this.isEnable) {
                Camera.Parameters parameters = this.camera.getParameters();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuvImage = new YuvImage(this.data, 17, parameters.getPreviewSize().width, parameters.getPreviewSize().height, null);
                yuvImage.compressToJpeg(new Rect(0, 0, parameters.getPreviewSize().width, parameters.getPreviewSize().height), 40, out);
                byte[] imageBytes = out.toByteArray();
                Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                try {
                    result = QRCodeReader.this.zxingDecode(image);
                } catch (ChecksumException e) {
                    e.printStackTrace();
                } catch (FormatException e2) {
                    e2.printStackTrace();
                }
            }
            long stopTime = System.currentTimeMillis();
            NumberFormat formatter = new DecimalFormat("#0.00000");
            Log.d("QRCodeReader", "Execution time is " + formatter.format((stopTime - startTime) / 1000.0d) + " seconds");
            return result;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(String result) {
            QRCodeReader.access$010(QRCodeReader.this);
            if (result != null && QRCodeReader.this.isEnable) {
                ModeController.getInstance(QRCodeReader.this.context).processQRCodeInPairMode(result);
            }
            super.onPostExecute(result);
        }
    }
}
