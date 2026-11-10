# Focused virtual runtime trace

Clony emits opt-in `ClonyVirtualRuntime` events from the virtual app process. Events include runtime binding, WebView data-directory setup, virtual activity creation/start/resume/destruction and dispatch, identity-hook reads, network-capability checks, virtual user ID, process/PID, and exception class. They do not include form contents, email addresses, codes, cookies, URLs, headers, or request bodies. Activity events can reveal duplicate navigation, but they do not count TikTok's internal authentication requests or individual screen taps.

Enable the tag on the connected device:

```sh
adb -s <serial> shell setprop log.tag.ClonyVirtualRuntime DEBUG
```

Find the virtual TikTok process. Match its Android UID to the `com.clony` UID so the native TikTok installation is not selected by mistake:

```sh
adb -s <serial> shell ps -A -o USER,PID,NAME | grep -E 'com\.clony|com\.zhiliaoapp\.musically'
```

Capture only Clony's safe runtime events for that PID:

```sh
adb -s <serial> logcat -v threadtime --pid <virtual-tiktok-pid> ClonyVirtualRuntime:D '*:S'
```

To inspect TikTok's own warning/error tags as well, read the process log through REA and redact email addresses, URLs, tokens, cookies, and codes before sharing. TikTok's own log lines are outside Clony's payload-free logging guarantee.

Disable the focused tag after capture:

```sh
adb -s <serial> shell setprop log.tag.ClonyVirtualRuntime INFO
```
