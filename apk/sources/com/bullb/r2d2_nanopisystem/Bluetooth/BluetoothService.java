package com.bullb.r2d2_nanopisystem.Bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CommandReceiver;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/* JADX INFO: loaded from: classes.dex */
public class BluetoothService {
    public static final int STATE_LISTEN = 1;
    public static final int STATE_NONE = 0;
    public static final String TAG = "BlueTooth Service";
    private static BluetoothService bluetoothService;
    private final int BUFSIZE = 1024;
    private byte[] buffer = new byte[1024];
    private ArrayList<ConnectedThread> connectedThreads = new ArrayList<>();
    private Context context;
    private AcceptThread mAcceptThread;
    private BluetoothAdapter mBluetoothAdapter;
    private int mState;

    public static synchronized BluetoothService getInstance(Context context) {
        if (bluetoothService == null) {
            bluetoothService = new BluetoothService(context);
        }
        return bluetoothService;
    }

    private BluetoothService(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService("bluetooth");
        this.mBluetoothAdapter = bluetoothManager.getAdapter();
        this.mBluetoothAdapter.setName(RobotPreference.getRobotName(context));
        Log.d("b_mac", this.mBluetoothAdapter.getAddress());
        if (!this.mBluetoothAdapter.isEnabled()) {
            this.mBluetoothAdapter.enable();
        }
        try {
            Method method = this.mBluetoothAdapter.getClass().getMethod("setScanMode", Integer.TYPE, Integer.TYPE);
            method.invoke(this.mBluetoothAdapter, 23, 0);
            Log.e("invoke", "method invoke successfully");
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.mState = 0;
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.mState + " -> " + state);
        this.mState = state;
        String devices = "";
        for (ConnectedThread connectedThread : this.connectedThreads) {
            devices = devices + connectedThread.getSocket().getRemoteDevice().getAddress() + " ";
        }
        Log.d(TAG, "Bt Connected Device:" + devices);
    }

    public synchronized int getState() {
        return this.mState;
    }

    public synchronized void start() {
        Log.d(TAG, "start");
        if (this.connectedThreads.size() != 0) {
            for (ConnectedThread connectedThread : this.connectedThreads) {
                connectedThread.cancel();
            }
            this.connectedThreads.clear();
        }
        if (this.mAcceptThread == null) {
            this.mAcceptThread = new AcceptThread();
            this.mAcceptThread.start();
        }
        setState(1);
    }

    public synchronized void connected(BluetoothSocket socket) {
        Log.d(TAG, "connected");
        ConnectedThread connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        this.connectedThreads.add(connectedThread);
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");
        if (this.connectedThreads != null) {
            for (ConnectedThread connectedThread : this.connectedThreads) {
                connectedThread.cancel();
            }
            this.connectedThreads.clear();
        }
        if (this.mAcceptThread != null) {
            this.mAcceptThread.cancel();
            this.mAcceptThread = null;
        }
        setState(0);
    }

    public void write(byte[] out) {
        for (ConnectedThread connectedThread : this.connectedThreads) {
            synchronized (this) {
                if (this.connectedThreads.size() <= 0) {
                    return;
                }
            }
            connectedThread.write(out);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void connectionLost(ConnectedThread thread) {
        this.connectedThreads.remove(thread);
        Log.d(TAG, "connectionLost");
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = BluetoothService.this.mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("R2D2", UUID.fromString("c8bb5a21-d5ab-458b-ab9a-f5b0c64637ac"));
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "listen() failed", e);
                e.printStackTrace();
            }
            this.mmServerSocket = tmp;
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Log.d(BluetoothService.TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            while (this.mmServerSocket != null) {
                Log.d(BluetoothService.TAG, "accept listen");
                try {
                    BluetoothSocket socket = this.mmServerSocket.accept();
                    if (socket != null) {
                        Log.d(BluetoothService.TAG, "accept thread accepted");
                        synchronized (BluetoothService.this) {
                            if (isDeviceConnecting(socket.getRemoteDevice().getAddress())) {
                                try {
                                    Log.d(BluetoothService.TAG, "accept thread already exist");
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(BluetoothService.TAG, "socket already exist", e);
                                }
                            } else {
                                switch (BluetoothService.this.mState) {
                                    case 0:
                                        try {
                                            socket.close();
                                        } catch (IOException e2) {
                                            Log.e(BluetoothService.TAG, "Could not close unwanted socket", e2);
                                        }
                                        break;
                                    case 1:
                                        BluetoothService.this.connected(socket);
                                        break;
                                }
                            }
                        }
                    }
                } catch (IOException e3) {
                    Log.e(BluetoothService.TAG, "accept() failed", e3);
                }
            }
            Log.d("mmServerSocket", "is null");
            BluetoothService.this.mAcceptThread = null;
            BluetoothService.this.start();
            Log.i(BluetoothService.TAG, "END mAcceptThread");
        }

        private boolean isDeviceConnecting(String macAddress) {
            for (ConnectedThread thread : BluetoothService.this.connectedThreads) {
                if (thread.getSocket().getRemoteDevice().getAddress().equals(macAddress)) {
                    return true;
                }
            }
            return false;
        }

        public void cancel() {
            Log.d(BluetoothService.TAG, "cancel " + this);
            try {
                this.mmServerSocket.close();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "close() of server failed", e);
            }
        }

