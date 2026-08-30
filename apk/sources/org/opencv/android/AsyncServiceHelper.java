package org.opencv.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import java.io.File;
import java.util.StringTokenizer;
import org.opencv.core.Core;
import org.opencv.engine.OpenCVEngineInterface;

/* JADX INFO: loaded from: classes.dex */
class AsyncServiceHelper {
    protected static final int MINIMUM_ENGINE_VERSION = 2;
    protected static final String OPEN_CV_SERVICE_URL = "market://details?id=org.opencv.engine";
    protected static final String TAG = "OpenCVManager/Helper";
    protected Context mAppContext;
    protected OpenCVEngineInterface mEngineService;
    protected String mOpenCVersion;
    protected ServiceConnection mServiceConnection = new ServiceConnection() { // from class: org.opencv.android.AsyncServiceHelper.3
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName className, IBinder service) {
            int status;
            Log.d(AsyncServiceHelper.TAG, "Service connection created");
            AsyncServiceHelper.this.mEngineService = OpenCVEngineInterface.Stub.asInterface(service);
            if (AsyncServiceHelper.this.mEngineService == null) {
                Log.d(AsyncServiceHelper.TAG, "OpenCV Manager Service connection fails. May be service was not installed?");
                AsyncServiceHelper.InstallService(AsyncServiceHelper.this.mAppContext, AsyncServiceHelper.this.mUserAppCallback);
                return;
            }
            AsyncServiceHelper.mServiceInstallationProgress = false;
            try {
                if (AsyncServiceHelper.this.mEngineService.getEngineVersion() < 2) {
                    Log.d(AsyncServiceHelper.TAG, "Init finished with status 4");
                    Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                    AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                    Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                    AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(4);
                    return;
                }
                Log.d(AsyncServiceHelper.TAG, "Trying to get library path");
                String path = AsyncServiceHelper.this.mEngineService.getLibPathByVersion(AsyncServiceHelper.this.mOpenCVersion);
                if (path == null || path.length() == 0) {
                    if (!AsyncServiceHelper.mLibraryInstallationProgress) {
                        InstallCallbackInterface InstallQuery = new InstallCallbackInterface() { // from class: org.opencv.android.AsyncServiceHelper.3.1
                            @Override // org.opencv.android.InstallCallbackInterface
                            public String getPackageName() {
                                return "OpenCV library";
                            }

                            @Override // org.opencv.android.InstallCallbackInterface
                            public void install() {
                                Log.d(AsyncServiceHelper.TAG, "Trying to install OpenCV lib via Google Play");
                                try {
                                    if (AsyncServiceHelper.this.mEngineService.installVersion(AsyncServiceHelper.this.mOpenCVersion)) {
                                        AsyncServiceHelper.mLibraryInstallationProgress = true;
                                        Log.d(AsyncServiceHelper.TAG, "Package installation started");
                                        Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                        AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                    } else {
                                        Log.d(AsyncServiceHelper.TAG, "OpenCV package was not installed!");
                                        Log.d(AsyncServiceHelper.TAG, "Init finished with status 2");
                                        Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                        AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                        Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                                        AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(2);
                                    }
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                    Log.d(AsyncServiceHelper.TAG, "Init finished with status 255");
                                    Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                    AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                    Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                                    AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(255);
                                }
                            }

                            @Override // org.opencv.android.InstallCallbackInterface
                            public void cancel() {
                                Log.d(AsyncServiceHelper.TAG, "OpenCV library installation was canceled");
                                Log.d(AsyncServiceHelper.TAG, "Init finished with status 3");
                                Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                                AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(3);
                            }

                            @Override // org.opencv.android.InstallCallbackInterface
                            public void wait_install() {
                                Log.e(AsyncServiceHelper.TAG, "Installation was not started! Nothing to wait!");
                            }
                        };
                        AsyncServiceHelper.this.mUserAppCallback.onPackageInstall(0, InstallQuery);
                        return;
                    } else {
                        InstallCallbackInterface WaitQuery = new InstallCallbackInterface() { // from class: org.opencv.android.AsyncServiceHelper.3.2
                            @Override // org.opencv.android.InstallCallbackInterface
                            public String getPackageName() {
                                return "OpenCV library";
                            }

                            @Override // org.opencv.android.InstallCallbackInterface
                            public void install() {
                                Log.e(AsyncServiceHelper.TAG, "Nothing to install we just wait current installation");
                            }

                            @Override // org.opencv.android.InstallCallbackInterface
                            public void cancel() {
                                Log.d(AsyncServiceHelper.TAG, "OpenCV library installation was canceled");
                                AsyncServiceHelper.mLibraryInstallationProgress = false;
                                Log.d(AsyncServiceHelper.TAG, "Init finished with status 3");
                                Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                                AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(3);
                            }

                            @Override // org.opencv.android.InstallCallbackInterface
                            public void wait_install() {
                                Log.d(AsyncServiceHelper.TAG, "Waiting for current installation");
                                try {
                                    if (!AsyncServiceHelper.this.mEngineService.installVersion(AsyncServiceHelper.this.mOpenCVersion)) {
                                        Log.d(AsyncServiceHelper.TAG, "OpenCV package was not installed!");
                                        Log.d(AsyncServiceHelper.TAG, "Init finished with status 2");
                                        Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                                        AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(2);
                                    } else {
                                        Log.d(AsyncServiceHelper.TAG, "Wating for package installation");
                                    }
                                    Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                    AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                    Log.d(AsyncServiceHelper.TAG, "Init finished with status 255");
                                    Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                                    AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                                    Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                                    AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(255);
                                }
                            }
                        };
                        AsyncServiceHelper.this.mUserAppCallback.onPackageInstall(1, WaitQuery);
                        return;
                    }
                }
                Log.d(AsyncServiceHelper.TAG, "Trying to get library list");
                AsyncServiceHelper.mLibraryInstallationProgress = false;
                String libs = AsyncServiceHelper.this.mEngineService.getLibraryList(AsyncServiceHelper.this.mOpenCVersion);
                Log.d(AsyncServiceHelper.TAG, "Library list: \"" + libs + "\"");
                Log.d(AsyncServiceHelper.TAG, "First attempt to load libs");
                if (AsyncServiceHelper.this.initOpenCVLibs(path, libs)) {
                    Log.d(AsyncServiceHelper.TAG, "First attempt to load libs is OK");
                    String eol = System.getProperty("line.separator");
                    for (String str : Core.getBuildInformation().split(eol)) {
                        Log.i(AsyncServiceHelper.TAG, str);
                    }
                    status = 0;
                } else {
                    Log.d(AsyncServiceHelper.TAG, "First attempt to load libs fails");
                    status = 255;
                }
                Log.d(AsyncServiceHelper.TAG, "Init finished with status " + status);
                Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(status);
            } catch (RemoteException e) {
                e.printStackTrace();
                Log.d(AsyncServiceHelper.TAG, "Init finished with status 255");
                Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                AsyncServiceHelper.this.mAppContext.unbindService(AsyncServiceHelper.this.mServiceConnection);
                Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                AsyncServiceHelper.this.mUserAppCallback.onManagerConnected(255);
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName className) {
            AsyncServiceHelper.this.mEngineService = null;
        }
    };
    protected LoaderCallbackInterface mUserAppCallback;
    protected static boolean mServiceInstallationProgress = false;
    protected static boolean mLibraryInstallationProgress = false;

