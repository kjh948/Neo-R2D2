package com.friendlyarm.AndroidSDK;

import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class SPI {
    private static final String TAG = "com.friendlyarm.AndroidSDK.SPI";
    private static final String devName = "/dev/spidev1.0";
    private int spi_mode = 0;
    private int spi_bits = 8;
    private int spi_delay = 0;
    private int spi_speed = 500000;
    private int spi_byte_order = 0;
    private int spi_fd = -1;

    public void begin() {
        this.spi_fd = HardwareControler.open(devName, 2);
        if (this.spi_fd >= 0) {
            Log.d(TAG, "open /dev/spidev1.0ok!");
            HardwareControler.setSPIWriteBitsPerWord(this.spi_fd, this.spi_bits);
            HardwareControler.setSPIReadBitsPerWord(this.spi_fd, this.spi_bits);
        } else {
            Log.d(TAG, "open /dev/spidev1.0failed!");
            this.spi_fd = -1;
        }
    }

    public void end() {
        if (this.spi_fd != -1) {
            HardwareControler.close(this.spi_fd);
            this.spi_fd = -1;
        }
    }

    public void setBitOrder(int order) {
        if (this.spi_fd >= 0) {
            this.spi_byte_order = 1;
            if (this.spi_byte_order == 0) {
                this.spi_mode |= 8;
            } else {
                this.spi_mode &= -9;
            }
            HardwareControler.setSPIBitOrder(this.spi_fd, this.spi_byte_order);
        }
    }

    public void setClockDivider(int divider) {
        if (this.spi_fd >= 0) {
            this.spi_speed = 66666666 / ((divider + 1) * 2);
            if (this.spi_speed > 500000) {
                this.spi_speed = 500000;
            }
            HardwareControler.setSPIClockDivider(this.spi_fd, divider);
        }
    }

    public void setDataMode(int mode) {
        if (this.spi_fd >= 0) {
            switch (mode) {
                case 0:
                    this.spi_mode &= -4;
                    break;
                case 1:
                    this.spi_mode &= -3;
                    this.spi_mode |= 1;
                    break;
                case 2:
                    this.spi_mode |= 2;
                    this.spi_mode &= -2;
                    break;
                case 3:
                    this.spi_mode |= 3;
                    break;
                default:
                    Log.e(TAG, "error data mode");
                    break;
            }
            HardwareControler.setSPIDataMode(this.spi_fd, this.spi_mode);
        }
    }

    public void setChipSelectPolarity(int cs, int active) {
    }

    public void chipSelect(int cs) {
    }

    public byte transfer(int value) {
        if (this.spi_fd < 0) {
            return (byte) 0;
        }
        return (byte) HardwareControler.SPItransferOneByte(this.spi_fd, (byte) value, this.spi_delay, this.spi_speed, this.spi_bits);
    }
}
