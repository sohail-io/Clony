# Clone App reverse-engineering notes

## Target

- Device: Google Pixel 8a (`akita`), Android 16 / API 36, connected over USB.
- Installed app: `com.pengyou.cloneapp`, version `4.2.2` (version code `820`), installer `com.android.vending`.
- Pulled APK set: [base.apk](com.pengyou.cloneapp/base.apk), [arm64 split](com.pengyou.cloneapp/split_config.arm64_v8a.apk), and [xxhdpi split](com.pengyou.cloneapp/split_config.xxhdpi.apk).
- Base APK SHA-256: `cc1d5669ebf6af90d02be966cbe663a005cf87f188a3e822b014a616c3b2b5b0`.
- arm64 split SHA-256: `84db215c9c2fb880fd273686ea732689bf20e8e5a3c45c459bba04758ed3b898`.
- xxhdpi split SHA-256: `c762f6db84a118d1d32550406d120b7ed1a94045085bf411041fbadd6c812ece`.
- JADX 0.7.1 analyzed the base APK: 25,654 classes and 1,360 resources. APK signature verification was not performed.

REA Evidence IDs: device/package `ev_3fc2c13251e8a86d91041fb593d216e1a8c994084209a341ffcd913be4436033`, `ev_e26bd9c214976944b1be50355f2a8cdeba22ee8f56c1236ae445b80c86b24905`; APK pull `ev_627ef33599783fb4fb98158f1647ae8257945ed51ff944ea60b41451ed3c020a`; APK inventory `ev_973146b3b2b5c7b08f40fd96aa2e7581a1fc3ba3469f7ceddc17549ac8bad3c9`.

## Observed screens

The app opened to a home screen with an existing TikTok clone and a floating plus button. Pressing plus opened **Choose APP**, which lists recently used apps and an alphabetical installed-app list. Selecting WhatsApp opened **Modify name/icon** with a name field, icon editor, clone count set to 1, plus/minus controls, a **Using WhatsApp Web** action, and a **Clone** button.

- [Home screen capture](screens/main/screen.png) — Evidence `ev_266fa93033167e1d3a4011d5b823ad715e05ecabe1d78bbd2adb7eecd1fbf874`.
- [App picker capture](screens/select-app/screen.png) — Evidence `ev_72ff1b06135d7d00362e18a7c06895b10e6a26ce83a396d3481799b2d05ac718`.
- [WhatsApp configuration capture](screens/config-whatsapp/screen.png) — Evidence `ev_3eb1667760701ca27e5a2a0e64b45c04dd43ce5967849f0803595b9c9909ff63`.

The final **Clone** action was not pressed, so the device did not create another clone during this investigation.

## Static creation flow

1. `MainActivity.R()` opens `SelectBeClonedActivity` for result. Its initialization configures the searchable app list, loads recent-clone data, and requests `GET_INSTALLED_APPS` on Android 12+ when that permission exists. It then loads installed-app entries asynchronously. Evidence: `ev_81e2f11574ac859533d1a8c32cba4036710460502920a125f34f1c748b292ecb`, `ev_a2246342984a78f9f9eedf4da21ce7f103ed7fcdac26ffdf12701c9b958e7f1e`, `ev_ad2f73505797afc97d917ac6aa68df3236156d4273d4dadcd1d8e76ba9b33892`.
2. `SelectBeClonedActivity.z(x63)` resolves the selected package, checks whether a secure environment is required, rejects unsupported 32-bit apps, then opens `CustomCreateAppActivity`. Evidence: `ev_72d4245b61cc60d527ececdde1dedb4b17260d13b1ee6ab85d6556d112e1d07d`; ABI predicate `vm0.k` is in `ev_a8e062e7d95a02ae8b766d0c0ad2964496332e274b4adfab04a0b9d525e07a88`.
3. `CustomCreateAppActivity.y(boolean)` validates the name, stores the requested copy count in the app model, optionally writes a changed icon to a temporary PNG, and returns the model to the caller. The count editor clamps values to 1–10 (`ev_42f5768c4c2195da636ec9d1cba2e1a18dc1d9c9ba1f45426fba24dc72ff2483`). The WhatsApp Web action changes the selected package marker to `com.py.cweb`. Evidence: `ev_59e04e21f537d16694e76d8eab57d5d35d7143e18efe2c97ee3d0e0fa8595`.
4. `MainActivity.onActivityResult()` receives `apkInfo` and calls `U(...)`. `U` schedules one copy by default. For a requested count above one, it checks a feature/entitlement flag and the remaining capacity (`103 - yy.k().q()`), capped by the requested count. Evidence: `ev_e87b71acd970f75750a06f1ae962503051568418cbd4944f78395f3c3cffe9c8`, `ev_14c7d9a8db4a764245bad3857963479dfcadc792c3862828b0aaac77c4919c6d`; the virtual package count is computed by `yy.q()` (`ev_5d249ad9a385835bdfbc51fba92ed4c830cb12eb94882ec46c5cb1f703608d78`).
5. The worker `vm2.run()` calls `MainActivity.F()` for a single copy; `xm2.run()` calls it sequentially for a batch. Evidence: `ev_5848931eba5db1ecc93821caf82c7855cc945797b37a45fd356c91af2b52e509`, `ev_74bce896c15f0ffdf198a821ed570377a5d79027e3f548058dcd7cdd67d7174e`.
6. `MainActivity.F()` calls the `a12.A(...)` Binder API with the package, clone label, mode flag, optional APK path, slot information, and model flags. The Binder manager is constructed with the service name `chaos.service.plugin`; success triggers icon handling and app-list updates. Evidence: `ev_5aa5e67f317e759589e1b9b77a10b91d99be0623889b2f4044d3c0a0c2502c59`, `ev_f78ebf6218cc4a612354ecd6efac9b296bfd5d437df54e0137b207452d43d434`, interface inventory `ev_3d26f446ba01d523e71e2ebd6c965282e47b4b0fc1b72eb876940ecb82596a14`.

