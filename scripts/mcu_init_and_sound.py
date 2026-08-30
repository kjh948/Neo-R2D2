#!/usr/bin/env python3
"""
Send initialization to MCU and request a startup sound.

Sends JSON messages over serial to `/dev/ttyAMA0` (115200) by default:
 - {"cmd":"ready"}\n
 - {"cmd":"play_sound","soundId":9}\n
This script sends the `play_sound` request to the MCU (not the host) by default.
Optionally, a local sound can be played with `--local-sound`, but local playback is disabled by default.
"""
import argparse
import json
import time
import sys
import os
import subprocess

try:
    import serial
except Exception as e:
    print("pyserial is required. Install with: pip install pyserial")
    raise


def send_json(ser, obj):
    s = json.dumps(obj) + "\n"
    ser.write(s.encode("utf-8"))
    ser.flush()
    print("Sent:", s.strip())


def play_local_sound(path):
    if not path:
        # simple beep fallback
        print("Playing system bell")
        sys.stdout.write("\a")
        sys.stdout.flush()
        return
    if not os.path.exists(path):
        print("Sound file not found:", path)
        return
    try:
        if sys.platform.startswith("darwin"):
            subprocess.run(["afplay", path])
        elif sys.platform.startswith("linux"):
            subprocess.run(["aplay", path])
        elif sys.platform.startswith("win"):
            # Windows: use PowerShell to play
            subprocess.run(["powershell", "-c", f"(New-Object Media.SoundPlayer '{path}').PlaySync();"], shell=True)
        else:
            print("No known player for this platform. Printing bell.")
            sys.stdout.write("\a")
            sys.stdout.flush()
    except Exception as e:
        print("Failed to play local sound:", e)


def main():
    p = argparse.ArgumentParser(description="Send MCU init and sound commands over serial")
    p.add_argument("--port", default="/dev/ttyAMA0", help="Serial device (default: /dev/ttyAMA0)")
    p.add_argument("--baud", type=int, default=115200, help="Baud rate (default: 115200)")
    p.add_argument("--sound-id", type=int, default=9, help="sound id to request from MCU (default: 9)")
    p.add_argument("--interrupt", type=int, default=1, help="Optional interrupt flag passed to MCU (default: 1)")
    p.add_argument("--local-sound", help="Optional local sound file to play after sending (disabled by default)")
    p.add_argument("--no-mcu", action="store_true", help="Don't open serial port; only play local sound if provided")
    args = p.parse_args()

    if not args.no_mcu:
        try:
            ser = serial.Serial(args.port, args.baud, timeout=1)
        except Exception as e:
            print(f"Failed to open serial port {args.port}: {e}")
            sys.exit(2)

        try:
            # send ready
            send_json(ser, {"cmd": "ready"})
            time.sleep(0.05)
            # request MCU to play a startup sound (use mapping 'soundId' to target MCU)
            play_cmd = {"cmd": "play_sound", "soundId": args.sound_id}
            if args.interrupt is not None:
                play_cmd["interrupt"] = args.interrupt
            send_json(ser, play_cmd)
        finally:
            try:
                ser.close()
            except Exception:
                pass
    else:
        print("Skipping MCU (--no-mcu)")

    # Local playback only if explicitly requested
    if args.local_sound:
        play_local_sound(args.local_sound)


if __name__ == "__main__":
    main()
