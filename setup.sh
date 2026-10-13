#!/bin/bash
# 在一個全新的容器裡把 Android 建置工具鏈裝起來。
#
# 為什麼不用 Android SDK：這個環境連不到 dl.google.com 也連不到 Maven Central，
# 但 npm registry 通。所以工具鏈是從兩個 npm 套件湊出來的。
#
# 用法：bash setup.sh          → 裝到 ../tools
#       PB_TOOLS=/x bash setup.sh → 裝到 /x
set -e
P=$(cd "$(dirname "$0")" && pwd)
T=${PB_TOOLS:-$P/../tools}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "工具鏈安裝目錄：$T"
mkdir -p "$T"

need=0
for f in aapt2 android.jar apksigner.jar d8.jar ecj-3.45.0.jar; do
  [ -f "$T/$f" ] || need=1
done
if [ "$need" = "0" ]; then
  echo "工具鏈已存在，略過下載。"
else
  cd "$TMP"
  npm init -y >/dev/null 2>&1
  echo "[1/2] 下載 @drxiaozhi/minapk（android.jar / apksigner / d8 / ecj）"
  npm i --no-audit --no-fund @drxiaozhi/minapk >/dev/null 2>&1
  echo "[2/2] 下載 aaptjs3（linux x64 的 aapt2 二進位）"
  npm i --no-audit --no-fund aaptjs3 >/dev/null 2>&1

  # 這兩個套件的內部結構不保證穩定，所以用 find 撈而不是寫死路徑
  for f in android.jar apksigner.jar d8.jar; do
    src=$(find node_modules -name "$f" -type f 2>/dev/null | head -1)
    [ -n "$src" ] && cp "$src" "$T/$f" && echo "  取得 $f"
  done
  src=$(find node_modules -name "ecj*.jar" -type f 2>/dev/null | head -1)
  [ -n "$src" ] && cp "$src" "$T/ecj-3.45.0.jar" && echo "  取得 ecj"
  # aaptjs3 同時帶 darwin / linux / win32 三份二進位。
  # 一定要挑對平台——字母序會先撞到 darwin，直接 head -1 會拿到不能執行的那個。
  arch=$(uname -m)
  case "$arch" in
    x86_64|amd64) sub="x64" ;;
    aarch64|arm64) sub="arm64" ;;
    *) sub="x64" ;;
  esac
  src=$(find node_modules -path "*/$sub/linux/aapt2" -type f 2>/dev/null | head -1)
  [ -z "$src" ] && src=$(find node_modules -path "*/linux/aapt2" -type f 2>/dev/null | head -1)
  if [ -n "$src" ]; then
    cp "$src" "$T/aapt2"; chmod +x "$T/aapt2"; echo "  取得 aapt2（$sub/linux）"
  else
    echo "  找不到 linux 版 aapt2，請手動在 node_modules 裡找 */linux/aapt2"
  fi
fi

echo
echo "=== 檢查 ==="
ok=1
for f in aapt2 android.jar apksigner.jar d8.jar ecj-3.45.0.jar; do
  if [ -f "$T/$f" ]; then
    printf "  有 %-18s %s\n" "$f" "$(du -h "$T/$f" | cut -f1)"
  else
    printf "  缺 %-18s ← 這個套件的目錄結構可能變了，需要手動找\n" "$f"
    ok=0
  fi
done
"$T/aapt2" version 2>/dev/null | head -1 | sed 's/^/  /' || echo "  aapt2 無法執行"
echo
[ "$ok" = "1" ] && echo "可以執行 bash build.sh 了。" || { echo "工具鏈不完整。"; exit 1; }