## Architecture and limits

The APK contains the `com.bly.chaos` host/runtime namespace, `InstrumentationProxy`, and families of stub activities, services, and providers. `CRuntime` exposes host-process and plugged-package identity fields. Together with the Binder route above, this is strong static evidence that Clone App runs copies inside an embedded virtual-app runtime rather than asking Android to install each copy as a separate package. Evidence: class inventory `ev_1bfecf0a720ce7783536ddbd1bac3c3401a209544df3935693fd0650e3ce1153`; runtime class `ev_081f92924c86cb8a5b360d4b0cc03b029e3f58c8f708c25a7413aa7330ca4fd2`; existing virtual-package lookup `ev_629d52c4cb41eae0d38fcd39c7bf303a4939d6695ca7d1961def6b0b08bcaaae`.

This is a static architectural inference, not proof of the exact internal isolation guarantees. JADX inspected the base APK; split APK code, native libraries, signatures, and a Clone App clone launch were not analyzed. The reference app's live exploration stopped at the configuration screen.

## What the reference tells us about TikTok

The reference APK is `com.pengyou.cloneapp` 4.2.2, min SDK 32 and target SDK 36, with 25,654 classes. The `com.bly.chaos` classes include an embedded runtime, `InstrumentationProxy`, activity/service/provider stubs, a native-hook bridge (`CNative`), and a custom `CPackageInstallerSession` exposing split-oriented operations such as `openWrite`, `removeSplit`, and `isMultiPackage`. This is a separate virtualization engine, not a configuration change to BlackBox. Evidence: package summary `ev_634bc81f2123cbeeae2ce22814d71f465fe78547182a17df8e9539b9953c81e`; class search `ev_4e0748f0597c17c926fc97743cb09770ef9fe51e5b134d5e4a686202d0d3c092`; `InstrumentationProxy` `ev_5ec6b7809c7c53e0ff82613c14be87a6b9bdf10b10631582c60b3428f77bc7cd`; `CNative` `ev_560be9fa747cc6e5b5d1647b4198f70648a26ac8da173b5da1148f5c45077475`; installer session `ev_732266ce16c96ad75ab6b585dfc9b473a7ccd8ec74b0aa03fca6871854ec49d0`.

The previously captured home screen contains a TikTok tile. The reference app's **Clone** action was not pressed. Its architecture and UX informed Clony's implementation, but this investigation does not establish that Clone App's own TikTok instance works on this device.

## Initial Clony failure and required fixes

The failure is not just one missing signing field:

