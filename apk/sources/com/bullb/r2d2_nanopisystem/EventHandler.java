package com.bullb.r2d2_nanopisystem;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.Model.EventJob.EventJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.HandJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.LCDJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.LEDJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.LightsaberJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.ModeJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.MoveHeadDirJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.MoveHeadJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.MoveJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.PatrolJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.ProjectorJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.ShiftHeadJob;
import com.bullb.r2d2_nanopisystem.Model.EventJob.SoundJob;
import com.bullb.r2d2_nanopisystem.SerialPort.SerialPort;
import com.bullb.r2d2_nanopisystem.Sound.SoundPlayer;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.bullb.r2d2_nanopisystem.utils.SharedUtils;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class EventHandler {
    private static EventHandler eventHandler;
    private Commander commander;
    private Context context;
    private LEDLightController ledLightController;
    private Timer moveTimer;
    private Timer readyTimer;
    private Runnable runnable;
    private SoundPlayer soundPlayer;
    private Timer stopMoveScheduleTimer;
    private Handler handler = new Handler();
    private boolean isBusy = false;
    private ArrayList<EventJob> jobQueue = new ArrayList<>();
    private final String TAG = "EventHandler";
    private boolean moveScheduleStart = false;

    public interface ProjectorFinishCallback {
        void finishCallBack();
    }

    public static synchronized EventHandler getInstance(Context context) {
        if (eventHandler == null) {
            eventHandler = new EventHandler(context);
        }
        return eventHandler;
    }

    private EventHandler(Context context) {
        this.context = context;
        this.soundPlayer = SoundPlayer.getInstance(context);
        Commander commander = this.commander;
        this.commander = Commander.getInstance(context);
        this.ledLightController = LEDLightController.getInstance(context);
        this.moveTimer = new Timer();
        this.stopMoveScheduleTimer = new Timer();
    }

    public boolean isBusy() {
        return this.isBusy;
    }

    public void softwareReady() {
        this.ledLightController.changeToReadyLight();
        this.commander.softwareReady();
    }

    public class MovingTimerTask extends TimerTask {
        int position = 0;

        public MovingTimerTask() {
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            switch (this.position) {
                case 0:
                    this.position = 1;
                    EventHandler.this.playSound(1, true);
                    break;
                case 1:
                    this.position = 2;
                    break;
                case 2:
                    this.position = 3;
                    EventHandler.this.playSound(5, true);
                    break;
                case 3:
                    this.position = 0;
                    break;
            }
        }
    }

    public class StopMoveSchduleTimer extends TimerTask {
        public StopMoveSchduleTimer() {
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            EventHandler.this.cancelMoveSchedule();
        }
    }

    public void cancelReady() {
        this.readyTimer.cancel();
    }

    public void cancelMoveSchedule() {
        this.moveTimer.cancel();
        this.moveScheduleStart = false;
    }

    public void makeSomeNoise() {
        playSound(6, true);
    }

    public void shakeYourHead() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new MoveHeadJob(-45, 600));
        this.jobQueue.add(new MoveHeadJob(45, 600));
        this.jobQueue.add(new MoveHeadJob(-45, 600));
        this.jobQueue.add(new MoveHeadJob(45, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        startJob();
    }

    public void voiceWakeUp() {
        this.ledLightController.restoreAll();
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(8, 0));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        startJob();
    }

    public void endVoice() {
        this.ledLightController.restoreAll();
    }

    public void dance() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(1, 0));
        this.jobQueue.add(new ModeJob(3, 0));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new ModeJob(3, 600));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new SoundJob(1, 0));
        this.jobQueue.add(new ModeJob(3, 0));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new ModeJob(3, 600));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new SoundJob(1, 0));
        this.jobQueue.add(new ModeJob(3, 0));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new ModeJob(3, 600));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new SoundJob(1, 0));
        this.jobQueue.add(new ModeJob(3, 0));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new ModeJob(3, 600));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        this.jobQueue.add(new ModeJob(2, 600));
        startJob();
    }

    public void powerOff() {
        this.ledLightController.powerOffLight();
        boolean success = this.commander.powerOff();
        if (success) {
            Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
            intent.setFlags(268435456);
            this.context.startActivity(intent);
        }
    }

    public void turnAround() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new ModeJob(2, 700));
        startJob();
    }

    public void whoAreYou() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(0, 0));
        this.jobQueue.add(new MoveHeadJob(-40, 600));
        this.jobQueue.add(new MoveHeadJob(40, 600));
        this.jobQueue.add(new MoveHeadJob(0, 600));
        startJob();
    }

    public void turnLeft() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new ModeJob(3, 700));
        startJob();
    }

    public void turnRight() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new ModeJob(4, 700));
        startJob();
    }

    public void goForward() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new ModeJob(5, 700));
        startJob();
    }

    public void flashFontLCD() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new ModeJob(13, 0));
        startJob();
    }

    public void modeStop() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new ModeJob(0, 0));
        startJob();
    }

    public void angleSecret() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(301, 0));
        startJob();
    }

    public void starkSecret() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(302, 0));
        startJob();
    }

    public void flashBackLCD() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new ModeJob(14, 0));
        startJob();
    }

    public void notRecognize() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(12, 0));
        startJob();
    }

    public void move(int power, int angle) {
        stopJob();
        this.jobQueue.clear();
        Log.d("EventHandler", String.valueOf(power));
        this.jobQueue.add(new MoveJob(power, angle, 0));
        startJob();
        if (angle == 0 || angle == 180) {
            if (!this.moveScheduleStart) {
                startMoveSchedule();
            }
            this.stopMoveScheduleTimer.cancel();
            this.stopMoveScheduleTimer = new Timer();
            this.stopMoveScheduleTimer.schedule(new StopMoveSchduleTimer(), 1000L);
        }
    }

    private void startMoveSchedule() {
        this.moveScheduleStart = true;
        this.moveTimer.cancel();
        this.moveTimer = new Timer();
        this.moveTimer.schedule(new MovingTimerTask(), 2000L, 2000L);
    }

    public void moveHead(int angle) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new MoveHeadJob(angle, 0));
        startJob();
    }

    public void shiftHead(int angle) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new MoveHeadJob(angle, 0));
        startJob();
    }

    public void moveHeadDir(int dir) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new MoveHeadDirJob(dir, 0));
        startJob();
    }

    public void walkCircle() {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new ModeJob(12, 700));
        startJob();
    }

    public void resetMCU() {
        SerialPort.getInstance(this.context).send("''");
    }

    public void lightsaber(int power) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new ProjectorJob(0, 0));
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new LightsaberJob(power, 0));
        startJob();
    }

    public void lightsaber() {
        if (RobotPreference.getLightsaber(this.context)) {
            lightsaber(0);
        } else {
            lightsaber(1);
        }
    }

    public void arm(int power) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new ProjectorJob(0, 0));
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new HandJob(power, 0));
        startJob();
    }

    public void arm() {
        if (RobotPreference.getRobotArm(this.context)) {
            arm(0);
        } else {
            arm(1);
        }
    }

    public void projectorMode(int mode) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new ProjectorJob(mode, 0));
        startJob();
    }

    public void projector1() {
        stopJob();
        this.jobQueue.clear();
        if (RobotPreference.getRobotProjector(this.context) != 1) {
            this.jobQueue.add(new ProjectorJob(1, 0));
        } else {
            this.jobQueue.add(new ProjectorJob(0, 0));
        }
        startJob();
    }

    public void projector2() {
        stopJob();
        this.jobQueue.clear();
        if (RobotPreference.getRobotProjector(this.context) != 2) {
            this.jobQueue.add(new ProjectorJob(2, 0));
        } else {
            this.jobQueue.add(new ProjectorJob(0, 0));
        }
        startJob();
    }

    public void LED(int r, int b, int y, int g) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new LEDJob(r, b, y, g, 0));
        startJob();
    }

    public void LCD(int s, int l) {
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new LCDJob(s, l, 0));
        startJob();
    }

    public void gotoSleep() {
        this.ledLightController.startSleepLight();
    }

    public void shortLCD() {
        stopJob();
        this.jobQueue.clear();
        int l = 1;
        int s = 1;
        if (!RobotPreference.getRobotShortLCD(this.context)) {
            s = 2;
        }
        if (RobotPreference.getRobotLongLCD(this.context)) {
            l = 2;
        }
        this.jobQueue.add(new ProjectorJob(0, 0));
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new LCDJob(s, l, 0));
        startJob();
    }

    public void longLCD() {
        stopJob();
        this.jobQueue.clear();
        int l = 1;
        int s = 1;
        if (RobotPreference.getRobotShortLCD(this.context)) {
            s = 2;
        }
        if (!RobotPreference.getRobotLongLCD(this.context)) {
            l = 2;
        }
        this.jobQueue.add(new ProjectorJob(0, 0));
        this.jobQueue.add(new SoundJob(7, 0));
        this.jobQueue.add(new LCDJob(s, l, 0));
        startJob();
    }

    public void patrol() {
        int currentMode = ModeController.getInstance(this.context).getMode();
        stopJob();
        this.jobQueue.clear();
        this.jobQueue.add(new ProjectorJob(0, 0));
        this.jobQueue.add(new SoundJob(7, 0));
        if (currentMode == 4) {
            this.jobQueue.add(new PatrolJob(false, 0));
        } else {
            this.jobQueue.add(new PatrolJob(true, 0));
        }
        startJob();
    }

    public void reset() {
        this.commander.reset();
    }

    public void changeHeadDirPower(int power) {
        this.commander.changeHeadPower(power);
    }

    public void changeLegPower(int power) {
        this.commander.changeRobotLegPower(power);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startJob() {
        this.isBusy = true;
        if (this.jobQueue == null || this.jobQueue.size() == 0) {
            this.isBusy = false;
            return;
        }
        final EventJob eventJob = this.jobQueue.get(0);
        this.runnable = new Runnable() { // from class: com.bullb.r2d2_nanopisystem.EventHandler.1
            @Override // java.lang.Runnable
            public void run() {
                EventHandler.this.executeJob(eventJob);
                EventHandler.this.jobQueue.remove(eventJob);
                EventHandler.this.startJob();
            }
        };
        this.handler.postDelayed(this.runnable, eventJob.getDelay());
    }

    public void stopJob() {
        if (this.handler != null && this.runnable != null) {
            this.handler.removeCallbacks(this.runnable);
        }
        this.isBusy = false;
        this.commander.mode(0);
        if (ModeController.getInstance(this.context).getMode() == 4) {
            ModeController.getInstance(this.context).stopPatrolMode();
        }
    }

    public void mode(int mode) {
        Log.d("EventHandler", String.valueOf(mode));
        try {
            switch (mode) {
                case 0:
                    modeStop();
                    break;
                case 1:
                    voiceWakeUp();
                    break;
                case 2:
                    turnAround();
                    break;
                case 3:
                    turnLeft();
                    break;
                case 4:
                    turnRight();
                    break;
                case 5:
                    goForward();
                    break;
                case 6:
                    lightsaber();
                    break;
                case 7:
                    whoAreYou();
                    break;
                case 8:
                case 11:
                default:
                    notRecognize();
                    break;
                case 9:
                    patrol();
                    break;
                case 10:
                    dance();
                    break;
                case 12:
                    walkCircle();
                    break;
                case 13:
                    flashFontLCD();
                    break;
                case 14:
                    flashBackLCD();
                    break;
                case 15:
                    shakeYourHead();
                    break;
                case 16:
                    arm();
                    break;
                case 17:
                    shortLCD();
                    break;
                case 18:
                    longLCD();
                    break;
                case 19:
                    projector1();
                    break;
                case 20:
                    projector2();
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void restoreLight() {
        this.ledLightController.restoreAll();
    }

    public void failInPairMode() {
        this.soundPlayer.play(3, false);
        this.ledLightController.failInPairMode();
    }

    public void startWifiConnectionEvent() {
        this.soundPlayer.play(7, false);
        this.ledLightController.connectWIFIMode();
    }

    public void userGrantAccessEvent() {
        this.soundPlayer.play(9, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void executeJob(EventJob eventJob) {
        boolean isSuccess;
        ModeController.getInstance(this.context).wake();
        try {
            if (eventJob instanceof MoveJob) {
                this.commander.move(((MoveJob) eventJob).getPower(), ((MoveJob) eventJob).getAngle());
                return;
            }
            if (eventJob instanceof MoveHeadJob) {
                this.commander.moveHeadAngle(((MoveHeadJob) eventJob).getAngle());
                return;
            }
            if (eventJob instanceof ShiftHeadJob) {
                this.commander.headShift(((ShiftHeadJob) eventJob).getAngle());
                return;
            }
            if (eventJob instanceof MoveHeadDirJob) {
                this.commander.moveHeadDirection(((MoveHeadDirJob) eventJob).getDir());
                return;
            }
            if (eventJob instanceof HandJob) {
                boolean isSuccess2 = this.commander.extendArm(((HandJob) eventJob).getPower());
                if (isSuccess2) {
                    if (((HandJob) eventJob).getPower() == 0) {
                        RobotPreference.setRobotArm(this.context, false);
                    } else {
                        RobotPreference.setRobotArm(this.context, true);
                    }
                    SharedUtils.notifyRobotChanged(this.context);
                    return;
                }
                return;
            }
            if (eventJob instanceof LightsaberJob) {
                boolean isSuccess3 = this.commander.lightsaber(((LightsaberJob) eventJob).getPower());
                if (isSuccess3) {
                    if (((LightsaberJob) eventJob).getPower() == 0) {
                        RobotPreference.setRobotLightsaber(this.context, false);
                    } else {
                        RobotPreference.setRobotLightsaber(this.context, true);
                    }
                    SharedUtils.notifyRobotChanged(this.context);
                    return;
                }
                return;
            }
            if (eventJob instanceof ProjectorJob) {
                ProjectorFinishCallback projectorFinishCallback = new ProjectorFinishCallback() { // from class: com.bullb.r2d2_nanopisystem.EventHandler.2
                    @Override // com.bullb.r2d2_nanopisystem.EventHandler.ProjectorFinishCallback
                    public void finishCallBack() {
                        EventHandler.this.commander.projectorMode(0);
                        RobotPreference.setRobotProjector(EventHandler.this.context, 0);
                        SharedUtils.notifyRobotChanged(EventHandler.this.context);
                    }
                };
                ProjectorJob projectorJob = (ProjectorJob) eventJob;
                if (projectorJob.getMode() == 0) {
                    boolean isSuccess4 = this.commander.projectorMode(0);
                    if (isSuccess4) {
                        RobotPreference.setRobotProjector(this.context, 0);
                        SharedUtils.notifyRobotChanged(this.context);
                        this.soundPlayer.pause();
                        return;
                    }
                    return;
                }
                boolean isSuccess5 = this.commander.projectorMode(projectorJob.getMode());
                if (isSuccess5) {
                    if (projectorJob.getMode() == 1) {
                        RobotPreference.setRobotProjector(this.context, 1);
                        playSound(100, true, projectorFinishCallback);
                    } else if (projectorJob.getMode() == 2) {
                        RobotPreference.setRobotProjector(this.context, 2);
                        playSound(101, true, projectorFinishCallback);
                    }
                    SharedUtils.notifyRobotChanged(this.context);
                    return;
                }
                return;
            }
            if (eventJob instanceof LCDJob) {
                LCDJob lcdJob = (LCDJob) eventJob;
                boolean isSuccess6 = this.commander.LCD(lcdJob.getS(), lcdJob.getL());
                if (isSuccess6) {
                    if (lcdJob.getL() == 1) {
                        RobotPreference.setRobotLongLCD(this.context, false);
                    } else {
                        RobotPreference.setRobotLongLCD(this.context, true);
                    }
                    if (lcdJob.getS() == 1) {
                        RobotPreference.setRobotShortLCD(this.context, false);
                    } else {
                        RobotPreference.setRobotShortLCD(this.context, true);
                    }
                    SharedUtils.notifyRobotChanged(this.context);
                    return;
                }
                return;
            }
            if (eventJob instanceof LEDJob) {
                this.commander.LED(((LEDJob) eventJob).getR(), ((LEDJob) eventJob).getB(), ((LEDJob) eventJob).getY(), ((LEDJob) eventJob).getG());
                return;
            }
            if (eventJob instanceof SoundJob) {
                SoundJob soundJob = (SoundJob) eventJob;
                Log.d("EventHandler", String.valueOf(soundJob.getSound()));
                playSound(soundJob.getSound(), true);
                return;
            }
            if (eventJob instanceof PatrolJob) {
                PatrolJob patrolJob = (PatrolJob) eventJob;
                if (patrolJob.isEnable()) {
                    isSuccess = this.commander.mode(9);
                } else {
                    isSuccess = this.commander.mode(0);
                }
                if (isSuccess) {
                    if (patrolJob.isEnable()) {
                        ModeController.getInstance(this.context).startPatrolMode();
                        return;
                    } else {
                        ModeController.getInstance(this.context).stopPatrolMode();
                        return;
                    }
                }
                return;
            }
            if (eventJob instanceof ModeJob) {
                ModeJob modeJob = (ModeJob) eventJob;
                this.commander.mode(modeJob.getMode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playSound(int soundId, boolean interrupt) {
        this.soundPlayer.play(soundId, interrupt);
    }

    public void playSound(int soundId, boolean interrupt, ProjectorFinishCallback soundFinishCallback) {
        this.soundPlayer.play(soundId, interrupt, soundFinishCallback);
    }
}
