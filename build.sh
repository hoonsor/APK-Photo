#!/bin/bash
set -e
P=$(cd "$(dirname "$0")" && pwd)
T=${PB_TOOLS:-$P/../tools}
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

rm -rf build && mkdir -p build/gen build/classes build/dex

if [ "$LAB" = "1" ]; then
  cp res/values/strings.xml build/strings.bak
  sed -i 's#<string name="app_name">照片整理箱</string>#<string name="app_name">照片整理箱 LAB</string>#' res/values/strings.xml
  trap 'cp build/strings.bak res/values/strings.xml 2>/dev/null' EXIT
fi
echo "[1/6] aapt2 compile"
$T/aapt2 compile --dir res -o build/res.zip
echo "[2/6] aapt2 link"
# --custom-package 讓 R.java 留在原本的 Java 套件，Java 原始碼一行都不用改
$T/aapt2 link -o build/base.apk -I $T/android.jar --manifest AndroidManifest.xml \
  build/res.zip --java build/gen --auto-add-overlay \
  --custom-package com.photobox.sorter $EXTRA
echo "[3/6] compile java"
find java build/gen -name '*.java' > build/sources.txt
java -jar $T/ecj-3.45.0.jar -nowarn -proc:none -source 1.8 -target 1.8 \
  -bootclasspath $T/android.jar -d build/classes @build/sources.txt
echo "[4/6] d8"
find build/classes -name '*.class' > build/classes.txt
java -cp $T/d8.jar com.android.tools.r8.D8 --release --min-api 30 \
  --lib $T/android.jar --output build/dex @build/classes.txt
echo "[5/6] package"
python3 "$P/pack.py" build/base.apk build/dex build/unsigned.apk
echo "[6/6] sign"
# 簽章金鑰與密碼都不放進 repo（repo 是公開的）：
#   PB_KEYSTORE  金鑰路徑，預設為專案根目錄的 keystore.p12（已被 .gitignore 排除）
#   PB_KS_PASS   金鑰密碼，由倬自己保管
# 找不到金鑰時「停下來」而不是自動產生新的——換金鑰會讓手機上的舊版無法覆蓋安裝，
# 卸載重裝會清掉相簿與整理進度（v1.0.6 → v1.1.0 時發生過）。
KS=${PB_KEYSTORE:-$P/keystore.p12}
if [ -z "$PB_KS_PASS" ]; then
  echo "請先設定環境變數 PB_KS_PASS（簽章金鑰密碼），例如：PB_KS_PASS=xxxx bash build.sh"
  exit 1
fi
if [ ! -f "$KS" ]; then
  if [ "$PB_NEW_KEYSTORE" = "1" ]; then
    echo "!!! 產生全新的簽章金鑰：手機上用舊金鑰簽的版本將無法直接覆蓋安裝"
    keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 \
      -alias photobox -keyalg RSA -keysize 2048 -validity 10950 \
      -storepass "$PB_KS_PASS" -keypass "$PB_KS_PASS" \
      -dname "CN=Photo Sort Box, OU=Personal, O=Photo Sort Box, C=TW" 2>/dev/null
  else
    echo "找不到簽章金鑰 $KS"
    echo "請把倬電腦上的 keystore.p12 放到專案根目錄，或用 PB_KEYSTORE 指定路徑。"
    echo "（確定要換一把新金鑰才加 PB_NEW_KEYSTORE=1，換了之後手機要先卸載舊版）"
    exit 1
  fi
fi
java -jar $T/apksigner.jar sign --ks "$KS" --ks-type PKCS12 \
  --ks-key-alias photobox --ks-pass env:PB_KS_PASS --key-pass env:PB_KS_PASS \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out build/$OUT build/unsigned.apk
java -jar $T/apksigner.jar verify build/$OUT
ls -la build/$OUT
