#!/bin/bash
set -e
P=$(cd "$(dirname "$0")" && pwd)
cd $P

# --lab：改成另一個套件名稱，可以和正式版「同時」安裝在同一支手機上。
# 測新功能而不動到已經在用的那一份，不必開另一個專案、也沒有合併成本。
LAB=0
OUT=photo-sort-box.apk
EXTRA=""
if [ "$1" = "--lab" ]; then
  LAB=1
  OUT=photo-sort-box-lab.apk
  EXTRA="--rename-manifest-package com.photobox.sorter.lab"
  echo "### 實驗版：套件名稱 com.photobox.sorter.lab"
fi

# ---- 工具鏈 ----
# 兩種來源擇一：
#   1. npm 湊出來的工具鏈（setup.sh 裝到 ../tools 或 PB_TOOLS；給連不到 Google 的雲端容器用）
#   2. Android SDK（ANDROID_HOME；GitHub Actions 與倬的電腦用）
#      PB_BUILD_TOOLS 指定 build-tools 版本（預設 36.0.0；34.0.0 的 d8 遇到 javac 編出的 class 會 NullPointerException），
#      PB_PLATFORM 指定 android.jar 的 API（預設 35）
T=${PB_TOOLS:-$P/../tools}
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
if [ -f "$T/aapt2" ] && [ -f "$T/android.jar" ]; then
  AAPT2=$T/aapt2
  ANDROID_JAR=$T/android.jar
  D8_JAR=$T/d8.jar
  APKSIGNER_JAR=$T/apksigner.jar
  ECJ=$T/ecj-3.45.0.jar
elif [ -n "$SDK" ]; then
  BT=$SDK/build-tools/${PB_BUILD_TOOLS:-36.0.0}
  AAPT2=$BT/aapt2
  [ -f "$AAPT2.exe" ] && AAPT2=$AAPT2.exe
  ANDROID_JAR=$SDK/platforms/android-${PB_PLATFORM:-35}/android.jar
  D8_JAR=$BT/lib/d8.jar
  APKSIGNER_JAR=$BT/lib/apksigner.jar
  ECJ=""
else
  echo "找不到工具鏈：請設定 ANDROID_HOME（Android SDK），或先執行 bash setup.sh"
  exit 1
fi
for f in "$AAPT2" "$ANDROID_JAR" "$D8_JAR" "$APKSIGNER_JAR"; do
  [ -f "$f" ] || { echo "缺少 $f"; exit 1; }
done
PY=${PYTHON:-python3}
$PY -c "" 2>/dev/null || PY=python

# ---- 簽章設定（先檢查，免得編譯完才發現不能簽） ----
# 金鑰與密碼都不放進 repo（repo 是公開的），一律從環境變數讀：
#   共用金鑰 hoonsor-release.jks（v1.2.0 起的正式簽章，GitHub Actions 從 Secrets 解出）：
#     SIGNING_KEYSTORE_PATH、SIGNING_STORE_PASSWORD、SIGNING_KEY_ALIAS、SIGNING_KEY_PASSWORD
#   舊金鑰 keystore.p12（v1.1.8 以前；只為了替還沒換裝的手機建置）：PB_KEYSTORE、PB_KS_PASS
# 找不到金鑰時「停下來」而不是自動產生新的——換金鑰會讓手機上的舊版無法覆蓋安裝，
# 卸載重裝會清掉相簿與整理進度（v1.0.6 → v1.1.0 時發生過）。
if [ -n "$SIGNING_KEYSTORE_PATH" ]; then
  [ -f "$SIGNING_KEYSTORE_PATH" ] || { echo "找不到簽章金鑰 $SIGNING_KEYSTORE_PATH"; exit 1; }
  for v in SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD; do
    [ -n "${!v}" ] || { echo "請設定環境變數 $v"; exit 1; }
  done
  SIGN_ARGS=(--ks "$SIGNING_KEYSTORE_PATH" --ks-key-alias "$SIGNING_KEY_ALIAS"
             --ks-pass env:SIGNING_STORE_PASSWORD --key-pass env:SIGNING_KEY_PASSWORD)
  echo "簽章：共用金鑰 $SIGNING_KEYSTORE_PATH"
elif [ -n "$PB_KS_PASS" ]; then
  KS=${PB_KEYSTORE:-$P/keystore.p12}
  [ -f "$KS" ] || { echo "找不到舊簽章金鑰 $KS（或改用共用金鑰 SIGNING_KEYSTORE_PATH）"; exit 1; }
  SIGN_ARGS=(--ks "$KS" --ks-type PKCS12 --ks-key-alias photobox
             --ks-pass env:PB_KS_PASS --key-pass env:PB_KS_PASS)
  echo "!!! 簽章：舊金鑰 $KS（v1.2.0 起正式版改用共用金鑰，兩者不能互相覆蓋安裝）"
else
  echo "請設定簽章環境變數：共用金鑰 SIGNING_KEYSTORE_PATH／SIGNING_STORE_PASSWORD／SIGNING_KEY_ALIAS／SIGNING_KEY_PASSWORD"
  echo "（詳見 AGENTS.md 第 3 節）"
  exit 1
fi

rm -rf build && mkdir -p build/gen build/classes build/dex

if [ "$LAB" = "1" ]; then
  cp res/values/strings.xml build/strings.bak
  sed -i 's#<string name="app_name">照片整理箱</string>#<string name="app_name">照片整理箱 LAB</string>#' res/values/strings.xml
  trap 'cp build/strings.bak res/values/strings.xml 2>/dev/null' EXIT
fi
echo "[1/6] aapt2 compile"
"$AAPT2" compile --dir res -o build/res.zip
echo "[2/6] aapt2 link"
# --custom-package 讓 R.java 留在原本的 Java 套件，Java 原始碼一行都不用改
"$AAPT2" link -o build/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  build/res.zip --java build/gen --auto-add-overlay \
  --custom-package com.photobox.sorter $EXTRA
echo "[3/6] compile java"
find java build/gen -name '*.java' > build/sources.txt
if [ -n "$ECJ" ] && [ -f "$ECJ" ]; then
  java -jar "$ECJ" -nowarn -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
else
  # Android SDK 沒有 ecj，改用 JDK 的 javac（17 或 21 都可以）
  javac -nowarn -proc:none -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
    -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
fi
echo "[4/6] d8"
find build/classes -name '*.class' > build/classes.txt
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 30 \
  --lib "$ANDROID_JAR" --output build/dex @build/classes.txt
echo "[5/6] package"
$PY "$P/pack.py" build/base.apk build/dex build/unsigned.apk
echo "[6/6] sign"
java -jar "$APKSIGNER_JAR" sign "${SIGN_ARGS[@]}" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out build/$OUT build/unsigned.apk
java -jar "$APKSIGNER_JAR" verify build/$OUT
ls -la build/$OUT
