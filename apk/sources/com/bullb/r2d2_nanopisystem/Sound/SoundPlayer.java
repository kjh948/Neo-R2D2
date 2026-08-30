package com.bullb.r2d2_nanopisystem.Sound;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.R;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class SoundPlayer {
    public static final int ABRUPT_THRILL = 2;
    public static final int ALARMED_THRILL = 3;
    public static final int BUILDING_FREAK_OUT = 4;
    public static final int CURT_REPLY = 5;
    public static final int DANGER_DANGER = 6;
    public static final int HAPPINESS_CONFIRMATION = 7;
    public static final int HAPPY_THREE_CHIRP = 8;
    public static final int LONELY_HELLO = 9;
    public static final int LONELY_SINGING = 10;
    public static final int NAGGING_WHINE = 11;
    public static final int PULLING_IT_TOGETHER = 0;
    public static final int SHORT_RASPBERRY = 12;
    public static final int SING_SONG_RESPONSE = 1;
    public static final int SOUND_ANGLE = 301;
    public static final int SOUND_STARK = 302;
    public static final int STARTLED_THREE_TONE = 13;
    public static final int STARTLED_WHOOP = 14;
    public static final int STARWAR01 = 100;
    public static final int STARWAR03 = 101;
    public static final int STIFLED_LAUGH = 15;
    public static final int UNCERTAIN_TWO_TONE = 16;
    public static final int UNCONVINCED_GRUMBLING = 17;
    public static final int UPSET_TWO_TONE = 18;
    private static MediaPlayer mp;
    private static SoundPlayer soundPlayer;
    private Context context;
    private boolean isPlaying = false;
    private Timer projectorTimer;

    public static synchronized SoundPlayer getInstance(Context context) {
        if (soundPlayer == null) {
            soundPlayer = new SoundPlayer(context);
            mp = new MediaPlayer();
            AudioManager mAudioManager = (AudioManager) context.getSystemService("audio");
            mAudioManager.setStreamVolume(3, mAudioManager.getStreamMaxVolume(3), 0);
            mp.setAudioStreamType(3);
        }
        return soundPlayer;
    }

    private SoundPlayer(Context context) {
        this.context = context;
    }

    public void play(int soundId, boolean shouldInterrupt) {
        play(soundId, shouldInterrupt, null);
    }

    public void play(int soundId, boolean shouldInterrupt, EventHandler.ProjectorFinishCallback soundFinishCallback) {
        if (!this.isPlaying || (this.isPlaying && shouldInterrupt)) {
            if (mp != null) {
                mp.reset();
            }
            try {
                Log.d("package:", this.context.getPackageName());
                mp.setDataSource(this.context, Uri.parse("android.resource://" + this.context.getPackageName() + "/" + getSoundEffectRawId(soundId)));
                mp.prepare();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // from class: com.bullb.r2d2_nanopisystem.Sound.SoundPlayer.1
                @Override // android.media.MediaPlayer.OnCompletionListener
                public void onCompletion(MediaPlayer mp2) {
                    SoundPlayer.this.isPlaying = false;
                }
            });
            if (soundFinishCallback != null) {
                Log.d("timer", "start");
                if (this.projectorTimer != null) {
                    this.projectorTimer.cancel();
                }
                this.projectorTimer = new Timer();
                this.projectorTimer.schedule(new CallBackTimerTask(soundFinishCallback), mp.getDuration());
            }
            mp.start();
        }
    }

    public class CallBackTimerTask extends TimerTask {
        private EventHandler.ProjectorFinishCallback soundFinishCallback;

        CallBackTimerTask(EventHandler.ProjectorFinishCallback soundFinishCallback) {
            this.soundFinishCallback = soundFinishCallback;
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.d("timer", "finish");
            this.soundFinishCallback.finishCallBack();
        }
    }

    public void stop() {
        try {
            if (mp != null) {
                mp.stop();
                mp.release();
            }
        } catch (IllegalStateException ie) {
            ie.printStackTrace();
        }
    }

    public void pause() {
        if (mp != null) {
            mp.pause();
        }
        if (this.projectorTimer != null) {
            this.projectorTimer.cancel();
        }
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public int getSoundEffectRawId(int id) {
        switch (id) {
            case 0:
                return R.raw.pulling_it_together;
            case 1:
                return R.raw.sing_song_response;
            case 2:
                return R.raw.abrupt_burst;
            case 3:
                return R.raw.alarmed_thrill;
            case 4:
                return R.raw.building_freak_out;
            case 5:
                return R.raw.curt_reply;
            case 6:
                return R.raw.danger_danger;
            case 7:
                return R.raw.happiness_confirmation;
            case 8:
            default:
                return R.raw.happy_three_chirp;
            case 9:
                return R.raw.lonely_hello;
            case 10:
                return R.raw.lonely_singing;
            case 11:
                return R.raw.nagging_whine;
            case 12:
                return R.raw.short_raspberry;
            case 13:
                return R.raw.startled_three_tone;
            case 14:
                return R.raw.startled_whoop;
            case 15:
                return R.raw.stifled_laugh;
            case 16:
                return R.raw.uncertain_two_tone;
            case 17:
                return R.raw.unconvinced_grumbling;
            case 18:
                return R.raw.upset_two_tone;
            case 100:
                return R.raw.starwar01_right_02;
            case 101:
                return R.raw.starwar03_right_02;
            case 301:
                return R.raw.angle;
            case 302:
                return R.raw.stark;
        }
    }
}