        private boolean isValidDevice(String macAddress) {
            return RobotPreference.getClientList(BluetoothService.this.context).contains(macAddress);
        }
    }

    public class ConnectedThread extends Thread {
        private CommandReceiver commandReceiver;
        private Timer establishConnectionTimer;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;
        private boolean isValidConnection = false;
        private int TIMER_TIMEOUT = 10000;
        private String clientUUID = null;
        private Handler handler = new Handler(Looper.getMainLooper()) { // from class: com.bullb.r2d2_nanopisystem.Bluetooth.BluetoothService.ConnectedThread.1
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        String data = "";
                        if (msg.getData() != null) {
                            data = msg.getData().getString("msg");
                        }
                        ConnectedThread.this.commandReceiver.interpretCommand(data);
                        break;
                }
                super.handleMessage(msg);
            }
        };

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(BluetoothService.TAG, "create ConnectedThread");
            this.mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            this.commandReceiver = new CommandReceiver(BluetoothService.this.context, this);
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "temp sockets not created", e);
            }
            this.mmInStream = tmpIn;
            this.mmOutStream = tmpOut;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Log.i(BluetoothService.TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            Log.d(BluetoothService.TAG, "connection timer start");
            this.establishConnectionTimer = new Timer();
            this.establishConnectionTimer.schedule(new StopBluetoothTimerTask(), this.TIMER_TIMEOUT);
            while (true) {
                try {
                    int bytes = this.mmInStream.read(buffer);
                    String input = new String(buffer, 0, bytes);
                    Log.d(BluetoothService.TAG, input);
                    Message msg = new Message();
                    msg.what = 1;
                    Bundle bundle = new Bundle();
                    bundle.putString("msg", input);
                    msg.setData(bundle);
                    this.handler.sendMessage(msg);
                } catch (IOException e) {
                    Log.e(BluetoothService.TAG, "disconnected", e);
                    BluetoothService.this.connectionLost(this);
                    return;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                this.mmOutStream.write(buffer);
                this.mmOutStream.flush();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            stopEstablishTimer();
            try {
                this.mmSocket.close();
            } catch (IOException e) {
                Log.e(BluetoothService.TAG, "close() of connect socket failed", e);
            }
        }

        public BluetoothSocket getSocket() {
            return this.mmSocket;
        }

        public void stopEstablishTimer() {
            if (this.establishConnectionTimer != null) {
                this.establishConnectionTimer.cancel();
            }
        }

        private class StopBluetoothTimerTask extends TimerTask {
            private final String TAG;

            private StopBluetoothTimerTask() {
                this.TAG = "StopBluetoothTimer";
            }

            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                Log.d("StopBluetoothTimer", "triggered");
                ConnectedThread.this.cancel();
                BluetoothService.this.connectedThreads.remove(ConnectedThread.this);
            }
        }

        public boolean isValidConnection() {
            return this.isValidConnection;
        }

        public void setValidConnection(boolean validConnection) {
            this.isValidConnection = validConnection;
        }

        public void validateConnection(String clientUUID) {
            setValidConnection(true);
            this.clientUUID = clientUUID;
        }
    }
}
