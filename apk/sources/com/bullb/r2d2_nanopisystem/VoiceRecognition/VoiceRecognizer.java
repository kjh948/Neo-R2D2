package com.bullb.r2d2_nanopisystem.VoiceRecognition;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.R;
import com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceToEventHandler;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class VoiceRecognizer implements RecognitionListener {
    private static final String COMMAND_SEARCH = "command";
    private static final String DICTATION_FILE = "cmudict.dict";
    private static final String KEYWORDS_FILE = "keywords";
    private static final int MODE_DISABLE = -1;
    private static final int MODE_WAIT = 0;
    private static final int MODE_WAKE = 1;
    private static final String PTM_FOLDER = "ptm";
    private static final int WAKE_TIME = 15000;
    private static VoiceRecognizer voiceRecognizer;
    private String assetSubPath;
    private Context context;
    private boolean isSet;
    private SpeechRecognizer recognizer;
    private TimerTask timerTask;
    private VoiceToEventHandler voiceToEventHandler;
    private String TAG = "Voice Recognizer";
    private int currentMode = -1;
    private boolean isVoiceRecognitionMode = false;
    private Timer timer = new Timer();

    public interface VoiceStatusChangedListener {
        void onStatusChanged(boolean z);
    }

    public static synchronized VoiceRecognizer getInstance(Context context) {
        if (voiceRecognizer == null) {
            voiceRecognizer = new VoiceRecognizer(context);
        }
        return voiceRecognizer;
    }

    private VoiceRecognizer(Context context) {
        this.context = context;
        if (!this.isSet) {
            runRecognizerSetup();
        }
        this.voiceToEventHandler = new VoiceToEventHandler(context);
        this.assetSubPath = context.getResources().getString(R.string.voice_path) + "/";
    }

    /* JADX WARN: Type inference failed for: r0v0, types: [com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceRecognizer$1] */
    private void runRecognizerSetup() {
        new AsyncTask<Void, Void, Exception>() { // from class: com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceRecognizer.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // android.os.AsyncTask
            public Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(VoiceRecognizer.this.context);
                    File assetDir = assets.syncAssets();
                    VoiceRecognizer.this.setupRecognizer(assetDir);
                    return null;
                } catch (IOException e) {
                    return e;
                }
            }

            /* JADX INFO: Access modifiers changed from: protected */
            @Override // android.os.AsyncTask
            public void onPostExecute(Exception result) {
                if (result != null) {
                    Log.d(VoiceRecognizer.this.TAG, "Failed to init recognizer");
                    return;
                }
                VoiceRecognizer.this.isSet = true;
                if (RobotPreference.isEnabledVoiceRecognition(VoiceRecognizer.this.context)) {
                    VoiceRecognizer.this.start();
                }
            }
        }.execute(new Void[0]);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setupRecognizer(File assetsDir) throws IOException {
        this.recognizer = SpeechRecognizerSetup.defaultSetup().setAcousticModel(new File(assetsDir, this.assetSubPath + PTM_FOLDER)).setDictionary(new File(assetsDir, this.assetSubPath + DICTATION_FILE)).getRecognizer();
        this.recognizer.addListener(this);
        File keywordFile = new File(assetsDir, this.assetSubPath + KEYWORDS_FILE);
        this.recognizer.addKeywordSearch(COMMAND_SEARCH, keywordFile);
    }

    private boolean isWakeUpCommand(String keyword) {
        return this.voiceToEventHandler.getCommand(keyword) == VoiceToEventHandler.VoiceCommand.WAKE_UP;
    }

    private void onRecognizeKeyword(String keyword) {
        if (this.currentMode == 1 || isWakeUpCommand(keyword)) {
            this.isVoiceRecognitionMode = true;
            boolean needRestart = this.voiceToEventHandler.voiceToEvent(keyword);
            if (needRestart) {
                switchMode(1);
                return;
            }
            return;
        }
        switchMode(0);
    }

    public void switchMode(int mode) {
        this.recognizer.stop();
        if (mode == -1) {
            Log.d(this.TAG, "MODE_DISABLE");
        } else if (mode == 0) {
            Log.d(this.TAG, "MODE_WAIT");
            this.recognizer.startListening(COMMAND_SEARCH);
        } else if (mode == 1) {
            Log.d(this.TAG, "MODE_WAKE");
            this.recognizer.startListening(COMMAND_SEARCH);
            if (this.timerTask != null) {
                this.timerTask.cancel();
            }
            if (this.timer != null) {
                this.timer.cancel();
            }
            this.timer = new Timer();
            this.timerTask = new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceRecognizer.2
                @Override // java.util.TimerTask, java.lang.Runnable
                public void run() {
                    ((Activity) VoiceRecognizer.this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceRecognizer.2.1
                        @Override // java.lang.Runnable
                        public void run() {
                            Log.d(VoiceRecognizer.this.TAG, "MODE_WAKE TIMEOUT, switch to MODE_WAIT");
                            if (VoiceRecognizer.this.currentMode != -1) {
                                VoiceRecognizer.this.switchMode(0);
                                VoiceRecognizer.this.isVoiceRecognitionMode = false;
                                VoiceRecognizer.this.voiceToEventHandler.endVoiceEvent();
                            }
                        }
                    });
                }
            };
            this.timer.schedule(this.timerTask, 15000L);
        }
        this.currentMode = mode;
    }

    public boolean isVoiceRecognitionMode() {
        return this.isVoiceRecognitionMode;
    }

    public void shutDown() {
        if (this.recognizer != null) {
            this.recognizer.shutdown();
        }
    }

    public void start() {
        if (this.recognizer != null) {
            switchMode(0);
        }
    }

    public void stop() {
        Log.d(this.TAG, "stop");
        if (this.recognizer != null) {
            switchMode(-1);
        }
        this.isVoiceRecognitionMode = false;
        EventHandler.getInstance(this.context).restoreLight();
    }

    @Override // edu.cmu.pocketsphinx.RecognitionListener
    public void onBeginningOfSpeech() {
        Log.d(this.TAG, "onBeginningOfSpeech");
    }

    @Override // edu.cmu.pocketsphinx.RecognitionListener
    public void onEndOfSpeech() {
        Log.d(this.TAG, "onEndOfSpeech");
    }

    @Override // edu.cmu.pocketsphinx.RecognitionListener
    public void onPartialResult(Hypothesis hypothesis) {
        Log.d(this.TAG, "onPartialResult, hypothesis: " + (hypothesis != null));
        if (hypothesis != null && hypothesis != null) {
            String text = hypothesis.getHypstr().toLowerCase();
            Log.d(this.TAG, "recognize (partial result): " + text + "    " + String.valueOf(hypothesis.getBestScore()) + "    " + String.valueOf(hypothesis.getProb()));
            Toast.makeText(this.context.getApplicationContext(), text, 0).show();
            onRecognizeKeyword(text);
        }
    }

    @Override // edu.cmu.pocketsphinx.RecognitionListener
    public void onResult(Hypothesis hypothesis) {
        Log.d(this.TAG, "onResult, hypothesis: " + (hypothesis != null));
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.d(this.TAG, "recognize (full result): " + text.toLowerCase() + "    " + String.valueOf(hypothesis.getBestScore()) + "    " + String.valueOf(hypothesis.getProb()));
        }
    }

    @Override // edu.cmu.pocketsphinx.RecognitionListener
    public void onError(Exception e) {
        Log.d(this.TAG, "onError");
        e.printStackTrace();
    }

    @Override // edu.cmu.pocketsphinx.RecognitionListener
    public void onTimeout() {
        Log.d(this.TAG, "onTimeout");
    }
}
