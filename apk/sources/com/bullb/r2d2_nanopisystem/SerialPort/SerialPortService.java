package com.bullb.r2d2_nanopisystem.SerialPort;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;

/* JADX INFO: loaded from: classes.dex */
public class SerialPortService extends Service {
    private static final int MSG_READ = 1;
    public static final String TAG = "SerialPort Service";
    private final int BUFSIZE = 512;
    private byte[] buf = new byte[512];
    private Handler handler = new Handler() { // from class: com.bullb.r2d2_nanopisystem.SerialPort.SerialPortService.2
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Bundle d = msg.getData();
                    String str = d.getString("string");
                    String[] lines = str.split("\\n");
                    for (String line : lines) {
                        if (line.length() > 2 && line.charAt(0) == '{') {
                            Intent intent = new Intent("serial_port_receiver");
                            intent.putExtra("msg", line);
                            LocalBroadcastManager.getInstance(SerialPortService.this.getApplicationContext()).sendBroadcast(intent);
                        }
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    };
    private SerialPort serialPort;
    private Thread thread;
    private Timer timer;

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() executed");
        this.timer = new Timer();
        this.serialPort = SerialPort.getInstance(getApplicationContext());
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.thread == null) {
            this.thread = new Thread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.SerialPort.SerialPortService.1
                @Override // java.lang.Runnable
                public void run() {
                    byte[] bArr = new byte[1024];
                    while (true) {
                        try {
                            InputStream is = SerialPortService.this.serialPort.getSerialInput();
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            Log.d(SerialPortService.TAG, "try read message");
                            while (true) {
                                int byteData = is.read();
                                if (byteData == 10) {
                                    break;
                                } else {
                                    out.write(byteData);
                                }
                            }
                            String str = out.toString();
                            Log.d(SerialPortService.TAG, "read: " + str);
                            Message message = new Message();
                            message.what = 1;
                            Bundle b = new Bundle();
                            b.putString("string", str);
                            message.setData(b);
                            SerialPortService.this.handler.sendMessage(message);
                        } catch (IOException | SecurityException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            Log.d(TAG, "start thread");
            this.thread.start();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() executed");
    }
}
