package com.erz.joysticklibrary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.internal.view.SupportMenu;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/* JADX INFO: loaded from: classes.dex */
public class JoyStick extends View implements Runnable {
    public static final int BOTTOM = 7;
    public static final int BOTTOM_LEFT = 8;
    public static final long DEFAULT_LOOP_INTERVAL = 100;
    public static final int FRONT = 3;
    public static final int FRONT_RIGHT = 4;
    public static final int LEFT = 1;
    public static final int LEFT_FRONT = 2;
    public static final int RIGHT = 5;
    public static final int RIGHT_BOTTOM = 6;
    private final double RAD;
    private Paint button;
    private int buttonRadius;
    private double centerX;
    private double centerY;
    private Paint horizontalLine;
    private int joystickRadius;
    private int lastAngle;
    private int lastPower;
    private long loopInterval;
    private Paint mainCircle;
    private OnJoystickMoveListener onJoystickMoveListener;
    private Paint secondaryCircle;
    private Thread thread;
    private Paint verticalLine;
    private int xPosition;
    private int yPosition;

    public interface OnJoystickMoveListener {
        void onValueChanged(int i, int i2, int i3);
    }

    public JoyStick(Context context) {
        super(context);
        this.RAD = 57.2957795d;
        this.thread = new Thread(this);
        this.loopInterval = 100L;
        this.xPosition = 0;
        this.yPosition = 0;
        this.centerX = 0.0d;
        this.centerY = 0.0d;
        this.lastAngle = 0;
        this.lastPower = 0;
    }

    public JoyStick(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.RAD = 57.2957795d;
        this.thread = new Thread(this);
        this.loopInterval = 100L;
        this.xPosition = 0;
        this.yPosition = 0;
        this.centerX = 0.0d;
        this.centerY = 0.0d;
        this.lastAngle = 0;
        this.lastPower = 0;
        initJoystickView();
    }

    public JoyStick(Context context, AttributeSet attrs, int defaultStyle) {
        super(context, attrs, defaultStyle);
        this.RAD = 57.2957795d;
        this.thread = new Thread(this);
        this.loopInterval = 100L;
        this.xPosition = 0;
        this.yPosition = 0;
        this.centerX = 0.0d;
        this.centerY = 0.0d;
        this.lastAngle = 0;
        this.lastPower = 0;
        initJoystickView();
    }

    protected void initJoystickView() {
        this.mainCircle = new Paint(1);
        this.mainCircle.setColor(-1);
        this.mainCircle.setStyle(Paint.Style.FILL_AND_STROKE);
        this.secondaryCircle = new Paint();
        this.secondaryCircle.setColor(-16711936);
        this.secondaryCircle.setStyle(Paint.Style.STROKE);
        this.verticalLine = new Paint();
        this.verticalLine.setStrokeWidth(5.0f);
        this.verticalLine.setColor(SupportMenu.CATEGORY_MASK);
        this.horizontalLine = new Paint();
        this.horizontalLine.setStrokeWidth(2.0f);
        this.horizontalLine.setColor(ViewCompat.MEASURED_STATE_MASK);
        this.button = new Paint(1);
        this.button.setColor(SupportMenu.CATEGORY_MASK);
        this.button.setStyle(Paint.Style.FILL);
    }

    @Override // android.view.View
    protected void onFinishInflate() {
    }

