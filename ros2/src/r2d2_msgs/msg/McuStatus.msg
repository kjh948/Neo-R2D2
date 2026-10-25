# One snapshot of the MCU ``gin`` status frame (r2d2/models.py GinResponse),
# plus the bridge's own boot ACK. Published by r2d2_motor on /r2d2/status.
std_msgs/Header header

uint8 CHARGING_NOT_CHARGED=0
uint8 CHARGING_CHARGING=1
uint8 CHARGING_CHARGING_FINISHED=2

int16 battery            # percent, -1 = unknown (gin default)
uint8 charging           # CHARGING_* above; debounced copy of charging-status
bool arm                 # true = extended
bool lightsaber          # true = on
uint8 projector          # 0 off, 1/2 = sequences (bridge owns auto-off)
bool lcd_short_open      # lcd_s >= 2 in the original protocol
bool lcd_long_open       # lcd_l >= 2
uint8 mode               # last reported animation mode (informational)
int32 head               # last reported head angle (informational)
string error             # MCU error string; "NO ERROR" when clean
bool mcu_ready           # ready ACK received since bridge start
