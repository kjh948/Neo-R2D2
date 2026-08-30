package cc.mvdan.accesspoint;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class WifiApControl {
    private static final String FALLBACK_DEVICE = "wlan0";
    public static final int STATE_DISABLED = 11;
    public static final int STATE_DISABLING = 10;
    public static final int STATE_ENABLED = 13;
    public static final int STATE_ENABLING = 12;
    public static final int STATE_FAILED = 14;
    private static final String TAG = "WifiApControl";
    public static final int WIFI_AP_STATE_DISABLED = 11;
    public static final int WIFI_AP_STATE_DISABLING = 10;
    public static final int WIFI_AP_STATE_ENABLED = 13;
    public static final int WIFI_AP_STATE_ENABLING = 12;
    public static final int WIFI_AP_STATE_FAILED = 14;
    private static Method getWifiApConfigurationMethod;
    private static Method getWifiApStateMethod;
    private static WifiApControl instance;
    private static Method isWifiApEnabledMethod;
    private static Method setWifiApEnabledMethod;
    private final String deviceName;
    private final WifiManager wm;

    public interface ReachableClientListener {
        void onComplete();

        void onReachableClient(Client client);
    }

    static {
        Method[] arr$ = WifiManager.class.getDeclaredMethods();
        for (Method method : arr$) {
            switch (method.getName()) {
                case "getWifiApConfiguration":
                    getWifiApConfigurationMethod = method;
                    break;
                case "getWifiApState":
                    getWifiApStateMethod = method;
                    break;
                case "isWifiApEnabled":
                    isWifiApEnabledMethod = method;
                    break;
                case "setWifiApEnabled":
                    setWifiApEnabledMethod = method;
                    break;
            }
        }
        instance = null;
    }

    private static boolean isSoftwareSupported() {
        return (getWifiApStateMethod == null || isWifiApEnabledMethod == null || setWifiApEnabledMethod == null || getWifiApConfigurationMethod == null) ? false : true;
    }

    private static boolean isHardwareSupported() {
        return true;
    }

    public static boolean isSupported() {
        return isSoftwareSupported() && isHardwareSupported();
    }

    private WifiApControl(Context context) {
        this.wm = (WifiManager) context.getSystemService("wifi");
        this.deviceName = getDeviceName(this.wm);
    }

    public static WifiApControl getInstance(Context context) {
        if (instance == null) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(context)) {
                Log.e(TAG, "6.0 or later, but haven't been granted WRITE_SETTINGS!");
                return null;
            }
            instance = new WifiApControl(context);
        }
        return instance;
    }

    @TargetApi(9)
    private static String getDeviceName(WifiManager wifiManager) {
        Enumeration<NetworkInterface> ifaces;
        if (Build.VERSION.SDK_INT < 9) {
            Log.w(TAG, "Older device - falling back to the default device name: wlan0");
            return FALLBACK_DEVICE;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            Log.w(TAG, "6.0 or later, unaccessible MAC - falling back to the default device name: wlan0");
            return FALLBACK_DEVICE;
        }
        String macString = wifiManager.getConnectionInfo().getMacAddress();
        if (macString == null) {
            Log.w(TAG, "MAC Address not found - Wi-Fi disabled? Falling back to the default device name: wlan0");
            return FALLBACK_DEVICE;
        }
        byte[] macBytes = macAddressToByteArray(macString);
        try {
            ifaces = NetworkInterface.getNetworkInterfaces();
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            byte[] hardwareAddress = iface.getHardwareAddress();
            if (hardwareAddress != null && Arrays.equals(macBytes, hardwareAddress)) {
                return iface.getName();
            }
            Log.w(TAG, "None found - falling back to the default device name: wlan0");
            return FALLBACK_DEVICE;
        }
        Log.w(TAG, "None found - falling back to the default device name: wlan0");
        return FALLBACK_DEVICE;
    }

    private static byte[] macAddressToByteArray(String macString) {
        String[] mac = macString.split("[:\\s-]");
        byte[] macAddress = new byte[6];
        for (int i = 0; i < mac.length; i++) {
            macAddress[i] = Integer.decode("0x" + mac[i]).byteValue();
        }
        return macAddress;
    }

    private static Object invokeQuietly(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "", e);
            return null;
        }
    }

    public boolean isWifiApEnabled() {
        Object result = invokeQuietly(isWifiApEnabledMethod, this.wm, new Object[0]);
        if (result == null) {
            return false;
        }
        return ((Boolean) result).booleanValue();
    }

    public boolean isEnabled() {
        return isWifiApEnabled();
    }

    public static int newStateNumber(int state) {
        if (state < 10) {
            return state + 10;
        }
        return state;
    }

    public int getWifiApState() {
        Object result = invokeQuietly(getWifiApStateMethod, this.wm, new Object[0]);
        if (result == null) {
            return -1;
        }
        return newStateNumber(((Integer) result).intValue());
    }

    public int getState() {
        return getWifiApState();
    }

    public WifiConfiguration getWifiApConfiguration() {
        Object result = invokeQuietly(getWifiApConfigurationMethod, this.wm, new Object[0]);
        if (result == null) {
            return null;
        }
        return (WifiConfiguration) result;
    }

    public WifiConfiguration getConfiguration() {
        return getWifiApConfiguration();
    }

    public boolean setWifiApEnabled(WifiConfiguration config, boolean enabled) {
        Object result = invokeQuietly(setWifiApEnabledMethod, this.wm, config, Boolean.valueOf(enabled));
        if (result == null) {
            return false;
        }
        return ((Boolean) result).booleanValue();
    }

    public boolean setEnabled(WifiConfiguration config, boolean enabled) {
        return setWifiApEnabled(config, enabled);
    }

    public boolean enable() {
        return setEnabled(getConfiguration(), true);
    }

    public boolean disable() {
        return setEnabled(null, false);
    }

    public Inet6Address getInet6Address() {
        if (isEnabled()) {
            return (Inet6Address) getInetAddress(Inet6Address.class);
        }
        return null;
    }

    public Inet4Address getInet4Address() {
        if (isEnabled()) {
            return (Inet4Address) getInetAddress(Inet4Address.class);
        }
        return null;
    }

    private <T extends InetAddress> T getInetAddress(Class<T> addressType) {
        Enumeration<NetworkInterface> ifaces;
        try {
            ifaces = NetworkInterface.getNetworkInterfaces();
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (iface.getName().equals(this.deviceName)) {
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addressType.isInstance(addr)) {
                        return addressType.cast(addr);
                    }
                    return null;
                }
            }
        }
        return null;
    }

    public static class Client {
        public String hwAddr;
        public String ipAddr;

        public Client(String ipAddr, String hwAddr) {
            this.ipAddr = ipAddr;
            this.hwAddr = hwAddr;
        }
    }

    public List<Client> getClients() throws Throwable {
        if (!isEnabled()) {
            return null;
        }
        List<Client> result = new ArrayList<>();
        Pattern macPattern = Pattern.compile("..:..:..:..:..:..");
        BufferedReader br = null;
        try {
            try {
                BufferedReader br2 = new BufferedReader(new FileReader("/proc/net/arp"));
                while (true) {
                    try {
                        String line = br2.readLine();
                        if (line == null) {
                            break;
                        }
                        String[] parts = line.split(" +");
                        if (parts.length >= 6) {
                            String ipAddr = parts[0];
                            String hwAddr = parts[3];
                            String device = parts[5];
                            if (device.equals(this.deviceName) && macPattern.matcher(parts[3]).find()) {
                                result.add(new Client(ipAddr, hwAddr));
                            }
                        }
                    } catch (IOException e) {
                        e = e;
                        br = br2;
                        Log.e(TAG, "", e);
                        if (br != null) {
                            try {
                                br.close();
                            } catch (IOException e2) {
                                Log.e(TAG, "", e2);
                            }
                        }
                    } catch (Throwable th) {
                        th = th;
                        br = br2;
                        if (br != null) {
                            try {
                                br.close();
                            } catch (IOException e3) {
                                Log.e(TAG, "", e3);
                            }
                        }
                        throw th;
                    }
                }
                if (br2 != null) {
                    try {
                        br2.close();
                        br = br2;
                    } catch (IOException e4) {
                        Log.e(TAG, "", e4);
                        br = br2;
                    }
                } else {
                    br = br2;
                }
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (IOException e5) {
            e = e5;
        }
        return result;
    }

    /* JADX WARN: Type inference failed for: r0v2, types: [cc.mvdan.accesspoint.WifiApControl$2] */
    public List<Client> getReachableClients(final int timeout, final ReachableClientListener listener) throws Throwable {
        List<Client> clients = getClients();
        if (clients == null) {
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(clients.size());
        ExecutorService es = Executors.newCachedThreadPool();
        for (final Client c : clients) {
            es.submit(new Runnable() { // from class: cc.mvdan.accesspoint.WifiApControl.1
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        InetAddress ip = InetAddress.getByName(c.ipAddr);
                        if (ip.isReachable(timeout)) {
                            listener.onReachableClient(c);
                        }
                    } catch (IOException e) {
                        Log.e(WifiApControl.TAG, "", e);
                    }
                    latch.countDown();
                }
            });
        }
        new Thread() { // from class: cc.mvdan.accesspoint.WifiApControl.2
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e(WifiApControl.TAG, "", e);
                }
                listener.onComplete();
            }
        }.start();
        return clients;
    }
}
