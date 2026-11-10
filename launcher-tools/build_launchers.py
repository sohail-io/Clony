#!/usr/bin/env python3
"""Build signed per-copy launcher APKs for the host app's debug signing key."""

import argparse
import concurrent.futures
import os
import pathlib
import re
import subprocess
import tempfile
import zipfile


parser = argparse.ArgumentParser()
parser.add_argument("--sdk", type=pathlib.Path, required=True)
parser.add_argument("--jdk", type=pathlib.Path, required=True)
parser.add_argument("--output", type=pathlib.Path, required=True)
parser.add_argument("--host-package", required=True)
parser.add_argument("--keystore", type=pathlib.Path, required=True)
parser.add_argument("--build-tools", default="36.1.0")
args = parser.parse_args()

if not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", args.host_package):
    raise SystemExit("Invalid Android host package name")

root = pathlib.Path(__file__).resolve().parent.parent
sdk, jdk = args.sdk.resolve(), args.jdk.resolve()
keystore = args.keystore.resolve()
build_tools = sdk / "build-tools" / args.build_tools
android_jar = sdk / "platforms" / "android-37.0" / "android.jar"
if not keystore.is_file():
    raise SystemExit(f"Debug signing key not found: {keystore}")
if not android_jar.is_file():
    raise SystemExit(f"Android platform jar not found: {android_jar}")

env = dict(os.environ, JAVA_HOME=str(jdk))
env["PATH"] = str(jdk / "bin") + os.pathsep + env.get("PATH", "")


def run(*command):
    subprocess.run(
        [str(part) for part in command],
        env=env,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


output = args.output.resolve() / "launchers"
output.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix="clony-launchers-") as temporary:
    temp = pathlib.Path(temporary)
    classes = temp / "classes"
    classes.mkdir()
    dex = temp / "dex"
    dex.mkdir()
    source = temp / "LauncherActivity.java"
    source_text = (root / "launcher-tools" / "LauncherActivity.java").read_text()
    source.write_text(source_text.replace("@@HOST_PACKAGE@@", args.host_package))
    run(
        jdk / "bin" / "javac",
        "-source", "8", "-target", "8",
        "-classpath", android_jar,
        "-d", classes,
        source,
    )
    run(
        build_tools / "d8",
        "--min-api", "24",
        "--lib", android_jar,
        "--output", dex,
        classes / "com/clony/launcher/LauncherActivity.class",
    )

    def build(slot):
        work = temp / str(slot)
        work.mkdir()
        values = work / "res" / "values"
        values.mkdir(parents=True)
        (values / "strings.xml").write_text(
            f'<resources><string name="app_name">Clony Copy {slot}</string></resources>'
        )
        manifest = work / "AndroidManifest.xml"
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.clony.launcher.copy{slot}" android:versionCode="1" android:versionName="1.0">
<uses-sdk android:minSdkVersion="24" android:targetSdkVersion="28" />
<application android:label="@string/app_name" android:allowBackup="false" android:theme="@android:style/Theme.Translucent.NoTitleBar">
<activity android:name="com.clony.launcher.LauncherActivity" android:exported="true" android:excludeFromRecents="true">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
</activity></application></manifest>''')
        raw = work / "raw.apk"
        aligned = work / "aligned.apk"
        run(build_tools / "aapt", "package", "-f", "-M", manifest, "-S", work / "res",
            "-I", android_jar, "-F", raw)
        with zipfile.ZipFile(raw, "a", zipfile.ZIP_DEFLATED) as archive:
            archive.write(dex / "classes.dex", "classes.dex")
        run(build_tools / "zipalign", "-f", "4", raw, aligned)
        apk = output / f"copy{slot}.apk"
        run(
            build_tools / "apksigner", "sign",
            "--ks", keystore,
            "--ks-pass", "pass:android",
            "--key-pass", "pass:android",
            "--ks-key-alias", "androiddebugkey",
            "--v4-signing-enabled", "false",
            "--out", apk, aligned,
        )
        run(build_tools / "apksigner", "verify", apk)
        run(build_tools / "zipalign", "-c", "4", apk)
        result = subprocess.check_output(
            [str(build_tools / "aapt"), "dump", "badging", str(apk)],
            env=env,
            text=True,
        )
        assert f"package: name='com.clony.launcher.copy{slot}'" in result
        assert f"application: label='Clony Copy {slot}'" in result
        assert "launchable-activity: name='com.clony.launcher.LauncherActivity'" in result
        return slot

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        completed = list(pool.map(build, range(1, 101)))
    print(f"Built {len(completed)} signed launcher APKs in {output}")
