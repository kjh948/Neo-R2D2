# "Play this clip on the Pi speaker" — republished by r2d2_motor when the MCU
# sends ``{"cmd":"play_sound","sound_id":..,"interrupt":..}``
# (r2d2/mcu_commands.py:75-76 routed it to the local player in the original
# app). Also the request shape used by r2d2_sound's PlaySound service.
std_msgs/Header header

uint16 sound_id          # r2d2/sound.py SOUND_EFFECTS table index
bool interrupt
string request_id        # optional; echoed on SoundFinished for correlation
