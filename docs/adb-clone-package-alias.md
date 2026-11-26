# Launch a virtual clone by an ADB package alias

Clony keeps the guest app inside its virtual runtime under the guest's original
package name and a virtual user ID. The clone package name shown in Clone
Settings (for example, `com.zhiliaoapp.musically.clone1`) is a persisted alias;
Android's package manager cannot see that private mapping on its own.

To make `pm path` and `monkey -p` work with that alias, build and install a small
launcher APK whose Android package name is the alias. The launcher forwards to
Clony, which resolves the alias to the saved guest package and virtual user.
The guest APK remains virtual and keeps its original package identity.

After creating both clones and saving the matching aliases in Clony, build one
launcher APK per alias from the project root:

```sh
./gradlew :app:buildCloneAliasLauncher \
  -PcloneAliasPackage=com.zhiliaoapp.musically.clone1 \
  -PcloneAliasLabel="TikTok clone 1"

./gradlew :app:buildCloneAliasLauncher \
  -PcloneAliasPackage=com.zhiliaoapp.musically.clone2 \
  -PcloneAliasLabel="TikTok clone 2"
```

Install both generated alias packages on each phone. Clony resolves each alias
against that phone's saved clone-to-user mapping, so the APKs do not hardcode
virtual user IDs:

```sh
adb install -r app/build/generated/clone-package-alias/clone1.apk
adb install -r app/build/generated/clone-package-alias/clone2.apk
adb shell pm path com.zhiliaoapp.musically.clone1
adb shell monkey -p com.zhiliaoapp.musically.clone1 1
adb shell pm path com.zhiliaoapp.musically.clone2
adb shell monkey -p com.zhiliaoapp.musically.clone2 1
```

The alias must exactly match the package name saved for that clone in Clony.
If the alias changes, rebuild and install the launcher for the new name. Remove
an obsolete launcher alias with `adb uninstall <old-alias>`.

## GenFarm clone IDs

Automation can also launch Clony's exported bridge activity directly. Pass
`package_name` as the guest app's original package and `clone_user_id` as either
the legacy numeric virtual user ID or a readable copy label such as `TikTok 1`:

```sh
adb shell am start -n com.clony/.AutomationLaunchActivity \
  --es package_name com.zhiliaoapp.musically \
  --es clone_user_id "TikTok 1"
```

For compatibility, GenFarm configurations that still send an integer continue
to select that exact virtual user:

```sh
adb shell am start -n com.clony/.AutomationLaunchActivity \
  --es package_name com.zhiliaoapp.musically \
  --ei clone_user_id 1
```

The readable form must match the app's displayed label and copy number. Clony
resolves it to the saved per-app copy assignment, then passes the numeric ID to
BlackBox. GenFarm must send `clone_user_id` as a string extra for the readable
form; Clony does not parse the whole JSON document or use `host_package` and
`wait_seconds` itself.