1. On Android 16, the virtual helper crashes when `HCallbackProxy` reads the hidden `ActivityThread.H.EXECUTE_TRANSACTION` field. This happens before Clony can reliably service virtual app installs.
2. The TikTok install then fails at `BPackage.SigningDetails` when it reads hidden `PackageParser.SigningDetails.pastSigningCertificates`.
3. Even after removing that read, the API 33+ parser path is incomplete. It calls public `getPackageArchiveInfo()`, creates a new `PackageParser.Package`, and sets only `applicationInfo`, signatures, and requested permissions. It never fills the activities, services, receivers, providers, or intent filters that `BPackage` copies from that object. The upstream compatibility note itself says archive info omits metadata such as intent filters and calls for AndroidManifest parsing, but the shipped `PackageParserFix` does not implement that parser. Local code: `third_party/BlackBoxReborn/Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageManagerService.java` and `third_party/BlackBoxReborn/Bcore/src/main/java/top/niunaijun/blackbox/utils/compat/PackageParserFix.java`.
4. TikTok is installed as a base APK plus split APKs, while Clony imports only the base. A working virtual installer must preserve and load the base and required splits together.

The fix needs to preserve Android's installed package metadata and splits, avoid the blocked hidden fields on API 33+, and keep the runtime service process alive on Android 16. Clone App's proprietary runtime demonstrates the scale of a virtual-app layer, but is not a drop-in source for Clony. Android's official non-SDK policy explains why these internal APIs are conditional and can break across releases: [Restrictions on non-SDK interfaces](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces).

Changing the device's global hidden-API policy would only hide these symptoms on a development device; it would not populate missing package components or import TikTok's splits, so it is not the fix.

## Clony implementation

The original Clony version used `DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE` and directed the user to install apps inside Android's Work Profile. That was a different backend from Clone App's embedded runtime.

Clony vendors the `Bcore`, `black-reflection`, and `compiler` modules from BlackBox Reborn as an open-source virtualization engine. The installed-app import path now builds its virtual package from Android's public `PackageInfo`, copies the base APK and installed splits, and loads the full APK path set in the virtual class loader. It queries launch and info entry points from the host PackageManager. This follows the same broad embedded-virtualization approach suggested by the observed `com.bly.chaos` namespace and `chaos.service.plugin` Binder route, but does not reuse Clone App's proprietary implementation or establish equivalent isolation or compatibility.

The current Clony UI supports up to ten copies per source package. The app picker follows Clone App's observed 1–10 quantity control; each copy receives its own BlackBox virtual user ID and separate app data, and every saved copy is listed with its own Open action. Clony does not copy Clone App's premium entitlement gate. Per-copy label/icon editing and the WhatsApp Web action are not implemented. The package model imports public component metadata and launcher/info filters; it does not recover every original manifest intent filter, so implicit receivers and services may still be incomplete. A clone starts with fresh app data and generally requires a new sign-in.

The batch-copy picker compiles in the debug build. Device verification of multiple copies and the reported WhatsApp/Snapchat black screens is pending: ADB had no connected device during this follow-up. Older WhatsApp logs recorded the virtual package service as unhealthy before the Android 16 service-connection fix, so they do not establish the current behavior.

## Initial on-device failure (before the fixes)

On 2026-10-28, the installed Clony debug build was opened on the Pixel 8a (Android 16 / API 36). The home screen and app picker render, and the screen reports **VIRTUAL RUNTIME READY**. Tapping **Clone** for the built-in Calculator did not create a working clone.

- The `com.clony:black` helper process crashes with a `NullPointerException` in `HCallbackProxy.handleMessage()` while reading `ActivityThread$H.EXECUTE_TRANSACTION`. Logcat directly reports that Android denied this hidden field (`api=max-target-o`, host target SDK 28). The main process then receives `DeadObjectException` from `installPackageAsUser`. REA evidence: device `ev_3fc2c13251e8a86d91041fb593d216e1a8c994084209a341ffcd913be4436033`, crash buffer `ev_a15e090f987808266e51244f702786becdcca4e8c31d1694628b6150ae8f35eb`, main log `ev_674c8e47464800e854339450dbc7d9294fc2e3dbb3e1963ec1b814aee80d9bfc`.
- A read-only inspection of Clony's private runtime directory found no imported app under `blackbox/data/app`, confirming Calculator was not imported.
- After the helper died, Clony displayed **22 cloned apps**. This is a false positive: `BPackageManager.isInstalledFallback()` asks the host PackageManager whether each package is installed, then treats host-installed packages as virtual installs. The app therefore labels launchable apps as clones when its virtual package service is unhealthy.
- The readiness badge only confirms BlackBox service initialization; it does not confirm that Android's required activity hooks are accessible. The captured failure screen is [here](screens/clony-runtime-failure/screen.png), REA evidence `ev_cf88b751b4001e86b6e6138364e87bee5c17f12c2506bb481eb3b75e269d17ca`.

