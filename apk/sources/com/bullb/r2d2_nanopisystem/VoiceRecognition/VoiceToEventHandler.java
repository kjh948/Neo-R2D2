package com.bullb.r2d2_nanopisystem.VoiceRecognition;

import android.content.Context;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.R;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class VoiceToEventHandler {
    private Context context;
    private EventHandler eventHandler;
    private final String TAG = "Voice Event Handler";
    private HashMap<String, VoiceCommand> voiceCommandMap = setUpVoiceCommandMap();

    public enum VoiceCommand {
        WAKE_UP,
        TURN_LEFT,
        TURN_RIGHT,
        TURN_AROUND,
        GO_FORWARD,
        SHAKE_HEAD,
        WALK_A_CIRCLE,
        DANCE,
        MAKE_SOME_NOISE,
        WHO_ARE_YOU,
        SKY_WALKER,
        PRINCESS_LEIA,
        LIGHT_SABER,
        ARMS,
        PATROL,
        STOP,
        ANGLE_SECRET,
        STARK_SECRET
    }

    public VoiceToEventHandler(Context context) {
        this.eventHandler = EventHandler.getInstance(context);
        this.context = context;
    }

    private HashMap<String, VoiceCommand> setUpVoiceCommandMap() {
        HashMap<String, VoiceCommand> voiceCommandMap = new HashMap<>();
        String[] array = this.context.getResources().getStringArray(R.array.voice_wake_up_phrase);
        for (String s : array) {
            voiceCommandMap.put(s, VoiceCommand.WAKE_UP);
        }
        String[] array2 = this.context.getResources().getStringArray(R.array.voice_turn_left);
        for (String s2 : array2) {
            voiceCommandMap.put(s2, VoiceCommand.TURN_LEFT);
        }
        String[] array3 = this.context.getResources().getStringArray(R.array.voice_turn_right);
        for (String s3 : array3) {
            voiceCommandMap.put(s3, VoiceCommand.TURN_RIGHT);
        }
        String[] array4 = this.context.getResources().getStringArray(R.array.voice_turn_around);
        for (String s4 : array4) {
            voiceCommandMap.put(s4, VoiceCommand.TURN_AROUND);
        }
        String[] array5 = this.context.getResources().getStringArray(R.array.voice_go_forward);
        for (String s5 : array5) {
            voiceCommandMap.put(s5, VoiceCommand.GO_FORWARD);
        }
        String[] array6 = this.context.getResources().getStringArray(R.array.voice_shake_your_head);
        for (String s6 : array6) {
            voiceCommandMap.put(s6, VoiceCommand.SHAKE_HEAD);
        }
        String[] array7 = this.context.getResources().getStringArray(R.array.voice_walk_a_circle);
        for (String s7 : array7) {
            voiceCommandMap.put(s7, VoiceCommand.WALK_A_CIRCLE);
        }
        String[] array8 = this.context.getResources().getStringArray(R.array.voice_dance);
        for (String s8 : array8) {
            voiceCommandMap.put(s8, VoiceCommand.DANCE);
        }
        String[] array9 = this.context.getResources().getStringArray(R.array.voice_make_some_noice);
        for (String s9 : array9) {
            voiceCommandMap.put(s9, VoiceCommand.MAKE_SOME_NOISE);
        }
        String[] array10 = this.context.getResources().getStringArray(R.array.voice_who_are_you);
        for (String s10 : array10) {
            voiceCommandMap.put(s10, VoiceCommand.WHO_ARE_YOU);
        }
        String[] array11 = this.context.getResources().getStringArray(R.array.voice_lightsaber);
        for (String s11 : array11) {
            voiceCommandMap.put(s11, VoiceCommand.LIGHT_SABER);
        }
        String[] array12 = this.context.getResources().getStringArray(R.array.voice_project_skywalker);
        for (String s12 : array12) {
            voiceCommandMap.put(s12, VoiceCommand.SKY_WALKER);
        }
        String[] array13 = this.context.getResources().getStringArray(R.array.voice_project_princess_leia);
        for (String s13 : array13) {
            voiceCommandMap.put(s13, VoiceCommand.PRINCESS_LEIA);
        }
        String[] array14 = this.context.getResources().getStringArray(R.array.voice_arm);
        for (String s14 : array14) {
            voiceCommandMap.put(s14, VoiceCommand.ARMS);
        }
        String[] array15 = this.context.getResources().getStringArray(R.array.voice_patrol_now);
        for (String s15 : array15) {
            voiceCommandMap.put(s15, VoiceCommand.PATROL);
        }
        String[] array16 = this.context.getResources().getStringArray(R.array.voice_stop);
        for (String s16 : array16) {
            voiceCommandMap.put(s16, VoiceCommand.STOP);
        }
        String[] array17 = this.context.getResources().getStringArray(R.array.voice_angle);
        for (String s17 : array17) {
            voiceCommandMap.put(s17, VoiceCommand.ANGLE_SECRET);
        }
        String[] array18 = this.context.getResources().getStringArray(R.array.voice_stark);
        for (String s18 : array18) {
            voiceCommandMap.put(s18, VoiceCommand.STARK_SECRET);
        }
        return voiceCommandMap;
    }

    public VoiceCommand getCommand(String voice) {
        for (String command : this.voiceCommandMap.keySet()) {
            if (voice.indexOf(command) == 0) {
                VoiceCommand cmd = this.voiceCommandMap.get(command);
                return cmd;
            }
        }
        return null;
    }

    public boolean voiceToEvent(String voice) {
        VoiceCommand cmd = getCommand(voice);
        boolean commandIsFound = true;
        if (cmd == null) {
            this.eventHandler.mode(8);
            commandIsFound = false;
        } else {
            switch (cmd) {
                case WAKE_UP:
                    this.eventHandler.voiceWakeUp();
                    break;
                case TURN_LEFT:
                    this.eventHandler.mode(3);
                    break;
                case TURN_RIGHT:
                    this.eventHandler.mode(4);
                    break;
                case TURN_AROUND:
                    this.eventHandler.mode(2);
                    break;
                case GO_FORWARD:
                    this.eventHandler.mode(5);
                    break;
                case SHAKE_HEAD:
                    this.eventHandler.shakeYourHead();
                    break;
                case WALK_A_CIRCLE:
                    this.eventHandler.walkCircle();
                    break;
                case DANCE:
                    this.eventHandler.mode(10);
                    break;
                case MAKE_SOME_NOISE:
                    this.eventHandler.makeSomeNoise();
                    break;
                case WHO_ARE_YOU:
                    this.eventHandler.mode(7);
                    break;
                case LIGHT_SABER:
                    this.eventHandler.lightsaber();
                    break;
                case SKY_WALKER:
                    this.eventHandler.mode(20);
                    break;
                case PRINCESS_LEIA:
                    this.eventHandler.mode(19);
                    break;
                case ARMS:
                    this.eventHandler.arm();
                    break;
                case PATROL:
                    this.eventHandler.patrol();
                    break;
                case STOP:
                    this.eventHandler.modeStop();
                    break;
                case ANGLE_SECRET:
                case STARK_SECRET:
                    break;
                default:
                    commandIsFound = false;
                    break;
            }
        }
        if (commandIsFound) {
            ModeController.getInstance(this.context).wake();
            return true;
        }
        return true;
    }

    public void endVoiceEvent() {
        this.eventHandler.endVoice();
    }
}
