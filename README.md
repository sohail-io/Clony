# TikViral

TikViral creates local app copies using an embedded Android virtualization
runtime. By default, each copy is installed into its own virtual container.
You can also add another installed app to an existing container. Apps in the
same container share its virtual account services, while each package keeps
separate app data. Cloning does not copy the original app's private data, and
happens on the device without a cloning backend.

Copies keep the source app's icon and use numbered names such as **TikTok 1**
and **TikTok 2** in TikViral and on the Android home screen. Each shortcut opens
its assigned copy through TikViral. The original app is still running inside
BlackBox, so the shortcut does not create a separate Android security sandbox
or change the guest app's package identity.

## Current limits

- TikViral supports up to 10 virtual copies per source app. Home-screen shortcuts
  use the source app's icon and a numbered name. Older copies may still have
  the generic companion launcher installed by previous versions.
- The runtime imports the installed base APK and split APKs. App compatibility
  still depends on the package format and the runtime's Android API support.
- App compatibility depends on the runtime's Android API and app-specific
  requirements. A clone may need a fresh sign-in, and some permissions or system
  integrations are not forwarded yet.
- When Google Play Services is installed on the device, Clony installs the real
  Play Services and Google Services Framework packages into each virtual user
  for sign-in compatibility. If that setup is unavailable, Clony reports it
  after clone creation; apps that require those services may not work.
- Clony does not solve or suppress reCAPTCHA challenges, or rewrite Play
  Integrity results. Complete CAPTCHA challenges normally when they are shown.
  Apps may still reject a clone when their checks require the original package
  or signing certificate.
- An earlier on-device investigation on a Pixel 8a running Android 16 / API 36
  found clone-creation failures related to hidden activity and package-signing
  fields used by the virtual runtime. The installer now imports split APKs too,
  but API 36 runtime compatibility still needs on-device verification. The
  readiness badge can say "ready" even though clone hooks are unavailable. See the
  [on-device investigation](reverse-engineering/clone-app/README.md#clony-on-device-runtime-test).
- The host target SDK is 28 to support this runtime's hidden-API reflection.
  The build is intended for local research and sideloading, not Play publishing.

## Build

Build the debug APK with `./gradlew :app:assembleDebug`. The build keeps the
signed launcher packages used by copies created with older Clony versions.
New copies use Android home-screen shortcuts. The vendored runtime uses Android
NDK `28.2.13676358`; Gradle resolves its Android and annotation processor
dependencies through Google Maven and Maven Central.

See [third-party notices](THIRD_PARTY_NOTICES.md) for licensing and runtime
modifications.
