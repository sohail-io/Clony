# Clony

Clony creates local app copies using an embedded Android virtualization
runtime. Each clone is installed into its own virtual user and starts with
separate app data; it does not copy the original app's private data. Cloning
and launching happen on the device without a cloning backend.

## Current limits

- The current UI supports one clone per source app. Clone App's multiple-copy
  count, per-copy label/icon editor, and WhatsApp Web action are not implemented.
- The vendored BlackBox Reborn installation path imports the app's base APK.
  Many Play Store apps are split APK installs, and their clones may be incomplete
  or fail to start.
- App compatibility depends on the runtime's Android API and app-specific
  requirements. A clone may need a fresh sign-in, and some permissions or system
  integrations are not forwarded yet.
- On the tested Pixel 8a running Android 16 / API 36, Clony opens but clone
  creation fails: Android blocks hidden activity and package-signing fields
  used by the virtual runtime. TikTok also has many split APKs, while Clony
  imports only the base APK. The current readiness badge can say "ready" even
  though clone hooks are unavailable. See the
  [on-device investigation](reverse-engineering/clone-app/README.md#clony-on-device-runtime-test).
- Clony does not rewrite Play Integrity responses.
- The host target SDK is 28 to support this runtime's hidden-API reflection.
  The build is intended for local research and sideloading, not Play publishing.

## Build

Build the debug APK with `./gradlew :app:assembleDebug`. The vendored runtime
uses Android NDK `28.2.13676358`; Gradle resolves its Android and annotation
processor dependencies through Google Maven and Maven Central.

See [third-party notices](THIRD_PARTY_NOTICES.md) for licensing and runtime
modifications.
