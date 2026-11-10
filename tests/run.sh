#!/bin/bash
# 純邏輯測試（不需要 Android）：照片牆排版 Flow、備份比對 BackupMatch、位置解析 Geo、更新版本比較 UpdateVersion、檢視器手勢 Swipe
# ＋每日自動備份 AutoBackup 的檔名／保留規則（要 android.jar 才能編譯，找不到就跳過並提示）
set -e
P=$(cd "$(dirname "$0")/.." && pwd)
S=$P/java/com/photobox/sorter
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -nowarn -d "$OUT" "$S/Flow.java" "$S/BackupMatch.java" "$S/Geo.java" "$S/UpdateVersion.java" "$S/Swipe.java" "$P/tests/LogicTest.java"
java -cp "$OUT" LogicTest

# AutoBackup 引用 Android 類別：用和 build.sh 相同的 android.jar 當 classpath（只呼叫純 Java 的靜態方法）
T=${PB_TOOLS:-$P/../tools}
SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
AJ=""
if [ -f "$T/android.jar" ]; then AJ=$T/android.jar
elif [ -n "$SDK" ] && [ -f "$SDK/platforms/android-${PB_PLATFORM:-35}/android.jar" ]; then AJ=$SDK/platforms/android-${PB_PLATFORM:-35}/android.jar
fi
if [ -n "$AJ" ]; then
  CP="$OUT:$AJ"
  # Windows 的 Git Bash：java 要 Windows 格式、分號分隔的 classpath
  case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) CP=$(cygpath -wp "$CP");; esac
  javac -nowarn -encoding UTF-8 -cp "$AJ" -d "$OUT" "$P/java/tw/hoonsor/backup/AutoBackup.java" \
    "$P/java/tw/hoonsor/backup/BackupJobService.java" "$P/tests/AutoBackupTest.java"
  java -cp "$CP" AutoBackupTest
else
  echo "（跳過 AutoBackupTest：找不到 android.jar，請設定 ANDROID_HOME）"
fi
