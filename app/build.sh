set -e
VC=${VC:-1}; VN=${VN:-1.0}
# Toolchain paths (override with env vars on your own machine)
TC=${TC:-/workspace/work/android}
R=${R:-$TC/root}
[ -d "$R" ] && export LD_LIBRARY_PATH=$R/usr/lib/aarch64-linux-gnu/android:$R/usr/lib/aarch64-linux-gnu:$R/usr/lib/7zip:$TC/croot/usr/lib/aarch64-linux-gnu:$R/usr/lib
AAPT2=${AAPT2:-$R/usr/lib/android-sdk/build-tools/debian/aapt2}; ZIPALIGN=${ZIPALIGN:-$R/usr/lib/android-sdk/build-tools/debian/zipalign}
JDK=${JDK:-$TC/jdk/bin}
[ -x "$AAPT2" ] || AAPT2=aapt2; [ -x "$ZIPALIGN" ] || ZIPALIGN=zipalign; [ -d "$JDK" ] || JDK=$(dirname "$(command -v java)")
BT=${BT:-$TC/bt/android-14}   # needs lib/d8.jar and lib/apksigner.jar
AJ=${AJ:-$TC/plat/android-34/android.jar}
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/gen build/classes build/dex build/stage/lib
$AAPT2 compile --dir res -o build/res.zip
$AAPT2 link -I $AJ --manifest AndroidManifest.xml -A assets --java build/gen -o build/base.apk build/res.zip --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name $VN --auto-add-overlay -0 xnet -0 param -0 bin
$JDK/javac -nowarn -source 11 -target 11 -encoding UTF-8 -classpath $AJ -d build/classes $(find src build/gen -name "*.java") 2>&1 | grep -v "bootstrap\|^warning\|^1 warning\|^Note:" || true
ls build/classes/com/wxcode/studio/MainActivity.class >/dev/null
$JDK/java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --min-api 26 --lib $AJ --output build/dex $(find build/classes -name "*.class")
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -X ../unsigned.apk classes.dex)
cp -r jni/arm64-v8a build/stage/lib/
(cd build/stage && zip -q -X -r ../unsigned.apk lib)
$ZIPALIGN -f -p 4 build/unsigned.apk build/aligned.apk
# Signing: KS (keystore path), KS_PASS, KS_ALIAS. Without them a throwaway debug key is generated.
[ -f "$TC/wxcode-signing.env" ] && . "$TC/wxcode-signing.env"
if [ -z "$KS" ]; then
  KS=debug.jks; KS_PASS=android; KS_ALIAS=debug
  [ -f $KS ] || $JDK/keytool -genkeypair -keystore $KS -storepass $KS_PASS -keypass $KS_PASS -alias $KS_ALIAS -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Debug" >/dev/null 2>&1
fi
$JDK/java -jar $BT/lib/apksigner.jar sign --ks "$KS" --ks-pass pass:$KS_PASS --ks-key-alias $KS_ALIAS --out build/app.apk build/aligned.apk
echo BUILT build/app.apk
