package com.bullb.r2d2_nanopisystem.ModeControl;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CentralController;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.LEDLightController;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class PatrolController {
    private Context context;
    private PatrolStateChangeListener patrolStateChangeListener;
    private PatrolTimerTask patrolTimerTask;
    private final int PATROL_TIME = 60000;
    private boolean isSleep = false;
    private Timer timer = new Timer();

    public interface PatrolStateChangeListener {
        void onPatrolStart();

        void onPatrolStop();
    }

    public PatrolController(Context context, PatrolStateChangeListener patrolStateChangeListener) {
        this.context = context;
        this.patrolStateChangeListener = patrolStateChangeListener;
    }

    /* JADX WARN: Type inference failed for: r0v6, types: [com.bullb.r2d2_nanopisystem.ModeControl.PatrolController$1] */
    public void startPatrol() {
        this.patrolStateChangeListener.onPatrolStart();
        if (this.patrolTimerTask != null) {
            this.patrolTimerTask.cancel();
        }
        this.patrolTimerTask = new PatrolTimerTask();
        this.timer.schedule(this.patrolTimerTask, 60000L);
        LEDLightController.getInstance(this.context).startPatrolLight();
        new AsyncTask<Object, Void, Void>() { // from class: com.bullb.r2d2_nanopisystem.ModeControl.PatrolController.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // android.os.AsyncTask
            public Void doInBackground(Object... params) {
                CentralController centralController = CentralController.getInstance(PatrolController.this.context);
                centralController.stopFaceDetection();
                centralController.stopVoiceRecognition();
                Log.d("Voice Recognizer", "stop by Patrol Controller");
                return null;
            }
        }.execute(new Object[0]);
    }

    /* JADX WARN: Type inference failed for: r0v3, types: [com.bullb.r2d2_nanopisystem.ModeControl.PatrolController$3] */
    public void stopPatrol() {
        this.patrolStateChangeListener.onPatrolStop();
        if (this.patrolTimerTask != null) {
            this.patrolTimerTask.cancel();
        }
        new Timer().schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.ModeControl.PatrolController.2
            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                LEDLightController.getInstance(PatrolController.this.context).stopPatrolLight();
            }
        }, 100L);
        new AsyncTask<Object, Void, Void>() { // from class: com.bullb.r2d2_nanopisystem.ModeControl.PatrolController.3
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // android.os.AsyncTask
            public Void doInBackground(Object... params) {
                CentralController centralController = CentralController.getInstance(PatrolController.this.context);
                centralController.startFaceDetection();
                centralController.startVoiceRecognition();
                return null;
            }
        }.execute(new Object[0]);
    }

    private class PatrolTimerTask extends TimerTask {
        private final String TAG;

        private PatrolTimerTask() {
            this.TAG = "PatrolTimerTask";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.i("PatrolTimerTask", "TaskTimer trigger");
            EventHandler.getInstance(PatrolController.this.context).stopJob();
        }
    }
}
