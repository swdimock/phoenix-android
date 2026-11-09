# Real Android Auto inside the emulator (TCP head unit server)

The emulator has no USB phone, so the receiver (our Open Headunit fork, `com.andrerinas.headunitrevived`) talks
to the Android Auto app on your phone over TCP, using Android Auto's developer **head unit server** on port
5277. The chain is:

    receiver in emulator --TCP--> 10.0.2.2:5277 (= the PC) --adb forward--> phone:5277 (head unit server)

`10.0.2.2` is the emulator's fixed alias for the host PC's loopback. On the real head unit the transport is
USB and none of this applies.

## One-time setup

### Phone

1. Update **Android Auto** from the Play Store (old versions lack or break the head unit server).
2. Open Android Auto settings: on Android 10+ that is *Settings > Connected devices > Connection preferences >
   Android Auto* (or search for "Android Auto" in Settings).
3. Scroll to the bottom and tap **Version** about 10 times until "Allow development settings?" appears; accept.
4. Enable **USB debugging**: *Settings > About phone*, tap *Build number* 7 times, then
   *Settings > System > Developer options > USB debugging* on.

### PC / emulator

```bash
cd /c/Users/Stephen/Documents/projects/phoenix-android && source scripts/env.sh
bash scripts/build-receiver.sh                 # builds ../open-headunit (branch phoenix) -> build/receiver/
ANDROID_SERIAL=emulator-5554 bash scripts/install-all.sh   # installs everything incl. the receiver
```

In the launcher, *Settings > Android Auto transport* should show **TCP** with `10.0.2.2` / `5277`
(this is the default on the emulator; USB is the default on real hardware).

## Every session

1. Plug the phone into the PC by USB and accept the "Allow USB debugging?" prompt on the phone.
2. With **two** adb devices attached (phone and emulator), every adb command needs `-s`:

   ```bash
   adb devices                                   # e.g. emulator-5554 and R5CT1234ABC
   ```

3. On the phone: Android Auto settings > three-dot menu (top right) > **Start head unit server**.
   A notification "Head unit server running" appears.
4. Forward the port. The forward is created on the PC side for the **phone** serial:

   ```bash
   adb -s <phone-serial> forward tcp:5277 tcp:5277
   adb -s <phone-serial> forward --list          # should list: <phone-serial> tcp:5277 tcp:5277
   ```

5. Open the launcher's Android Auto pane and tap **Open Android Auto** (on a fresh launcher start it opens by
   itself once the audio path is ready). The receiver connects to `10.0.2.2:5277`, retrying 5 s after each failed attempt (about every 10 s on the
   emulator, where a refused connection takes ~5 s to surface through its NAT), and
   shows "Android Auto: waiting for phone at 10.0.2.2:5277 (attempt N ...)" until the phone answers.
   Accept any prompt on the phone; the projection then fills the screen above the 227 px climate bar.
6. The round button at the top-left of the receiver goes back to the launcher (system HOME). The receiver
   stops retrying while it is in the background; tap **Open Android Auto** to resume.

Manual launch without the launcher (same extras the launcher sends):

```bash
adb -s emulator-5554 shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity \
  --es phoenix.transport tcp --es phoenix.host 10.0.2.2 --ei phoenix.port 5277 --ei phoenix.inset_bottom 227
```

![Receiver waiting for the phone](screenshots/aa-receiver-waiting.png)

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Receiver keeps saying "waiting", logcat shows `ECONNREFUSED` | Nothing is listening on the PC's 5277: the forward is missing (`adb -s <phone> forward --list`) or the head unit server is not running on the phone. |
| Connects, then drops after a few seconds | The phone's screen turned off or Android Auto was swiped away; the head unit server stops with it. Keep the phone unlocked and screen on (Developer options > *Stay awake* while charging). |
| "Start head unit server" missing from the menu | Developer settings not enabled (tap Version 10x again) or Android Auto is outdated. |
| `adb: more than one device/emulator` | Add `-s emulator-5554` or `-s <phone-serial>`, or `export ANDROID_SERIAL=...`. |
| Forward disappears | It is tied to the adb server and the phone's USB link: re-run it after replugging the phone or `adb kill-server`. |
| Port 5277 already in use on the PC | Something else holds it (a desktop head unit, an old forward): `adb forward --remove-all`, or forward another PC port (`adb -s <phone> forward tcp:5300 tcp:5277`) and set the launcher's port to 5300. |
| Black or stuttering video | The emulator uses MediaCodec only (no FFmpeg software decoder for x86_64). Use a host-GPU emulator (`-gpu host`); real hardware is unaffected. |
| No sound | Expected until the launcher's audio path is READY: the receiver holds audio while it receives `AUDIO_PATH ready=false`. The header chip on the launcher shows the state. |
| Phone shows nothing / session never starts after the TCP connect | First connection to a new "car": unlock the phone and accept Android Auto's prompts (new car, permissions). If it still stalls, in Android Auto developer settings check *Unknown sources* is on, then stop and restart the head unit server. |
| Launcher pane says receiver not installed | `bash scripts/build-receiver.sh` then re-run `install-all.sh`, or check the receiver package in Settings. |

Useful logs: `adb -s emulator-5554 logcat | grep -E "OPENHU|Phoenix"`.
