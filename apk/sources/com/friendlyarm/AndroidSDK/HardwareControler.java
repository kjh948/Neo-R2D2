package com.friendlyarm.AndroidSDK;

import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class HardwareControler {
    public static native int PWMPlay(int i);

    public static native int PWMStop();

    public static native int SPItransferBytes(int i, byte[] bArr, byte[] bArr2, int i2, int i3, int i4);

    public static native int SPItransferOneByte(int i, byte b, int i2, int i3, int i4);

    public static native void close(int i);

    public static native int exportGPIOPin(int i);

    public static native int getBoardType();

    public static native int getGPIODirection(int i);

    public static native int getGPIOValue(int i);

    public static native int ioctlWithIntValue(int i, int i2, int i3);

    public static native int open(String str, int i);

    public static native int openI2CDevice();

    public static native int openSerialPort(String str, long j, int i, int i2);

    public static native int openSerialPortEx(String str, long j, int i, int i2, String str2, String str3);

    public static native int read(int i, byte[] bArr, int i2);

    public static native int readADC();

    public static native int readADCWithChannel(int i);

    public static native int[] readADCWithChannels(int[] iArr);

    public static native int readByteDataFromI2C(int i, int i2);

    public static native int readByteFromI2C(int i, int i2, int i3);

    public static native int readBytesFromSPI(int i, byte[] bArr, int i2, int i3, int i4);

    public static native int select(int i, int i2, int i3);

    public static native int setGPIODirection(int i, int i2);

    public static native int setGPIOValue(int i, int i2);

    public static native int setI2CRetries(int i, int i2);

    public static native int setI2CSlave(int i, int i2);

    public static native int setI2CTimeout(int i, int i2);

    public static native int setLedState(int i, int i2);

    public static native int setSPIBitOrder(int i, int i2);

    public static native int setSPIClockDivider(int i, int i2);

    public static native int setSPIDataMode(int i, int i2);

    public static native int setSPIReadBitsPerWord(int i, int i2);

    public static native int setSPIWriteBitsPerWord(int i, int i2);

    public static native int unexportGPIOPin(int i);

    public static native int write(int i, byte[] bArr);

    public static native int writeByteDataToI2C(int i, int i2, byte b);

    public static native int writeByteToI2C(int i, int i2, byte b, int i3);

    public static native int writeBytesToSPI(int i, byte[] bArr, int i2, int i3, int i4);

    static {
        try {
            System.loadLibrary("friendlyarm-hardware");
        } catch (UnsatisfiedLinkError e) {
            Log.d("HardwareControler", "libfriendlyarm-hardware library not found!");
        }
    }
}
