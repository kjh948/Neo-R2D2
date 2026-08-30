package edu.cmu.pocketsphinx;

import java.io.File;
import java.io.IOException;

/* JADX INFO: loaded from: classes.dex */
public class SpeechRecognizerSetup {
    private final Config config;

    static {
        System.loadLibrary("pocketsphinx_jni");
    }

    public static SpeechRecognizerSetup defaultSetup() {
        return new SpeechRecognizerSetup(Decoder.defaultConfig());
    }

    public static SpeechRecognizerSetup setupFromFile(File configFile) {
        return new SpeechRecognizerSetup(Decoder.fileConfig(configFile.getPath()));
    }

    private SpeechRecognizerSetup(Config config) {
        this.config = config;
    }

    public SpeechRecognizer getRecognizer() throws IOException {
        return new SpeechRecognizer(this.config);
    }

    public SpeechRecognizerSetup setAcousticModel(File model) {
        return setString("-hmm", model.getPath());
    }

    public SpeechRecognizerSetup setDictionary(File dictionary) {
        return setString("-dict", dictionary.getPath());
    }

    public SpeechRecognizerSetup setSampleRate(int rate) {
        return setFloat("-samprate", rate);
    }

    public SpeechRecognizerSetup setRawLogDir(File dir) {
        return setString("-rawlogdir", dir.getPath());
    }

    public SpeechRecognizerSetup setKeywordThreshold(float threshold) {
        return setFloat("-kws_threshold", threshold);
    }

    public SpeechRecognizerSetup setBoolean(String key, boolean value) {
        this.config.setBoolean(key, value);
        return this;
    }

    public SpeechRecognizerSetup setInteger(String key, int value) {
        this.config.setInt(key, value);
        return this;
    }

    public SpeechRecognizerSetup setFloat(String key, double value) {
        this.config.setFloat(key, value);
        return this;
    }

    public SpeechRecognizerSetup setString(String key, String value) {
        this.config.setString(key, value);
        return this;
    }
}
