#!/bin/bash
# 純邏輯測試（不需要 Android）：照片牆排版 Flow、備份比對 BackupMatch、位置解析 Geo、更新版本比較 UpdateVersion
set -e
P=$(cd "$(dirname "$0")/.." && pwd)
S=$P/java/com/photobox/sorter
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -nowarn -d "$OUT" "$S/Flow.java" "$S/BackupMatch.java" "$S/Geo.java" "$S/UpdateVersion.java" "$P/tests/LogicTest.java"
java -cp "$OUT" LogicTest
