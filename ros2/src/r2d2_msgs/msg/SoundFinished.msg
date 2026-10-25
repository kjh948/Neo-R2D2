# Emitted by r2d2_sound when a clip's player process ends (naturally, killed
# by a newer request [last-wins], or via stop). The projector auto-off
# sequence in r2d2_motor correlates on request_id.
std_msgs/Header header

uint16 sound_id
string clip              # resolved file stem that was played
bool interrupted         # true if killed before natural exit
string request_id        # echo of the request, empty for MCU-frame plays