    public static boolean initOpenCV(String Version, Context AppContext, LoaderCallbackInterface Callback) {
        AsyncServiceHelper helper = new AsyncServiceHelper(Version, AppContext, Callback);
        Intent intent = new Intent("org.opencv.engine.BIND");
        intent.setPackage("org.opencv.engine");
        if (AppContext.bindService(intent, helper.mServiceConnection, 1)) {
            return true;
        }
        AppContext.unbindService(helper.mServiceConnection);
        InstallService(AppContext, Callback);
        return false;
    }

    protected AsyncServiceHelper(String Version, Context AppContext, LoaderCallbackInterface Callback) {
        this.mOpenCVersion = Version;
        this.mUserAppCallback = Callback;
        this.mAppContext = AppContext;
    }

    protected static boolean InstallServiceQuiet(Context context) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(OPEN_CV_SERVICE_URL));
            intent.addFlags(268435456);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected static void InstallService(final Context AppContext, final LoaderCallbackInterface Callback) {
        if (!mServiceInstallationProgress) {
            Log.d(TAG, "Request new service installation");
            InstallCallbackInterface InstallQuery = new InstallCallbackInterface() { // from class: org.opencv.android.AsyncServiceHelper.1
                private LoaderCallbackInterface mUserAppCallback;

                {
                    this.mUserAppCallback = Callback;
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public String getPackageName() {
                    return "OpenCV Manager";
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public void install() {
                    Log.d(AsyncServiceHelper.TAG, "Trying to install OpenCV Manager via Google Play");
                    boolean result = AsyncServiceHelper.InstallServiceQuiet(AppContext);
                    if (result) {
                        AsyncServiceHelper.mServiceInstallationProgress = true;
                        Log.d(AsyncServiceHelper.TAG, "Package installation started");
                        return;
                    }
                    Log.d(AsyncServiceHelper.TAG, "OpenCV package was not installed!");
                    Log.d(AsyncServiceHelper.TAG, "Init finished with status 2");
                    Log.d(AsyncServiceHelper.TAG, "Unbind from service");
                    Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                    this.mUserAppCallback.onManagerConnected(2);
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public void cancel() {
                    Log.d(AsyncServiceHelper.TAG, "OpenCV library installation was canceled");
                    Log.d(AsyncServiceHelper.TAG, "Init finished with status 3");
                    Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                    this.mUserAppCallback.onManagerConnected(3);
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public void wait_install() {
                    Log.e(AsyncServiceHelper.TAG, "Instalation was not started! Nothing to wait!");
                }
            };
            Callback.onPackageInstall(0, InstallQuery);
        } else {
            Log.d(TAG, "Waiting current installation process");
            InstallCallbackInterface WaitQuery = new InstallCallbackInterface() { // from class: org.opencv.android.AsyncServiceHelper.2
                private LoaderCallbackInterface mUserAppCallback;

                {
                    this.mUserAppCallback = Callback;
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public String getPackageName() {
                    return "OpenCV Manager";
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public void install() {
                    Log.e(AsyncServiceHelper.TAG, "Nothing to install we just wait current installation");
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public void cancel() {
                    Log.d(AsyncServiceHelper.TAG, "Waiting for OpenCV canceled by user");
                    AsyncServiceHelper.mServiceInstallationProgress = false;
                    Log.d(AsyncServiceHelper.TAG, "Init finished with status 3");
                    Log.d(AsyncServiceHelper.TAG, "Calling using callback");
                    this.mUserAppCallback.onManagerConnected(3);
                }

                @Override // org.opencv.android.InstallCallbackInterface
                public void wait_install() {
                    AsyncServiceHelper.InstallServiceQuiet(AppContext);
                }
            };
            Callback.onPackageInstall(1, WaitQuery);
        }
    }

    private boolean loadLibrary(String AbsPath) {
        Log.d(TAG, "Trying to load library " + AbsPath);
        try {
            System.load(AbsPath);
            Log.d(TAG, "OpenCV libs init was ok!");
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.d(TAG, "Cannot load library \"" + AbsPath + "\"");
            e.printStackTrace();
            boolean result = true & false;
            return result;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean initOpenCVLibs(String Path, String Libs) {
        Log.d(TAG, "Trying to init OpenCV libs");
        if (Path != null && Path.length() != 0) {
            boolean result = true;
            if (Libs != null && Libs.length() != 0) {
                Log.d(TAG, "Trying to load libs by dependency list");
                StringTokenizer splitter = new StringTokenizer(Libs, ";");
                while (splitter.hasMoreTokens()) {
                    String AbsLibraryPath = Path + File.separator + splitter.nextToken();
                    result &= loadLibrary(AbsLibraryPath);
                }
                return result;
            }
            String AbsLibraryPath2 = Path + File.separator + "libopencv_java3.so";
            boolean result2 = true & loadLibrary(AbsLibraryPath2);
            return result2;
        }
        Log.d(TAG, "Library path \"" + Path + "\" is empty");
        return false;
    }
}