    @Override // android.view.View
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        this.xPosition = getWidth() / 2;
        this.yPosition = getWidth() / 2;
        int d = Math.min(xNew, yNew);
        this.buttonRadius = (int) (((double) (d / 2)) * 0.25d);
        this.joystickRadius = (int) (((double) (d / 2)) * 0.75d);
    }

    @Override // android.view.View
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int d = Math.min(measure(widthMeasureSpec), measure(heightMeasureSpec));
        setMeasuredDimension(d, d);
    }

    private int measure(int measureSpec) {
        int specMode = View.MeasureSpec.getMode(measureSpec);
        int specSize = View.MeasureSpec.getSize(measureSpec);
        if (specMode == 0) {
            return 200;
        }
        return specSize;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        this.centerX = getWidth() / 2;
        this.centerY = getHeight() / 2;
        canvas.drawCircle((int) this.centerX, (int) this.centerY, this.joystickRadius, this.mainCircle);
        canvas.drawCircle((int) this.centerX, (int) this.centerY, this.joystickRadius / 2, this.secondaryCircle);
        canvas.drawLine((float) this.centerX, (float) this.centerY, (float) this.centerX, (float) (this.centerY - ((double) this.joystickRadius)), this.verticalLine);
        canvas.drawLine((float) (this.centerX - ((double) this.joystickRadius)), (float) this.centerY, (float) (this.centerX + ((double) this.joystickRadius)), (float) this.centerY, this.horizontalLine);
        canvas.drawLine((float) this.centerX, (float) (this.centerY + ((double) this.joystickRadius)), (float) this.centerX, (float) this.centerY, this.horizontalLine);
        canvas.drawCircle(this.xPosition, this.yPosition, this.buttonRadius, this.button);
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent event) {
        this.xPosition = (int) event.getX();
        this.yPosition = (int) event.getY();
        double abs = Math.sqrt(((((double) this.xPosition) - this.centerX) * (((double) this.xPosition) - this.centerX)) + ((((double) this.yPosition) - this.centerY) * (((double) this.yPosition) - this.centerY)));
        if (abs > this.joystickRadius) {
            this.xPosition = (int) ((((((double) this.xPosition) - this.centerX) * ((double) this.joystickRadius)) / abs) + this.centerX);
            this.yPosition = (int) ((((((double) this.yPosition) - this.centerY) * ((double) this.joystickRadius)) / abs) + this.centerY);
        }
        invalidate();
        if (event.getAction() == 1) {
            this.xPosition = (int) this.centerX;
            this.yPosition = (int) this.centerY;
            this.thread.interrupt();
            if (this.onJoystickMoveListener != null) {
                this.onJoystickMoveListener.onValueChanged(getAngle(), getPower(), getDirection());
            }
        }
        if (this.onJoystickMoveListener != null && event.getAction() == 0) {
            if (this.thread != null && this.thread.isAlive()) {
                this.thread.interrupt();
            }
            this.thread = new Thread(this);
            this.thread.start();
            if (this.onJoystickMoveListener != null) {
                this.onJoystickMoveListener.onValueChanged(getAngle(), getPower(), getDirection());
            }
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getAngle() {
        if (this.xPosition > this.centerX) {
            if (this.yPosition < this.centerY) {
                int iAtan = (int) ((Math.atan((((double) this.yPosition) - this.centerY) / (((double) this.xPosition) - this.centerX)) * 57.2957795d) + 90.0d);
                this.lastAngle = iAtan;
                return iAtan;
            }
            if (this.yPosition > this.centerY) {
                int iAtan2 = ((int) (Math.atan((((double) this.yPosition) - this.centerY) / (((double) this.xPosition) - this.centerX)) * 57.2957795d)) + 90;
                this.lastAngle = iAtan2;
                return iAtan2;
            }
            this.lastAngle = 90;
            return 90;
        }
        if (this.xPosition >= this.centerX) {
            if (this.yPosition <= this.centerY) {
                this.lastAngle = 0;
                return 0;
            }
            if (this.lastAngle < 0) {
                this.lastAngle = -180;
                return -180;
            }
            this.lastAngle = 180;
            return 180;
        }
        if (this.yPosition < this.centerY) {
            int iAtan3 = (int) ((Math.atan((((double) this.yPosition) - this.centerY) / (((double) this.xPosition) - this.centerX)) * 57.2957795d) - 90.0d);
            this.lastAngle = iAtan3;
            return iAtan3;
        }
        if (this.yPosition > this.centerY) {
            int iAtan4 = ((int) (Math.atan((((double) this.yPosition) - this.centerY) / (((double) this.xPosition) - this.centerX)) * 57.2957795d)) - 90;
            this.lastAngle = iAtan4;
            return iAtan4;
        }
        this.lastAngle = -90;
        return -90;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getPower() {
        return (int) ((100.0d * Math.sqrt(((((double) this.xPosition) - this.centerX) * (((double) this.xPosition) - this.centerX)) + ((((double) this.yPosition) - this.centerY) * (((double) this.yPosition) - this.centerY)))) / ((double) this.joystickRadius));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getDirection() {
        if (this.lastPower == 0 && this.lastAngle == 0) {
            return 0;
        }
        int a = 0;
        if (this.lastAngle <= 0) {
            a = (this.lastAngle * (-1)) + 90;
        } else if (this.lastAngle > 0) {
            if (this.lastAngle <= 90) {
                a = 90 - this.lastAngle;
            } else {
                a = 360 - (this.lastAngle - 90);
            }
        }
        int direction = ((a + 22) / 45) + 1;
        if (direction > 8) {
            return 1;
        }
        return direction;
    }

    public void setOnJoystickMoveListener(OnJoystickMoveListener listener, long repeatInterval) {
        this.onJoystickMoveListener = listener;
        this.loopInterval = repeatInterval;
    }

    @Override // java.lang.Runnable
    public void run() {
        while (!Thread.interrupted()) {
            post(new Runnable() { // from class: com.erz.joysticklibrary.JoyStick.1
                @Override // java.lang.Runnable
                public void run() {
                    if (JoyStick.this.onJoystickMoveListener != null) {
                        JoyStick.this.onJoystickMoveListener.onValueChanged(JoyStick.this.getAngle(), JoyStick.this.getPower(), JoyStick.this.getDirection());
                    }
                }
            });
            try {
                Thread.sleep(this.loopInterval);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
