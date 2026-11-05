# Camera, gallery and post-completion investigation — 2026-11-04

Test device: connected Pixel 8a, Android 17 / API 37. Host: `com.clony`,
target SDK 28. Tested guest: TikTok (`com.zhiliaoapp.musically`), virtual user 2.

## Reproduced failures and changes

1. Opening Create showed a camera/microphone Settings prompt and then crashed.
   The captured fatal exception was `SecurityException` for missing `VIBRATE`.
   Clony also lacked camera and microphone declarations, and CameraService
   rejected its real UID. Added the host permissions and preserved actual OS
   camera/microphone/media grants instead of synthesizing grants.
2. After adding permissions, vibration failed with `UPDATE_APP_OPS_STATS`:
   a framework Vibrator retained a source/virtual UID. The runtime now pairs
   vibration calls with Clony's real UID. Android 17 blocks the original hidden
   Stub and named/enumerated cache lookups; a narrow checked-JNI bridge installs
   the permission/vibrator proxies in their framework caches.
3. Open Settings now reaches Clony's real settings. A live cloned camera preview
   and CameraService streaming under Clony's UID were observed. See
   `camera-after.png` and `camera-after-log.txt`.
4. Android's photo/video grant alone left this target-28 host's legacy Files
   permission denied, and MediaStore returned zero rows. The user explicitly
   approved the separate Files grant; it was enabled through Android Settings.
   System-provider queries/file opens also now rebuild the host AttributionSource
   through the public builder. The existing Pictures path exception now keeps
   the complete filename and respects directory boundaries.
   Final UI verification after the approved Files grant showed 37 image nodes
   in the gallery and no empty-gallery message (`gallery-after-files-grant.png`).
5. The later posting screen remained visible, appearing to have failed. Reading
   only status fields from the guest's cached publishing response found
   `status_code=0`, `error_code=0`, and two `publishStatus=SUCCESS` records.
   The current desk photo was published at **11:18:12 Asia/Karachi**; an earlier
   selfie was published at **11:16:35**. Both are visible on the signed-in
   profile (`published-profile.png`). They are different photos.
6. The log shows `CLEAR_TOP | SINGLE_TOP` routing back to the reused MainActivity.
   The old runtime tries token-based hidden ActivityThread new-intent entry
   points; current Android instead takes an ActivityClientRecord, and filtered
   lookups previously ended without delivering anything. Android 12+ now uses
   public `Instrumentation.callActivityOnNewIntent`, resetting platform fragment
   saved state first. A read-only Activity FragmentController bridge supports
   that reset. This addresses the lost post-completion navigation without
   changing publishing requests or server responses.

## Validation and limits

- Debug app and instrumentation APK builds pass.
- **17 focused device tests pass**: capture permissions, host package/UID mapping,
  actual framework proxy installation, provider attribution, Pictures path
  preservation, new-intent extras/flags/exactly-once delivery, and fragment
  saved-state reset. Output: `permission-tests.txt`.
- Published-photo confirmation comes from both cached status records
  (`post-status.json`, stripped of credentials) and the profile UI.
- No additional post was published to test the navigation patch. Its delivery
  and lifecycle behavior were validated by instrumentation. The two approved
  monitored taps on the already-published composer produced no new upload event.
- The final debug APK is installed. Clone data and sign-in were preserved.

REA crash observations: `ev_9a2c8b2bbee1a3057fa09badc48eebcb37388ae645aadc66a5cff01485396dd6`
(missing VIBRATE) and
`ev_cbb81f3f6a0a88557720cd104fabf508759dd1c182f4a42c3e5ccfd9fcd4c2f5`
(vibration UID mismatch after manifest update). These are bounded log captures;
the screenshots and focused later log contain the successful camera observation.
