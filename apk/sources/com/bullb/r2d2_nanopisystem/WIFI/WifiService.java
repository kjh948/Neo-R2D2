package com.bullb.r2d2_nanopisystem.WIFI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.text.format.Formatter;
import android.util.Log;
import cc.mvdan.accesspoint.WifiApControl;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.LEDLightController;
import com.bullb.r2d2_nanopisystem.Model.Wifi;
import com.bullb.r2d2_nanopisystem.R;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.bullb.r2d2_nanopisystem.UDP.UDPBroadcastService;
import com.bullb.r2d2_nanopisystem.WebSocket.SocketServer;
import com.bullb.r2d2_nanopisystem.WebSocket.StreamingServer;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.bullb.r2d2_nanopisystem.utils.WIFIPreference;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class WifiService {
    public static final String AP_IP = "192.168.43.1";
    private static WifiService wifiService;
    private Timer apModeTimer;
    private Timer connectWifiTimer;
    private Context context;
    private boolean isAPMode;
    private NetworkInfo.DetailedState networkState;
    private SocketServer socketServer;
    private StreamingServer streamingServer;
    private UDPBroadcastService udpBroadcastService;
    private WifiManager wifiManager;
    public final int ERROR_UNSUPPORTED_NETWORK = 410;
    public final int ERROR_NETWORK_NOT_AUTHORIZED = 411;
    public final int ERROR_NETWORK_INVALID_CONFIG = 412;
    public final int ERROR_NETWORK_NOT_FOUND = 414;
    private final String TAG = "WIFI_Service";
    private ArrayList<WifiConnectionStateChangedListener> wifiConnectionStateListeners = new ArrayList<>();
    private boolean isProcessing = false;
    private BroadcastReceiver wifiStateChangeReceiver = new BroadcastReceiver() { // from class: com.bullb.r2d2_nanopisystem.WIFI.WifiService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiService.this.isAPMode) {
                Log.d("WIFI_Service", "Network State change is ap mode");
                return;
            }
            if (!action.equals("android.net.wifi.SCAN_RESULTS")) {
                if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                    int iTemp = intent.getIntExtra("wifi_state", 4);
                    Log.d("WIFI_Service", "+++++++-----------wifiStateReceiver------+++++++");
                    WifiService.this.checkState(iTemp);
                } else if (action.equals("android.net.wifi.supplicant.CONNECTION_CHANGE")) {
                    NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf((SupplicantState) intent.getParcelableExtra("connected"));
                    WifiService.this.changeState(state);
                    Log.d("WIFI_Service", "------------>>>>SUPPLICANT_STATE_CHANGED_ACTION<<<<<<-------");
                } else if (action.equals("android.net.wifi.STATE_CHANGE")) {
                    NetworkInfo.DetailedState state2 = ((NetworkInfo) intent.getParcelableExtra("networkInfo")).getDetailedState();
                    WifiService.this.changeState(state2);
                }
            }
        }
    };

    public interface WifiConnectionStateChangedListener {
        void onAuthenticating();

        void onConnected();

        void onConnecting();

        void onDisconnected();

        void onFailed();

        void onUnauthorized();
    }

    public static synchronized WifiService getInstance(Context context) {
        if (wifiService == null) {
            wifiService = new WifiService(context);
        }
        return wifiService;
    }

    private String getR2D2APSSID() {
        String name = RobotPreference.getRobotName(this.context);
        if (name == null) {
            return this.context.getString(R.string.ap_ssid);
        }
        String prefix = this.context.getString(R.string.ap_ssid) + " ";
        String name2 = prefix + name;
        byte[] b = name2.getBytes(StandardCharsets.UTF_8);
        new String(Arrays.copyOfRange(b, 0, 32), StandardCharsets.UTF_8);
        return name2;
    }

    private WifiService(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService("wifi");
        this.wifiManager.startScan();
        this.udpBroadcastService = UDPBroadcastService.getInstance(context);
        this.apModeTimer = new Timer();
        this.isAPMode = WIFIPreference.isAPMode(context);
        this.connectWifiTimer = new Timer();
        this.connectWifiTimer.schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.WIFI.WifiService.1
            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                if (!WifiService.this.isAPMode) {
                    if (WifiService.this.networkState == NetworkInfo.DetailedState.DISCONNECTED || WifiService.this.networkState == NetworkInfo.DetailedState.FAILED || WifiService.this.networkState == NetworkInfo.DetailedState.SCANNING || WifiService.this.networkState == null) {
                        WifiService.this.connectToSavedWifiSetting();
                    }
                }
            }
        }, 1000L, 3000L);
    }

    public void onMainActivityStart() {
        Log.d("WIFI_Service", "On Start");
        if (isAPMode()) {
            changeToAPMode();
        } else {
            changeToWifiMode();
        }
    }

    private void setWifiConnectionSetting(String ssid, String password) {
        WIFIPreference.setWifiSSID(this.context, ssid);
        WIFIPreference.setWifiPassword(this.context, password);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int connectToSavedWifiSetting() {
        String ssid = WIFIPreference.getWifiSID(this.context);
        String password = WIFIPreference.getWifiPassword(this.context);
        if (ssid == null) {
            ssid = "";
        }
        if (password == null) {
            password = "00000000";
        }
        PowerManager pm = (PowerManager) this.context.getApplicationContext().getSystemService(RobotApiHandler.POWER);
        boolean isScreenOn = pm.isScreenOn();
        Log.d("WIFI_Service", "screen on... " + isScreenOn);
        if (!isScreenOn) {
            PowerManager.WakeLock wl = pm.newWakeLock(805306394, "MyLock");
            wl.acquire(30000L);
            PowerManager.WakeLock wl_cpu = pm.newWakeLock(1, "MyCpuLock");
            wl_cpu.acquire(30000L);
        }
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + ssid + "\"";
        String cap = getNetworkCapabilities(ssid);
        if (cap == null) {
            Log.d("WIFI_Service", "No network found");
            return 414;
        }
        if (cap.contains("WPA")) {
            Log.d("WIFI_Service", "WPA");
            conf.preSharedKey = "\"" + password + "\"";
        } else if (cap.contains("WEP")) {
            Log.d("WIFI_Service", "WEP");
            conf.wepKeys[0] = "\"" + password + "\"";
            conf.wepTxKeyIndex = 0;
            conf.allowedKeyManagement.set(0);
            conf.allowedGroupCiphers.set(0);
        } else {
            Log.d("WIFI_Service", "Unsupported network");
            return 410;
        }
        forgotAllNetwork();
        this.wifiManager.disconnect();
        int netId = this.wifiManager.addNetwork(conf);
        if (netId == -1) {
            this.wifiManager.removeNetwork(netId);
            Log.d("WIFI_Service", "Failed to create network configuration");
            return 412;
        }
        this.wifiManager.enableNetwork(netId, false);
        this.wifiManager.reconnect();
        Log.i("WIFI_Service", "add new ");
        return -1;
    }

    public int connectWifi(String ssid, String password) {
        setWifiConnectionSetting(ssid, password);
        return isAPMode() ? changeToWifiMode() : connectToSavedWifiSetting();
    }

    public WifiInfo getCurrentNetworkWifiInfo() {
        ConnectivityManager cm = (ConnectivityManager) this.context.getSystemService("connectivity");
        NetworkInfo wifi = cm.getNetworkInfo(1);
        if (wifi.isConnected()) {
            return this.wifiManager.getConnectionInfo();
        }
        return null;
    }

    public String getNetworkCapabilities(String ssid) {
        List<ScanResult> list = this.wifiManager.getScanResults();
        for (ScanResult result : list) {
            if (result.SSID.equals(ssid)) {
                return result.capabilities;
            }
        }
        return null;
    }

    private void forgotAllNetwork() {
        for (WifiConfiguration config : this.wifiManager.getConfiguredNetworks()) {
            this.wifiManager.removeNetwork(config.networkId);
        }
    }

    public void apModeToggle() {
        Log.d("WIFI_Service", "apModeToggle()");
        if (!this.isProcessing) {
            if (this.isAPMode) {
                changeToWifiMode();
                return;
            } else {
                changeToAPMode();
                return;
            }
        }
        Log.d("WIFI_Service", "Drop ap mode toggle command");
    }

    private void changeToAPMode() {
        stopConnection();
        Log.d("WIFI_Service", "changing to ap mode");
        this.isProcessing = true;
        this.isAPMode = true;
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = getR2D2APSSID();
        wifiConfiguration.preSharedKey = "00000000";
        wifiConfiguration.hiddenSSID = false;
        wifiConfiguration.allowedAuthAlgorithms.set(0);
        wifiConfiguration.allowedProtocols.set(1);
        wifiConfiguration.allowedKeyManagement.set(4);
        wifiConfiguration.allowedPairwiseCiphers.set(2);
        wifiConfiguration.allowedGroupCiphers.set(3);
        WifiManager wifiManager = (WifiManager) this.context.getApplicationContext().getSystemService("wifi");
        WifiApControl apControl = WifiApControl.getInstance(this.context);
        wifiManager.setWifiEnabled(false);
        apControl.setEnabled(wifiConfiguration, true);
        this.apModeTimer.schedule(new APModeCheckingTask(), 0L, 1000L);
        EventHandler.getInstance(this.context).restoreLight();
        WIFIPreference.setIsAPMode(this.context, true);
    }

    public boolean isAPMode() {
        return this.isAPMode;
    }

    public boolean isAPModeConnecting() {
        return this.isProcessing;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int changeToWifiMode() {
        stopConnection();
        Log.d("WIFI_Service", "change to wifi mode");
        this.networkState = null;
        this.isAPMode = false;
        WifiApControl apControl = WifiApControl.getInstance(this.context);
        apControl.disable();
        this.wifiManager.setWifiEnabled(true);
        EventHandler.getInstance(this.context).restoreLight();
        WIFIPreference.setIsAPMode(this.context, false);
        return connectToSavedWifiSetting();
    }

    private class APModeCheckingTask extends TimerTask {
        private int counter;

        private APModeCheckingTask() {
            this.counter = 0;
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            WifiApControl apControl = WifiApControl.getInstance(WifiService.this.context);
            if (apControl.isEnabled()) {
                WifiService.this.isProcessing = false;
                Log.d("WIFI_Service", "AP Mode ENABLED");
                EventHandler.getInstance(WifiService.this.context).restoreLight();
                WifiService.this.startConnection();
                cancel();
            } else if (this.counter > 10) {
                Log.d("WIFI_Service", "Change AP Mode Fail");
                WifiService.this.isProcessing = false;
                WifiService.this.changeToWifiMode();
                cancel();
            }
            this.counter++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void changeState(NetworkInfo.DetailedState aState) {
        Log.d("WIFI_Service", ">>>>>>>>>>>>>>>>>>changeState<<<<<<<<<<<<<<<<" + aState);
        if (aState == NetworkInfo.DetailedState.SCANNING) {
            Log.d("WIFI_Service", "SCANNING");
        } else if (aState == NetworkInfo.DetailedState.CONNECTING) {
            Log.d("WIFI_Service", "CONNECTING");
            for (WifiConnectionStateChangedListener listener : this.wifiConnectionStateListeners) {
                listener.onConnecting();
            }
        } else if (aState == NetworkInfo.DetailedState.OBTAINING_IPADDR) {
            Log.d("WIFI_Service", "OBTAINING_IPADDR");
        } else if (aState == NetworkInfo.DetailedState.CONNECTED) {
            Log.d("WIFI_Service", "CONNECTED");
            if (this.networkState != NetworkInfo.DetailedState.CONNECTED) {
                startConnection();
                for (WifiConnectionStateChangedListener listener2 : this.wifiConnectionStateListeners) {
                    listener2.onConnected();
                }
            }
        } else if (aState == NetworkInfo.DetailedState.DISCONNECTING) {
            Log.d("WIFI_Service", "DISCONNECTING");
        } else if (aState == NetworkInfo.DetailedState.DISCONNECTED) {
            if (this.networkState == NetworkInfo.DetailedState.AUTHENTICATING) {
                Log.d("WIFI_Service", "UNAUTHORIZED");
                forgotAllNetwork();
                for (WifiConnectionStateChangedListener listener3 : this.wifiConnectionStateListeners) {
                    listener3.onUnauthorized();
                }
            } else {
                Log.d("WIFI_Service", "DISCONNECTED");
                stopConnection();
                for (WifiConnectionStateChangedListener listener4 : this.wifiConnectionStateListeners) {
                    listener4.onDisconnected();
                }
            }
        } else if (aState == NetworkInfo.DetailedState.FAILED) {
            Log.d("WIFI_Service", "FAILED");
            for (WifiConnectionStateChangedListener listener5 : this.wifiConnectionStateListeners) {
                listener5.onFailed();
            }
        } else if (aState == NetworkInfo.DetailedState.AUTHENTICATING) {
            Log.d("WIFI_Service", "AUTHENTICATING");
            for (WifiConnectionStateChangedListener listener6 : this.wifiConnectionStateListeners) {
                listener6.onAuthenticating();
            }
        }
        this.networkState = aState;
        LEDLightController.getInstance(this.context).restoreBackBaseMode();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startConnection() {
        this.udpBroadcastService.start();
        try {
            this.socketServer = SocketServer.getInstance(this.context);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        if (this.socketServer != null) {
            this.socketServer.startServer();
        }
        try {
            this.streamingServer = StreamingServer.getInstance(this.context);
        } catch (UnknownHostException e2) {
            e2.printStackTrace();
        }
        if (this.streamingServer != null) {
            this.streamingServer.startServer();
        }
    }

    private void stopConnection() {
        this.udpBroadcastService.stop();
        if (this.socketServer != null) {
            this.socketServer.stopServer();
        }
        if (this.streamingServer != null) {
            this.streamingServer.stopServer();
        }
    }

    public SocketServer getSocketServer() {
        return this.socketServer;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void checkState(int aInt) {
        Log.d("WIFI_Service", "==>>>>>>>>checkState<<<<<<<<" + aInt);
        if (aInt == 2) {
            Log.d("WifiManager", "WIFI_STATE_ENABLING");
            return;
        }
        if (aInt == 3) {
            Log.d("WifiManager", "WIFI_STATE_ENABLED");
        } else if (aInt == 0) {
            Log.d("WifiManager", "WIFI_STATE_DISABLING");
        } else if (aInt == 1) {
            Log.d("WifiManager", "WIFI_STATE_DISABLED");
        }
    }

    public String getCurrentNetworkSSID() {
        if (this.networkState != NetworkInfo.DetailedState.CONNECTED) {
            return null;
        }
        WifiInfo wifiInfo = this.wifiManager.getConnectionInfo();
        return wifiInfo.getSSID();
    }

    public String getCurrentNetworkSSIDCut() {
        if (this.networkState != NetworkInfo.DetailedState.CONNECTED) {
            return null;
        }
        if (isAPMode()) {
            return getR2D2APSSID();
        }
        WifiInfo wifiInfo = this.wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();
        return ssid.substring(1, ssid.length() - 1);
    }

    public String getIPAddress() {
        if (this.isAPMode) {
            return AP_IP;
        }
        WifiInfo wifiInfo = this.wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        return Formatter.formatIpAddress(ip);
    }

    public InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager) this.context.getSystemService("wifi");
        DhcpInfo dhcp = wifi.getDhcpInfo();
        Log.d("WIFI_Service", "Subnetmask:" + String.valueOf(dhcp.netmask));
        int broadcast = (dhcp.ipAddress & dhcp.netmask) | (dhcp.netmask ^ (-1));
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++) {
            quads[k] = (byte) (broadcast >> (k * 8));
        }
        return InetAddress.getByName("255.255.255.255");
    }

    public void addWifiConnectionStateChangeListener(WifiConnectionStateChangedListener listener) {
        if (listener != null) {
            this.wifiConnectionStateListeners.add(listener);
        }
    }

    public void removeWifiConnectionStateChangeListener(WifiConnectionStateChangedListener listener) {
        if (listener != null) {
            this.wifiConnectionStateListeners.remove(listener);
        }
    }

    public void startScan() {
        this.wifiManager.startScan();
    }

    public ArrayList<Wifi> getScanResult() {
        wifiService.startScan();
        WifiManager wifiManager = (WifiManager) this.context.getApplicationContext().getSystemService("wifi");
        List<ScanResult> list = wifiManager.getScanResults();
        return getScanResultsList(list);
    }

    public ArrayList<Wifi> getScanResultsList(List<ScanResult> scanResults) {
        ArrayList<Wifi> scannedWifiList = new ArrayList<>();
        if (scanResults != null) {
            for (ScanResult scanResult : scanResults) {
                int rssi = scanResult.level;
                scannedWifiList.add(new Wifi(scanResult.SSID, rssi));
            }
            Collections.sort(scannedWifiList);
        }
        return scannedWifiList;
    }

    public NetworkInfo.DetailedState getNetworkState() {
        return this.networkState;
    }

    public void start() {
        Log.d("WIFI_Service", "apMode:" + String.valueOf(this.isAPMode));
        if (this.isAPMode) {
            startConnection();
        }
        this.wifiManager.setWifiEnabled(true);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("android.net.wifi.SCAN_RESULTS");
        mIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        mIntentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        this.context.registerReceiver(this.wifiStateChangeReceiver, mIntentFilter);
    }

    public void stop() {
        this.context.unregisterReceiver(this.wifiStateChangeReceiver);
    }
}
