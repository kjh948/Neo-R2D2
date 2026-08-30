package com.bullb.r2d2_nanopisystem;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.bullb.r2d2_nanopisystem.Bluetooth.BluetoothService;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.bullb.r2d2_nanopisystem.SelfUpdate.AppUpdater;
import com.bullb.r2d2_nanopisystem.SerialPort.SerialPort;
import com.bullb.r2d2_nanopisystem.SerialPort.SerialPortCommandReceiver;
import com.bullb.r2d2_nanopisystem.Sound.SoundPlayer;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.erz.joysticklibrary.JoyStick;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.JavaCameraView;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, JoyStick.OnJoystickMoveListener, SerialPort.SerialPortSendCallback {
    private static final String TAG = "MainActivity";
    public static CentralController centralController;
    private AppUpdater appUpdater;
    private ToggleButton armBtn;
    private BluetoothService bluetoothService;
    private Button clearBtn;
    private BroadcastReceiver clientAppCmdReceiver;
    private Commander commander;
    private Button connectBtn;
    private Button debugBtn;
    private EventHandler eventHandler;
    private Button headLeftBtn;
    private Button headRightBtn;
    private JoyStick joystick;
    private ToggleButton lightsaberBtn;
    private Button modeBtn;
    private EditText modeEditText;
    private Button playSoundBtn;
    private ToggleButton projectorBtn;
    private TextView receivedText;
    private ScrollView scrollView;
    private Button sendBtn;
    private SerialPort serialPort;
    private SerialPortCommandReceiver serialPortCommandReceiver;
    private BroadcastReceiver serialPortReceiver;
    private SoundPlayer soundPlayer;
    private View terminal;
    private EditText toEditText;
    private Timer updateRobotInfoTimer;
    private TextView voiceIndicator;
    private WifiService wifiService;
    private final int MAX_LINE = 50;
    private ArrayList<String> log = new ArrayList<>();
    private boolean toogle = true;
    private boolean camera = false;
    private boolean toggle = true;
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() { // from class: com.bullb.r2d2_nanopisystem.MainActivity.5
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                switch (state) {
                    case 10:
                        Log.d("BroadcastReceiver", "Bluetooth off");
                        break;
                    case 11:
                        Log.d("BroadcastReceiver", "Turning Bluetooth off");
                        break;
                    case 12:
                        Log.d("BroadcastReceiver", "Bluetooth on");
                        break;
                    case 13:
                        Log.d("BroadcastReceiver", "Turning Bluetooth off...");
                        break;
                }
            }
        }
    };

    @Override // android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MainApplication.getLocalizedContext(base));
    }

    @Override // android.support.v7.app.AppCompatActivity, android.support.v4.app.FragmentActivity, android.support.v4.app.BaseFragmentActivityGingerbread, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(1);
        getWindow().setFlags(1024, 1024);
        getWindow().getDecorView().setSystemUiVisibility(6);
        setContentView(R.layout.activity_main);
        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));
        if (RobotPreference.getRobotName(this) == null) {
            RobotPreference.setRobotName(this, "R2D2-" + String.valueOf((System.currentTimeMillis() % 900) + 100));
        }
        if (RobotPreference.getRobotUdid(this) == null) {
            String uuid = UUID.randomUUID().toString();
            RobotPreference.setRobotUdid(this, uuid);
        }
        this.wifiService = WifiService.getInstance(this);
        this.wifiService.start();
        initUi();
        this.soundPlayer = SoundPlayer.getInstance(this);
        this.serialPort = SerialPort.getInstance(this);
        this.serialPort.startService();
        this.serialPort.setSerialPortSendCallback(this);
        this.appUpdater = AppUpdater.getInstance(this);
        this.commander = Commander.getInstance(this);
        ModeController.getInstance(this);
        LEDLightController.getInstance(this);
        LEDLightController.getInstance(this).restoreAll();
        this.eventHandler = EventHandler.getInstance(this);
        this.serialPortCommandReceiver = new SerialPortCommandReceiver(this);
        this.serialPortReceiver = new BroadcastReceiver() { // from class: com.bullb.r2d2_nanopisystem.MainActivity.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                String msg = intent.getStringExtra("msg");
                Log.d("SERIAL_RECEIVE", msg);
                MainActivity.this.serialPortCommandReceiver.interpretCommand(msg);
            }
        };
        this.clientAppCmdReceiver = new BroadcastReceiver() { // from class: com.bullb.r2d2_nanopisystem.MainActivity.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                String msg = intent.getStringExtra("msg");
                boolean isInput = intent.getBooleanExtra("isInput", false);
                boolean isClient = intent.getBooleanExtra("isClient", true);
                MainActivity.this.showSerialPortMsg(msg, isInput, isClient);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.serialPortReceiver, new IntentFilter("serial_port_receiver"));
        LocalBroadcastManager.getInstance(this).registerReceiver(this.clientAppCmdReceiver, new IntentFilter("client_app_cmd_receiver"));
        registerReceiver(this.bluetoothStateReceiver, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"));
        JavaCameraView javaCameraView = (JavaCameraView) findViewById(R.id.java_camera_view);
        centralController = CentralController.getInstance(this);
        centralController.setJavaCameraView(javaCameraView);
        centralController.setMute(RobotPreference.isEnabledMute(this));
        if (RobotPreference.isEnabledVoiceRecognition(this)) {
            centralController.startVoiceRecognition();
        }
        if (RobotPreference.isEnabledFaceDetection(this)) {
            centralController.startFaceDetection();
        }
        this.eventHandler.softwareReady();
        this.updateRobotInfoTimer = new Timer();
        this.updateRobotInfoTimer.schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.MainActivity.3
            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                Log.i(MainActivity.TAG, "Gin request");
                MainActivity.this.commander.gin();
            }
        }, 0L, 5000L);
        RobotPreference.addRobotRestartVersion(this);
        this.eventHandler.playSound(9, true);
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService("keyguard");
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock("keyguard");
        lock.disableKeyguard();
        getWindow().addFlags(128);
        this.wifiService.onMainActivityStart();
    }

    private void initUi() {
        this.toEditText = (EditText) findViewById(R.id.to_edit_text);
        this.modeEditText = (EditText) findViewById(R.id.mode_edit_text);
        this.sendBtn = (Button) findViewById(R.id.send_btn);
        this.receivedText = (TextView) findViewById(R.id.received_text);
        this.scrollView = (ScrollView) findViewById(R.id.scroll_view);
        this.joystick = (JoyStick) findViewById(R.id.joystick);
        this.clearBtn = (Button) findViewById(R.id.clear_btn);
        this.modeBtn = (Button) findViewById(R.id.mode_btn);
        this.headLeftBtn = (Button) findViewById(R.id.head_left_btn);
        this.headRightBtn = (Button) findViewById(R.id.head_right_btn);
        this.armBtn = (ToggleButton) findViewById(R.id.arm_btn);
        this.projectorBtn = (ToggleButton) findViewById(R.id.projector_btn);
        this.lightsaberBtn = (ToggleButton) findViewById(R.id.lightsaber_btn);
        this.debugBtn = (Button) findViewById(R.id.debug_btn);
        this.playSoundBtn = (Button) findViewById(R.id.play_sound_btn);
        this.voiceIndicator = (TextView) findViewById(R.id.voice_indicator);
        this.connectBtn = (Button) findViewById(R.id.connect_btn);
        this.terminal = findViewById(R.id.terminal);
        this.joystick.setOnJoystickMoveListener(this, 300L);
        this.sendBtn.setOnClickListener(this);
        this.clearBtn.setOnClickListener(this);
        this.armBtn.setOnClickListener(this);
        this.projectorBtn.setOnClickListener(this);
        this.lightsaberBtn.setOnClickListener(this);
        this.debugBtn.setOnClickListener(this);
        this.modeBtn.setOnClickListener(this);
        this.headLeftBtn.setOnClickListener(this);
        this.headRightBtn.setOnClickListener(this);
        this.playSoundBtn.setOnClickListener(this);
        this.connectBtn.setOnClickListener(this);
        this.terminal.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSerialPortMsg(String msg, boolean isInput, boolean isClient) {
        String temp;
        if (isInput) {
            temp = ">>>" + msg;
        } else {
            temp = msg;
        }
        if (isClient) {
            temp = "From App:" + msg;
        }
        this.receivedText.append(temp);
        int excessLineNumber = this.receivedText.getLineCount() - 50;
        if (excessLineNumber > 0) {
            int eolIndex = -1;
            CharSequence charSequence = this.receivedText.getText();
            for (int i = 0; i < excessLineNumber; i++) {
                do {
                    eolIndex++;
                    if (eolIndex < charSequence.length()) {
                    }
                } while (charSequence.charAt(eolIndex) != '\n');
            }
            if (eolIndex < charSequence.length()) {
                this.receivedText.getEditableText().delete(0, eolIndex + 1);
            } else {
                this.receivedText.setText("");
            }
        }
        this.scrollView.post(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.MainActivity.4
            @Override // java.lang.Runnable
            public void run() {
                MainActivity.this.scrollView.fullScroll(130);
            }
        });
    }

    public void onDebugCrashClick(View v) {
        throw new NullPointerException();
    }

    public void onDebugHardwareButtonClick(View v) {
        Button b = (Button) v;
        int i = Integer.parseInt(b.getText().toString());
        Log.d("VBUTTON", "{\"cmd\":\"btn\", \"value\": " + i + "}");
        Intent intent = new Intent("serial_port_receiver");
        intent.putExtra("msg", "{\"cmd\":\"btn\", \"value\": " + i + "}");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    public void debugConnectWifi(View v) {
        String password = ((EditText) findViewById(R.id.wifi_password_edittext)).getText().toString();
        ModeController.getInstance(this).debugConnectWIFI("BULL.B TECH", password);
    }

    public void debugUpdateApp(View v) {
        this.appUpdater.updateAPK("https://release.stage.bull-b.com/r2d2/download/5b5585864074440024359992");
    }

    public void debug1(View vSERIAL_SEND_SUCCESS) {
        SerialPort serialPort = SerialPort.getInstance(this);
        serialPort.send("{\"g\":0,\"cmd\":\"led\",\"b\":2,\"r\":2,\"y\":0}");
    }

    public void debug2() {
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.arm_btn /* 2131558493 */:
                if (this.armBtn.isChecked()) {
                    this.commander.extendArm(1);
                } else {
                    this.commander.extendArm(0);
                }
                break;
            case R.id.projector_btn /* 2131558494 */:
                if (this.projectorBtn.isChecked()) {
                    this.commander.projectorMode(1);
                } else {
                    this.commander.projectorMode(0);
                }
                break;
            case R.id.lightsaber_btn /* 2131558495 */:
                if (this.lightsaberBtn.isChecked()) {
                    this.commander.lightsaber(1);
                } else {
                    this.commander.lightsaber(0);
                }
                break;
            case R.id.debug_btn /* 2131558496 */:
                this.commander.debug();
                break;
            case R.id.mode_btn /* 2131558498 */:
                if (this.modeEditText.getText().toString() != null && !this.modeEditText.getText().toString().isEmpty()) {
                    this.commander.mode(Integer.valueOf(this.modeEditText.getText().toString()).intValue());
                    break;
                }
                break;
            case R.id.head_left_btn /* 2131558499 */:
                this.eventHandler.moveHead(-90);
                break;
            case R.id.head_right_btn /* 2131558500 */:
                this.eventHandler.moveHead(90);
                break;
            case R.id.play_sound_btn /* 2131558501 */:
                this.eventHandler.playSound(4, false);
                break;
            case R.id.connect_btn /* 2131558502 */:
                this.toggle = this.toggle ? false : true;
                break;
            case R.id.send_btn /* 2131558506 */:
                this.serialPort.send(this.toEditText.getText().toString());
                break;
            case R.id.clear_btn /* 2131558507 */:
                this.receivedText.setText("");
                break;
        }
    }

    @Override // android.support.v4.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        super.onPause();
    }

    @Override // android.support.v7.app.AppCompatActivity, android.support.v4.app.FragmentActivity, android.app.Activity
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.serialPortReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.clientAppCmdReceiver);
        unregisterReceiver(this.bluetoothStateReceiver);
        this.soundPlayer.stop();
        if (centralController != null) {
            centralController.stopAllControl();
        }
        this.wifiService.stop();
        this.serialPort.stopService();
        super.onDestroy();
    }

    @Override // com.bullb.r2d2_nanopisystem.SerialPort.SerialPort.SerialPortSendCallback
    public void sentCallBack(boolean isSent, String msg) {
    }

    @Override // com.erz.joysticklibrary.JoyStick.OnJoystickMoveListener
    public void onValueChanged(int angle, int power, int direction) {
        Log.d("Joystick", "power: " + String.valueOf(power) + "   degree:" + String.valueOf(angle));
        this.commander.move(power, angle);
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", Commander.MOVE);
            json.put(RobotApiHandler.POWER, String.valueOf(power));
            json.put("angle", String.valueOf(angle));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.bluetoothService.write((json.toString() + "\n").getBytes());
    }
}
