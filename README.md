# R2D2-Neo
Hacked R2D2 deagostini

## Layout

- `apk/` — decompiled Android app (`com.bullb.r2d2_nanopisystem`), the robot's
  original host-side brain
- `info/` — protocol notes (`info/protocol/`) and MCU wiring (`info/mcu/`)
- `sound_effects/` — effect clips and the OpenCV face cascades the app ships
- `r2d2/` — **Python port of the app's host role** (UART to the MCU, WebSocket
  command server, sounds, face detection, behaviour modes). See
  [`r2d2/README.md`](r2d2/README.md) for the protocol tables and the deliberate
  deviations from the original.
- `scripts/` — standalone helpers plus the `r2d2.service` systemd unit
- `tests/` — unit/end-to-end tests for the port (no hardware needed)

```bash
python3 -m r2d2 --mock                              # run without hardware
python3 -m unittest discover -s tests -t tests      # 164 tests
```

Open the browser console at `http://<robot-ip>:8080/` (served automatically;
`--no-web` to disable). Ports the host uses: `8887` command WebSocket,
`12121` video WebSocket, `8090` UDP discovery, `8080` console. 
