# Clony

Clony creates local app copies using an embedded Android virtualization
runtime. Each clone is installed into its own virtual user and starts with
separate app data; it does not copy the original app's private data. Cloning
and launching happen on the device without a cloning backend.

Each clone can also have a separately installed **Clony Copy N** launcher app
in Android's app drawer. That small companion package contains only a launcher
stub; it opens the assigned virtual copy through Clony. The original app is
still running inside BlackBox, so the companion app does not create a separate
Android security sandbox or change the guest app's package identity. Android
will ask to allow installs from Clony and confirm launcher installation.

## Current limits

- Clony supports up to 10 virtual copies per source app and up to 100 distinct
  companion launcher packages. Per-copy label/icon editing and WhatsApp Web
  actions are not implemented.
- The runtime imports the installed base APK and split APKs. App compatibility
  still depends on the package format and the runtime's Android API support.
- App compatibility depends on the runtime's Android API and app-specific
  requirements. A clone may need a fresh sign-in, and some permissions or system
  integrations are not forwarded yet.
- An earlier on-device investigation on a Pixel 8a running Android 16 / API 36
  found clone-creation failures related to hidden activity and package-signing
  fields used by the virtual runtime. The installer now imports split APKs too,
  but API 36 runtime compatibility still needs on-device verification. The
  readiness badge can say "ready" even though clone hooks are unavailable. See the
  [on-device investigation](reverse-engineering/clone-app/README.md#clony-on-device-runtime-test).
- Clony does not rewrite Play Integrity responses.
- The host target SDK is 28 to support this runtime's hidden-API reflection.
  The build is intended for local research and sideloading, not Play publishing.

## Build

Build the debug APK with `./gradlew :app:assembleDebug`. The build generates
100 signed launcher packages using the machine's Android debug keystore. The
vendored runtime uses Android NDK `28.2.13676358`; Gradle resolves its Android
and annotation processor dependencies through Google Maven and Maven Central.

See [third-party notices](THIRD_PARTY_NOTICES.md) for licensing and runtime
modifications.
