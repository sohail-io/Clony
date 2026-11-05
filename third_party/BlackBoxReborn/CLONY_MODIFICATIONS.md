# Clony changes to BlackBox Reborn

Source: <https://github.com/fdavids77/BlackBoxReborn> (the inspected `main`
snapshot; an upstream commit hash was not available in the local source
archive). Upstream `README.md`, `COMPAT.md`, `CHANGELOG.md`, and `LICENSE` are
kept alongside the vendored modules.

Clony changes:

- The Bcore manifest no longer requests the upstream's broad set of privileged,
  telephony, account, location, camera, microphone, storage, overlay, and other
  unrelated permissions. It keeps only internet, network-state, Wi-Fi-state,
  wake-lock, and foreground-service declarations for the basic local runtime.
- Clony's host manifest separately declares camera, microphone, shared-media,
  audio-settings, and vibration permissions needed by content creation. Runtime
  camera, microphone, and media checks and package permission flags reflect the
  real host grant; they no longer synthesize microphone or media grants. Modern
  permission-manager queries map the current clone package to the host package.
- Android 17 service-cache access uses a checked JNI bridge limited to the
  permission and vibrator caches. Activity fragment-controller access is
  read-only. No raw object offsets are used. Reused activities receive new
  intents through public Instrumentation APIs on Android 12+, with platform
  fragment state reset before callbacks; the old token-based ActivityThread
  entry points silently missed post-completion navigation on current Android.
- System-provider queries and file opens rebuild the real host AttributionSource
  through its public builder, as provider call() already did. Pictures path
  exceptions preserve the full filename and check directory boundaries.
- Internal activity, service, receiver, and content-provider proxy stubs are
  non-exported. Proxy job services retain `BIND_JOB_SERVICE` so Android's job
  scheduler can bind them. The VPN service remains non-exported and protected
  by `BIND_VPN_SERVICE`; Clony does not request VPN networking.
- The Play Integrity token bridge installation and service-connection wrapper
  were removed. Clony does not rewrite Integrity responses.
- Upstream startup collected system-wide logcat into shared Downloads, and its
  crash hooks could upload logcat, device details, and crash captions to a
  hard-coded remote service. Clony disables log collection and makes the upload
  API a no-op.
- The library manifest no longer opts the app into cleartext network traffic.
- The vendored Bcore module uses the locally installed NDK `28.2.13676358` and
  the AIDL package list is expressed with the AGP 9.4 library DSL.
- The generated-AIDL comment rewriting task was removed. It addressed Windows
  path escaping, while Clony's supported local build host is macOS; removing it
  also keeps the Gradle configuration cache usable.
- The upstream `closecode.Entry` native-library injection hook was removed. The
  unused Material, TOML4J, and FreeReflection dependencies were removed;
  AndroidX AppCompat was updated to `1.7.1` and ConstraintLayout `2.2.0` was
  added because vendored resources require their theme and layout attributes.
  Prefab was disabled because the native build has no Prefab modules. This
  removes the JitPack requirement. If native hidden-API setup
  fails on a device, the runtime logs a compatibility warning rather than
  loading a fallback reflection library.
- The ART field bridge has an Android 16 path that avoids treating the opaque
  `jfieldID` returned by JNI as a raw `ArtField*`. Device testing confirms this
  gets native initialization past the original ART-field crash. It does not
  make the later BlackBox activity hooks compatible: Android 16 still denies
  `ActivityThread.H.EXECUTE_TRANSACTION` to this target-SDK-28 app.
- Clone launch provisions the device's installed Google Play Services into the
  virtual user with its base and split APKs, so guest Google Sign-In requests
  resolve through the clone runtime. When virtual GMS is present, auth and
  token calls are forwarded as-is; the runtime does not fabricate sign-in
  success or tokens. The guest's `SignInHubActivity` is allowed to run and hand
  off to virtual GMS.
- Virtual storage keeps Android's logical `/storage/emulated/<host-user>` mount
  on the returned `StorageVolume`, while IOCore redirects file I/O to each
  virtual user's private backing directory. Android 17's
  `Environment.isExternalStorageEmulated()` requires that logical mount to
  match a returned volume and throws if it is replaced with Clony's backing
  path.
- Package-install exceptions now mark `InstallResult` as failed, and Clony
  handles a null result when the helper process dies. `isInstalled` now returns
  unknown/false when the virtual package service is unhealthy instead of
  consulting the host package manager, which had falsely labeled every host
  app as a clone.

- The runtime launch screen shows an indeterminate progress indicator and resolves
  the app label/icon from the installed source app during startup. Clony hands
  off to this screen without waiting for guest instrumentation callbacks.

These edits narrow the runtime for Clony's local app-copy feature. They can
reduce compatibility with apps that require a permission or system integration
not declared by Clony.
