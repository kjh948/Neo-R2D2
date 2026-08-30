package com.bullb.r2d2_nanopisystem.SerialPort;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/* JADX INFO: loaded from: classes.dex */
public class SerialPort {
    private static SerialPort serialPort;
    private Context context;
    private InputStream mSerialInput;
    private OutputStream mSerialOutput;
    private SerialPortOrangePi mSerialPort;
    private SerialPortSendCallback serialPortSendCallback;
    private String TAG = "SerialPortOrangePi";
    private boolean serviceStart = false;
    private String devName = "/dev/ttyS2";
    private int speed = 115200;
    private int dataBits = 8;
    private int stopBits = 1;

    public interface SerialPortSendCallback {
        void sentCallBack(boolean z, String str);
    }

    public InputStream getSerialInput() throws IOException, SecurityException {
        open();
        return this.mSerialInput;
    }

    public OutputStream getSerialOutput() throws IOException, SecurityException {
        open();
        return this.mSerialOutput;
    }

    public static synchronized SerialPort getInstance(Context context) {
        if (serialPort == null) {
            serialPort = new SerialPort(context);
        }
        return serialPort;
    }

    private SerialPort(Context context) {
        this.context = context;
        try {
            open();
        } catch (IOException | SecurityException ex) {
            Log.d(this.TAG, "Fail to open" + this.devName + "!, Exception: " + ex.getMessage());
        }
    }

    public void open() throws IOException, SecurityException {
        if (this.mSerialPort == null) {
            this.mSerialPort = new SerialPortOrangePi(new File(this.devName), this.speed);
            this.mSerialInput = this.mSerialPort.getInputStream();
            this.mSerialOutput = this.mSerialPort.getOutputStream();
        }
    }

    public boolean send(String command) {
        boolean successfulSend = false;
        try {
            open();
            if (command != null && command.length() > 0 && isDevValid()) {
                if (command.charAt(command.length() - 1) != '\n') {
                    command = command + "\n";
                }
                this.mSerialOutput.write(command.getBytes());
                successfulSend = true;
                Log.d("SERIAL_SEND_SUCCESS", command);
            }
            successfulSend = true;
        } catch (IOException | SecurityException ex) {
            Log.d("SERIAL_SEND_FAIL", command + ", Exception: " + ex.getMessage());
        }
        this.serialPortSendCallback.sentCallBack(successfulSend, command);
        Log.d("SERIAL_SEND", command);
        return successfulSend;
    }

    public void startService() {
        if (isDevValid() && !this.serviceStart) {
            Intent startIntent = new Intent(this.context, (Class<?>) SerialPortService.class);
            this.context.startService(startIntent);
            this.serviceStart = true;
        }
    }

    public void stopService() {
        if (isDevValid() && this.serviceStart) {
            Intent stopIntent = new Intent(this.context, (Class<?>) SerialPortService.class);
            this.context.stopService(stopIntent);
            this.serviceStart = false;
        }
    }

    private boolean isDevValid() {
        if (this.mSerialPort != null) {
            return true;
        }
        Log.d(this.TAG, "Fail to open " + this.devName + "!");
        return false;
    }

    public void setSerialPortSendCallback(SerialPortSendCallback serialPortSendCallback) {
        this.serialPortSendCallback = serialPortSendCallback;
    }
}
