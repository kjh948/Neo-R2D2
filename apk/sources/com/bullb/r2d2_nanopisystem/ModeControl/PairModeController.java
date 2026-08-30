package com.bullb.r2d2_nanopisystem.ModeControl;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CentralController;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.koushikdutta.async.http.AsyncHttpRequest;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class PairModeController {
    private Context context;
    private String pairKey;
    private PairStateChangeListener pairStateChangeListener;
    private StopPairingTask stopPairingTask;
    private WifiService.WifiConnectionStateChangedListener wifiConnectionStateChangedListener;
    private final String TAG = "PairModeController";
    private final int TIMEOUT = AsyncHttpRequest.DEFAULT_TIMEOUT;
    private boolean start = false;
    private boolean isConnectingWifi = false;
    private boolean isProcessing = false;
    private Timer pairTimer = new Timer();

    public interface PairStateChangeListener {
        void onPairStart();

        void onPairStop();
    }

    public PairModeController(Context context, PairStateChangeListener pairStateChangeListener) {
        this.context = context;
        this.pairStateChangeListener = pairStateChangeListener;
    }

    public void startPairMode() {
        if (this.isProcessing) {
            Log.d("PairModeController", "drop start command");
            return;
        }
        this.start = true;
        this.pairStateChangeListener.onPairStart();
        PairToggleTask pairToggleTask = new PairToggleTask(true);
        pairToggleTask.execute(new Object[0]);
    }

    public void stopPairMode() {
        if (this.isProcessing) {
            Log.d("PairModeController", "drop stop command");
            return;
        }
        Log.d("PairModeController", "start stopping pair mode...");
        this.pairStateChangeListener.onPairStop();
        this.pairKey = null;
        PairToggleTask pairToggleTask = new PairToggleTask(false);
        pairToggleTask.execute(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isProcessing() {
        return this.isProcessing;
    }

    private class StopPairingTask extends TimerTask {
        private final String TAG;

        private StopPairingTask() {
            this.TAG = "PairTimer";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.i("PairTimer", "Pair timer triggered, pair mode stop");
            ((Activity) PairModeController.this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.ModeControl.PairModeController.StopPairingTask.1
                @Override // java.lang.Runnable
                public void run() {
                    PairModeController.this.stopPairMode();
                }
            });
        }
    }

    public boolean isConnectingWifi() {
        return this.isConnectingWifi;
    }

    public void processQRCode(String qrCodeText) {
        Log.d("PairModeController", "processQRCode:" + qrCodeText);
        CentralController centralController = CentralController.getInstance(this.context);
        EventHandler eventHandler = EventHandler.getInstance(this.context);
        try {
            String[] dataList = qrCodeText.split("/a/");
            if (dataList.length != 3) {
                Log.i("PairModeController", "Invalid QRCode:wrong data size");
                eventHandler.failInPairMode();
            } else if (dataList[0].isEmpty()) {
                Log.i("PairModeController", "Invalid QRCode:wrong ssid");
                eventHandler.failInPairMode();
            } else if (dataList[1].isEmpty()) {
                Log.i("PairModeController", "Invalid QRCode:wrong pw");
                eventHandler.failInPairMode();
            } else if (dataList[2].isEmpty()) {
                Log.i("PairModeController", "Invalid QRCode:wrong randomNum");
                eventHandler.failInPairMode();
            }
            Log.d("PairModeController", "ssid:" + dataList[0] + "    pw:" + dataList[1] + "  key:" + dataList[2]);
            this.isConnectingWifi = true;
            eventHandler.startWifiConnectionEvent();
            centralController.stopQRCodeReader();
            if (this.stopPairingTask != null) {
                this.stopPairingTask.cancel();
            }
            this.pairKey = dataList[2];
            connectWifi(dataList[0], dataList[1]);
        } catch (Exception e) {
            eventHandler.failInPairMode();
        }
    }

    public void successConnectionEstablished() {
        EventHandler.getInstance(this.context).userGrantAccessEvent();
        stopPairMode();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void connectWifiFail() {
        CentralController centralController = CentralController.getInstance(this.context);
        EventHandler eventHandler = EventHandler.getInstance(this.context);
        this.isConnectingWifi = false;
        eventHandler.failInPairMode();
        this.stopPairingTask = new StopPairingTask();
        this.pairTimer.schedule(this.stopPairingTask, 30000L);
        centralController.startQRCodeReader();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void connectWifiSuccess() {
        CentralController centralController = CentralController.getInstance(this.context);
        EventHandler eventHandler = EventHandler.getInstance(this.context);
        this.isConnectingWifi = false;
        eventHandler.restoreLight();
        this.stopPairingTask = new StopPairingTask();
        this.pairTimer.schedule(this.stopPairingTask, 30000L);
        centralController.startQRCodeReader();
    }

    public String getPairKey() {
        return this.pairKey;
    }

    public void connectWifi(String ssid, String pw) {
        final WifiService wifiService = WifiService.getInstance(this.context);
        int result = wifiService.connectWifi(ssid, pw);
        if (result > 0) {
            connectWifiFail();
            return;
        }
        if (result == -2) {
            connectWifiSuccess();
            return;
        }
        final Timer wifiConnectTimer = new Timer();
        wifiConnectTimer.schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.ModeControl.PairModeController.1
            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                Log.i("ConnectWIFI", "Wifi Timer trigger");
                wifiService.removeWifiConnectionStateChangeListener(PairModeController.this.wifiConnectionStateChangedListener);
                if (PairModeController.this.start) {
                    PairModeController.this.connectWifiFail();
                }
            }
        }, 30000L);
        this.wifiConnectionStateChangedListener = new WifiService.WifiConnectionStateChangedListener() { // from class: com.bullb.r2d2_nanopisystem.ModeControl.PairModeController.2
            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onConnected() {
                PairModeController.this.connectWifiSuccess();
                wifiService.removeWifiConnectionStateChangeListener(this);
                wifiConnectTimer.cancel();
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onConnecting() {
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onDisconnected() {
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onAuthenticating() {
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onUnauthorized() {
                wifiService.removeWifiConnectionStateChangeListener(this);
                wifiConnectTimer.cancel();
                PairModeController.this.connectWifiFail();
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onFailed() {
            }
        };
        wifiService.addWifiConnectionStateChangeListener(this.wifiConnectionStateChangedListener);
    }

    private class PairToggleTask extends AsyncTask<Object, Void, Void> {
        private CentralController centralController;
        private boolean enable;

        public PairToggleTask(boolean enable) {
            this.enable = enable;
            this.centralController = CentralController.getInstance(PairModeController.this.context);
        }

        @Override // android.os.AsyncTask
        protected void onPreExecute() {
            super.onPreExecute();
            PairModeController.this.isProcessing = true;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Void doInBackground(Object... params) {
            if (this.enable) {
                long startTime = System.nanoTime();
                this.centralController.startQRCodeReader();
                this.centralController.stopFaceDetection();
                long stopTime = System.nanoTime();
                Log.d("PairModeController", "Start QRCode:" + String.valueOf(stopTime - startTime));
                return null;
            }
            Log.d("PairModeController", "stop in background");
            long startTime2 = System.nanoTime();
            Log.d("PairModeController", "start face detection");
            this.centralController.startFaceDetection();
            Log.d("PairModeController", "stop qrcode");
            this.centralController.stopQRCodeReader();
            long stopTime2 = System.nanoTime();
            Log.d("PairModeController", "Stop QRCode:" + String.valueOf(stopTime2 - startTime2));
            return null;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Void aVoid) {
            PairModeController.this.isProcessing = false;
            EventHandler eventHandler = EventHandler.getInstance(PairModeController.this.context);
            eventHandler.restoreLight();
            if (this.enable) {
                WifiService.getInstance(PairModeController.this.context).startScan();
                this.centralController.stopVoiceRecognition();
                if (PairModeController.this.stopPairingTask != null) {
                    PairModeController.this.stopPairingTask.cancel();
                }
                PairModeController.this.stopPairingTask = new StopPairingTask();
                PairModeController.this.pairTimer.schedule(PairModeController.this.stopPairingTask, 30000L);
                Log.d("PairModeController", "start pair mode");
            } else {
                if (PairModeController.this.stopPairingTask != null) {
                    PairModeController.this.stopPairingTask.cancel();
                }
                Log.d("PairModeController", "stop voice recognition");
                this.centralController.startVoiceRecognition();
                Log.d("PairModeController", "stop pair mode");
            }
            super.onPostExecute(aVoid);
        }
    }
}
