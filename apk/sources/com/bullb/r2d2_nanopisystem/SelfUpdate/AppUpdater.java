package com.bullb.r2d2_nanopisystem.SelfUpdate;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.utils.AsyncTaskCallback;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.bullb.r2d2_nanopisystem.utils.SharedUtils;
import java.io.File;

/* JADX INFO: loaded from: classes.dex */
public class AppUpdater {
    static final int ESTIMATED_APP_SIZE = 83886080;
    static final String TAG = "AppUpdate";
    private static AppUpdater instance;
    private Context context;
    DownloadTask t;

    public static AppUpdater getInstance(Context context) {
        if (instance == null) {
            synchronized (AppUpdater.class) {
                if (instance == null) {
                    instance = new AppUpdater(context);
                }
            }
        }
        return instance;
    }

    private AppUpdater(Context context) {
        this.context = context;
        RobotPreference.setRobotSelfUpdate(context, 0);
    }

    public void installAPK(File file) {
        if (file.exists()) {
            try {
                RobotPreference.setRobotSelfUpdate(this.context, 2);
                SharedUtils.notifyRobotChanged(this.context);
                String command = "(pm install -r " + file.getAbsolutePath() + " && am start -n com.bullb.r2d2_nanopisystem/com.bullb.r2d2_nanopisystem.MainActivity) &";
                Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                proc.waitFor();
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        Log.d(TAG, "file " + file.getAbsolutePath() + " does not exists");
    }

    public void updateAPK(String url) {
        final File apkFile = new File("/sdcard/update.apk");
        if (this.t != null) {
            this.t.cancel(true);
        }
        RobotPreference.setRobotSelfUpdate(this.context, 1);
        RobotPreference.setRobotUpdateDownloadProgress(this.context, 0);
        SharedUtils.notifyRobotChanged(this.context);
        this.t = downloadAPK(url, apkFile, new AsyncTaskCallback() { // from class: com.bullb.r2d2_nanopisystem.SelfUpdate.AppUpdater.1
            @Override // com.bullb.r2d2_nanopisystem.utils.AsyncTaskCallback
            public void onProgress(int percentage) {
                RobotPreference.setRobotSelfUpdate(AppUpdater.this.context, 1);
                RobotPreference.setRobotUpdateDownloadProgress(AppUpdater.this.context, percentage);
                SharedUtils.notifyRobotChanged(AppUpdater.this.context);
            }

            @Override // com.bullb.r2d2_nanopisystem.utils.AsyncTaskCallback
            public void onTaskComplete(String result) {
                Log.d(AppUpdater.TAG, "result:" + result);
                AppUpdater.this.installAPK(apkFile);
            }
        });
    }

    public static DownloadTask downloadAPK(String url, File destination, @Nullable AsyncTaskCallback callback) {
        return (DownloadTask) new DownloadTask(url, destination, callback).execute(new String[0]);
    }

    private static class DownloadTask extends AsyncTask<String, Integer, String> {
        AsyncTaskCallback callback;
        File destination;
        long lastPublish = 0;
        String url;

        public DownloadTask(String url, File destination, AsyncTaskCallback callback) {
            this.url = url;
            this.destination = destination;
            this.callback = callback;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        /* JADX WARN: Code restructure failed: missing block: B:23:0x0083, code lost:
        
            r7.close();
         */
        /* JADX WARN: Code restructure failed: missing block: B:24:0x0086, code lost:
        
            r13 = null;
         */
        /* JADX WARN: Code restructure failed: missing block: B:25:0x0087, code lost:
        
            if (r9 == null) goto L27;
         */
        /* JADX WARN: Code restructure failed: missing block: B:26:0x0089, code lost:
        
            r9.close();
         */
        /* JADX WARN: Code restructure failed: missing block: B:27:0x008c, code lost:
        
            if (r7 == null) goto L29;
         */
        /* JADX WARN: Code restructure failed: missing block: B:28:0x008e, code lost:
        
            r7.close();
         */
        /* JADX WARN: Code restructure failed: missing block: B:29:0x0091, code lost:
        
            if (r2 != null) goto L30;
         */
        /* JADX WARN: Code restructure failed: missing block: B:30:0x0093, code lost:
        
            r2.disconnect();
         */
        /* JADX WARN: Code restructure failed: missing block: B:31:0x0096, code lost:
        
            r8 = r9;
         */
        /* JADX WARN: Removed duplicated region for block: B:100:? A[SYNTHETIC] */
        /* JADX WARN: Removed duplicated region for block: B:46:0x00ce  */
        /* JADX WARN: Removed duplicated region for block: B:57:0x00f1  */
        @Override // android.os.AsyncTask
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public java.lang.String doInBackground(java.lang.String... r21) throws java.lang.Throwable {
            /*
                Method dump skipped, instruction units count: 280
                To view this dump change 'Code comments level' option to 'DEBUG'
            */
            throw new UnsupportedOperationException("Method not decompiled: com.bullb.r2d2_nanopisystem.SelfUpdate.AppUpdater.DownloadTask.doInBackground(java.lang.String[]):java.lang.String");
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onProgressUpdate(Integer... values) {
            super.onProgressUpdate((Object[]) values);
            long now = System.currentTimeMillis();
            if (now - this.lastPublish >= 1000) {
                this.lastPublish = now;
                Log.d(AppUpdater.TAG, "onProgressUpdate: " + values[0]);
                int percentage = values[0].intValue();
                int percentage2 = Math.max(Math.min(percentage, 100), 0);
                if (this.callback != null) {
                    this.callback.onProgress(percentage2);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(String s) {
            super.onPostExecute(s);
            if (this.callback != null) {
                this.callback.onTaskComplete(s);
            }
        }
    }
}
