============================================================
Waveshare UGV General Driver (ESP32) GPIO Assignment
============================================================

CPU
----
ESP32-WROOM-32

------------------------------------------------------------
UART
------------------------------------------------------------

UART0 (Communication with Raspberry Pi)
TX : GPIO1
RX : GPIO3
Baud : 115200 (Default)

Connection
ESP32 GPIO1 (TX) --> Raspberry Pi GPIO15 (RXD0)
ESP32 GPIO3 (RX) <-- Raspberry Pi GPIO14 (TXD0)

------------------------------------------------------------

UART1 (Bus Servo)
TX/RX : GPIO27
DIR    : GPIO26

Used for ST3215 serial bus servo.

------------------------------------------------------------

UART2 (LiDAR)
TX : GPIO25
RX : GPIO4

------------------------------------------------------------
I2C
------------------------------------------------------------

SDA : GPIO21
SCL : GPIO22

Devices
- QMI8658 IMU
- AK09918 Magnetometer
- INA219 Power Monitor

------------------------------------------------------------
Motor Driver (TB6612)
------------------------------------------------------------

Motor A PWM : GPIO16
Motor A IN1 : GPIO17
Motor A IN2 : GPIO18

Motor B PWM : GPIO19
Motor B IN1 : GPIO23
Motor B IN2 : GPIO5

------------------------------------------------------------
Encoder
------------------------------------------------------------

Left Encoder
A : GPIO34
B : GPIO35

Right Encoder
A : GPIO32
B : GPIO33

------------------------------------------------------------
PWM Servo
------------------------------------------------------------

Servo1 : GPIO13
Servo2 : GPIO12
Servo3 : GPIO14
Servo4 : GPIO15

------------------------------------------------------------
SPI (Micro SD)
------------------------------------------------------------

CLK  : GPIO14
MOSI : GPIO13
MISO : GPIO2
CS   : GPIO15

------------------------------------------------------------
Raspberry Pi Connection
------------------------------------------------------------

Raspberry Pi GPIO14 (TXD0)
        |
        +------> ESP32 GPIO3 (RX0)

Raspberry Pi GPIO15 (RXD0)
        |
        +<------ ESP32 GPIO1 (TX0)

Raspberry Pi GPIO2 (SDA1)
        |
        +------> ESP32 GPIO21 (SDA)

Raspberry Pi GPIO3 (SCL1)
        |
        +------> ESP32 GPIO22 (SCL)

------------------------------------------------------------
Power
------------------------------------------------------------

Battery
      │
      ├── DC Motor Driver
      ├── Servo Power
      ├── ESP32
      └── 5V DC-DC → Raspberry Pi

------------------------------------------------------------
GPIO Usage Summary
------------------------------------------------------------

GPIO1   UART0 TX (Pi)
GPIO3   UART0 RX (Pi)

GPIO4   LiDAR RX
GPIO5   Motor B IN2

GPIO12  PWM Servo2
GPIO13  PWM Servo1

GPIO14  PWM Servo3 / SPI CLK
GPIO15  PWM Servo4 / SPI CS

GPIO16  Motor A PWM
GPIO17  Motor A IN1
GPIO18  Motor A IN2
GPIO19  Motor B PWM

GPIO21  I2C SDA
GPIO22  I2C SCL
GPIO23  Motor B IN1

GPIO25  LiDAR TX
GPIO26  Servo Direction
GPIO27  Bus Servo UART

GPIO32  Encoder Right A
GPIO33  Encoder Right B
GPIO34  Encoder Left A
GPIO35  Encoder Left B

============================================================
Reserved GPIO
============================================================

GPIO1
GPIO3
GPIO5
GPIO16~19
GPIO21
GPIO22
GPIO23
GPIO25
GPIO26
GPIO27
GPIO32~35

These GPIOs are already used by the default firmware.

============================================================
Available GPIO
============================================================

GPIO0

(Additional GPIOs may be available only if the corresponding peripherals
such as LiDAR, Bus Servo, or SD Card are not used.)
============================================================