### TikTok retry before the runtime fixes

TikTok (`com.zhiliaoapp.musically`, version `47.4.3`) is installed from Google Play as a base APK plus many split APKs, including ABI, language, density, and feature splits. At the time of the first attempt, Clony imported only the base APK. The screen said TikTok was cloned even though the clone count was zero. Logcat showed `NoSuchFieldError` while `BPackageManagerService.installPackageAsUserLocked()` read `PackageParser.SigningDetails.pastSigningCertificates`; Android 16 classified that field as `max-target-o` and denied it for target SDK 28. The exception was swallowed while `InstallResult.success` retained its default `true` value.

The first retry already reported failures accurately, but still showed `pastSigningCertificates`; the clone count stayed at zero and `blackbox/data/app` remained empty. The helper process also crashed on the denied `ActivityThread.H.EXECUTE_TRANSACTION` field. REA evidence: final screen `ev_80690ae1a0757ad6035abd090b46dc07ad7ac18a6d72c90d65b0dfa88e6f6516`, main log `ev_652fa572d1ce41652c732be506a48ffa533d11525a6f4e4450be9f3401496bce`, crash log `ev_2586edb849f45f03d38701e5fe0a242ba40718b088fa4837a2def94b12211de5`, TikTok package `ev_4a7e7e00ca3abdce704f690eaf9094c09f1505a2a33cbceee4dc08ae2eaaaac8`.

The screenshot records that failed pre-fix attempt: [TikTok failure](screens/clony-tiktok-failure/screen.png).

## Fixes applied and Pixel verification

The fixes now in Clony:

1. `HCallbackProxy` uses Android's ActivityThread message IDs directly instead of reflectively reading blocked `ActivityThread.H` fields.
2. Cloning an app already installed on the phone uses public `PackageManager` metadata and signatures rather than routing the installed base APK through the incomplete Android 13+ archive parser. The model includes the installed activities, services, receivers, providers, permissions, version data, and launcher/info entry points.
3. The importer copies the base APK and every installed split into Clony's app directory, aggregates ABI checks across the APK set, persists split names and paths in a versioned parcel, exposes split paths in `ApplicationInfo`, and loads base plus splits in the virtual class loader.
4. The main process binds to the runtime service process so Android 16 does not freeze the provider-only process between binder calls. The package-manager health check also initializes its binder lazily before treating it as unhealthy.
5. Install exceptions and a dead package-service result remain visible as failures instead of being reported as successful clones.
6. Android 16/API 36 added `IBinderSession` to `IServiceConnection.connected()`. The virtual `ServiceConnectionDelegate` used a handler gated at API 37, so API 36 fell through to an older AIDL stub and aborted TikTok's binder thread with `AbstractMethodError`. The handler now intercepts the transaction starting at API 36, consumes the extra binder argument, and forwards the connection through the existing compatibility path.

On 2026-10-28, the updated `:app:assembleDebug` succeeded and was installed on the Pixel 8a (Android 16 / API 36), APK SHA-256 `b2486a7612467860167b8788d70af816d10a750e22d693b0bdcfc8e50aab8d3b`. Opening TikTok from Clony's saved clone card reached the TikTok feed in the virtual process (`com.clony:p0`). The crash buffer had no new fatal exception after this launch; the earlier API 36 `AbstractMethodError` was from before the updated build. This verifies the previously working split import and persisted clone still launch past the binder callback failure. Sign-in and every TikTok feature were not exercised.

- [Verified Clony screen with one saved TikTok clone](screens/clony-tiktok-success/screen.png) — REA evidence `ev_a6bf443e5e89681eaf49d3f2ec4f8fd55165ef63950470c6f047faabd8f00bb9`.
- [TikTok feed after the Android 16 callback fix](screens/clony-tiktok-android16-fixed/screen.png) — REA evidence `ev_8e6f2b0d4b6d75c4d6e7308a014880d4dbfbdf5f005bd1e44a31a65492b4f41a`.
- The installed debug build remains on the phone. No Android verifier rejection was bypassed, and no global security setting was changed.
