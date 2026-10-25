# Physical button-panel press from the MCU ``{"cmd":"btn","value":N}`` frame
# (r2d2/mcu_commands.py:91-108). The bridge only republishes; acting on it is
# the behavior layer's job (power_off is deliberately NOT auto-handled).
std_msgs/Header header

uint8 BTN_POWER_OFF=1
uint8 BTN_AP_TOGGLE=2
uint8 BTN_PAIRING=3
uint8 BTN_LIGHTSABER=4
uint8 BTN_ARM=5
uint8 BTN_PATROL=6

uint8 value
