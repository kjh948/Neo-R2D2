package com.bullb.r2d2_nanopisystem.ModeControl;

import android.content.Context;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CentralController;
import com.bullb.r2d2_nanopisystem.LEDLightController;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class SleepController {
    private Context context;
    private SleepStateChangeListener sleepStateChangeListener;
    private final int SLEEP_TIME = 180000;
    private boolean isSleep = false;
    private Timer timer = new Timer();
    private SleepTimerTask sleepTimerTask = new SleepTimerTask();

    public interface SleepStateChangeListener {
        void onSleep();

        void onWake();
    }

    public SleepController(Context context, SleepStateChangeListener sleepStateChangeListener) {
        this.context = context;
        this.timer.schedule(this.sleepTimerTask, 180000L);
        this.sleepStateChangeListener = sleepStateChangeListener;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sleep() {
        Log.d("SleepController", "sleep now");
        this.isSleep = true;
        this.sleepStateChangeListener.onSleep();
        CentralController.getInstance(this.context).stopFaceDetection();
        Log.d("Voice Recognizer", "stop by sleep");
        CentralController.getInstance(this.context).stopVoiceRecognition();
    }

    public void restartSleepTimer() {
        Log.d("SleepController", "restartSleepTimer");
        if (this.sleepTimerTask != null) {
            this.sleepTimerTask.cancel();
        }
        this.sleepTimerTask = new SleepTimerTask();
        this.timer.schedule(this.sleepTimerTask, 180000L);
    }

    public void stopTimer() {
        if (this.sleepTimerTask != null) {
            this.sleepTimerTask.cancel();
        }
    }

    public void wake() {
        boolean beforeChange = this.isSleep;
        this.isSleep = false;
        restartSleepTimer();
        if (beforeChange) {
            this.sleepStateChangeListener.onWake();
            CentralController.getInstance(this.context).startFaceDetection();
            CentralController.getInstance(this.context).startVoiceRecognition();
            LEDLightController.getInstance(this.context).restoreAll();
        }
    }

    private class SleepTimerTask extends TimerTask {
        private final String TAG;

        private SleepTimerTask() {
            this.TAG = "SleepTimerTask";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.i("SleepTimerTask", "SleepTimerTask trigger");
            SleepController.this.sleep();
        }
    }
}
