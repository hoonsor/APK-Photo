# 照片整理箱（Photo Sort Box）— 給接手 AI 的說明

讀者：接手開發的 AI（Antigravity、Codex、DeepSeek Harness、另一台電腦上的 Claude）。
目標：**不看任何對話紀錄**就能繼續開發。使用者說明在 `README.md`，版本與進度在 `PROJECT_STATUS.md`，
語音動作在 `docs/AGENT_API.md` 與 `docs/agent-manifest.json`。

> 本檔整合了原本的 `HANDOFF.md`（2026-10-13 併入，原檔保留一行指向本檔）。

---

## 1. 專案目的

倬（使用者）自用的 Android 相片整理 App：

- **滑動整理**：一張一張左右上下滑，標記保留／待刪，確認後才交給系統刪除。
- **找回舊照片**：日期格照片牆、年月跳轉、日期與檔名搜尋。
- **相簿**：照片與影片可放在同一本「虛擬相簿」，一張可在多本相簿；多選分享到 LINE、Dropbox、雲端硬碟。
- **相似照片**：連拍、重複存檔的照片成組找出。

操作方式參考倬每天在用的相簿 App（截圖在對話中提供過，未存檔），但**外觀維持本 App 的深色風格**。
靈感來自 Slidebox，但**程式與素材全部從零撰寫**：當初倬提供過 Slidebox 的 APK，已明確拒絕反編譯或複製。
接手時請維持這一點，不要逆向任何商業 App。

## 2. 現狀速覽

| 項目 | 內容 |
|---|---|
| 版本 | 見 `PROJECT_STATUS.md` 最上方（撰寫本檔時為 v1.1.8，versionCode 10108） |
| 套件 | `com.photobox.sorter`（LAB 版 `com.photobox.sorter.lab`）。舊 APP，**不改套件名** |
| 平台 | minSdk 30（Android 11）／targetSdk 34 |
| 技術棧 | **純 Android Framework（Java 8 語法）。無 AndroidX、無 Gradle、無 Kotlin、無任何第三方函式庫** |
| 權限 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_VISUAL_USER_SELECTED`、`ACCESS_MEDIA_LOCATION`、`VIBRATE`、`READ_EXTERNAL_STORAGE`（maxSdk 32）、`INTERNET`＋`REQUEST_INSTALL_PACKAGES`（v1.2.0 起，**只用於 App 內更新**） |
| 規模 | 35 個 Java 類別、約 9,400 行 |
| 測試裝置 | OPPO Reno14F／Android 15／ColorOS（開發端無法連接實機，全靠倬回報） |
| repo | `hoonsor/APK-Photo`（public）。程式碼工作資料夾為 **`D:\dev\APK-Photo`**（全英文路徑，方便建置與安裝手機測試）。`D:\01-Project\30-手機照片APP` 只放筆記、舊快照（`photobox/`）、舊簽章與交付的 APK，不在那裡建置 |

## 3. 建置與安裝

```bash
export ANDROID_HOME=<Android SDK>   # 或先 bash setup.sh 從 npm 取得工具鏈到 ../tools（雲端容器用）
export SIGNING_KEYSTORE_PATH=... SIGNING_STORE_PASSWORD=... SIGNING_KEY_ALIAS=... SIGNING_KEY_PASSWORD=...
bash build.sh                       # 正式版 → build/photo-sort-box.apk
bash build.sh --lab                 # LAB 版 → build/photo-sort-box-lab.apk
bash tests/run.sh                   # 純邏輯測試（Flow／BackupMatch／Geo／UpdateVersion），不需要 Android
```

- **工具鏈**：`build.sh` 先找 `../tools`（或 `PB_TOOLS`）的 npm 工具鏈，沒有就用 `ANDROID_HOME` 的 Android SDK
  （`PB_BUILD_TOOLS` 預設 36.0.0、`PB_PLATFORM` 預設 35；SDK 沒有 ecj，改用 JDK 的 `javac -source 8 -target 8`）。
  **build-tools 34.0.0 的 d8 遇到 javac 編出的 class 會 NullPointerException**，不要降版。
- **簽章（v1.2.0 起）**：共用金鑰 `hoonsor-release.jks`，從環境變數 `SIGNING_KEYSTORE_PATH`、`SIGNING_STORE_PASSWORD`、
  `SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD` 讀取；GitHub Actions 從 repo Secrets 解出。金鑰與密碼**不要寫進任何檔案**。
  憑證 SHA-256 `514ec00eea070dd3e67b4f6a0c722c9c779497bb0797e7a3446fcca4e4548825`。
- 舊金鑰 `keystore.p12`（v1.1.8 以前）只存在倬的電腦（`D:/01-Project/30-手機照片APP/photobox/keystore.p12`，勿刪），
  設定 `PB_KS_PASS`（＋`PB_KEYSTORE`）時 `build.sh` 仍可用它簽，只為了替還沒換裝的手機建置。
- 找不到金鑰時 `build.sh` 會**停下來**，不會自動產生新的（見第 7 節坑 14）。
- `--lab`：應用 ID 加 `.lab`、名稱加「LAB」，可和正式版同時安裝、資料完全隔離。
  實作是 `aapt2 link --rename-manifest-package` 搭配 `--custom-package com.photobox.sorter`
  （讓 `R.java` 留在原套件），所以兩版永遠是同一份程式碼。**測新功能用 LAB 版，不要另開專案。**
- **發布**：改 `AndroidManifest.xml` 版號 → `PROJECT_STATUS.md` 新增 `### vX.Y.Z — 日期` → 推送 main 與同名 tag。
  `.github/workflows/build.yml` 在 ubuntu 上用 sdkmanager 下載 build-tools 與 android.jar、跑測試、`build.sh` 打包，
  發布 `PhotoSortBox-vX.Y.Z.apk` 到 Releases（main 另發 `latest` 預先發行版）。細節見 README「如何發新版」。
- 安裝與更新：手機用 App 內更新（`Updater`／`UpdateDialog`）或 Obtainium。同一把金鑰簽的新版可直接覆蓋安裝，資料保留。

### 為什麼建置長這樣

當初的開發環境**連不到 dl.google.com 與 Maven Central**，只有 npm registry 能用，所以工具鏈從兩個 npm 套件湊出來：

| 來源 | 取得 |
|---|---|
| `@drxiaozhi/minapk` | `android.jar`（API 34）、`apksigner.jar`、`d8.jar`、`ecj-*.jar` |
| `aaptjs3` | `aapt2`（**必須挑 `bin/x64/linux/`**，字母序會先撞到 darwin 版，會 `Exec format error`） |

管線：`aapt2 compile → aapt2 link → ecj（-source/-target 1.8, -bootclasspath android.jar）→ d8（--min-api 30）→ pack.py → apksigner`

- **`pack.py` 不是裝飾品**：`resources.arsc` 必須 STORED（不壓縮）且在檔案中 4 位元組對齊，否則 Android 8 以上拒絕安裝。
- minSdk 30 → apksigner 實際只用 v3 簽章，`verify` 顯示 v2=false 是正常的。
- 已實測可重現：2026-10-13 用 repo 內容重建，`classes.dex` 與交付的 v1.1.8 完全相同。

### 怎麼驗證（沒有實機）

1. 編譯：`ecj` 零錯誤
2. dex 字串檢查：用 `zipfile` 讀 `classes.dex`，搜尋新功能的 UTF-8 字串，確認真的編進去
3. `aapt2 dump badging` / `dump xmltree --file AndroidManifest.xml`：驗權限、元件、版號
4. 純邏輯 JVM 測試：`tests/run.sh`（目前 29,307 項檢查全過）。**新增演算法請照這個模式補測試**——
   本專案的 bug 幾乎都出在 MediaStore 用法與索引運算，不在畫面
5. 倬實機回報：每個回報對應一個版本與 `PROJECT_STATUS.md` 一條紀錄

## 4. 目錄與架構

```
AndroidManifest.xml      版號在這裡（versionCode 由版號推導：1.1.8 → 10108）
build.sh / setup.sh / pack.py
res/                     圖示（adaptive＋monochrome＋各密度點陣圖）、styles、strings
icons/*.py               產生圖示的腳本（要改圖示從這裡改）
tests/                   LogicTest.java＋run.sh
docs/                    AGENT_API.md、agent-manifest.json
java/com/photobox/sorter/
```

沒有 Fragment（不用 AndroidX），用自己的 `Page` 抽象：

```
MainActivity              單一 Activity，底部四分頁（照片／相簿／待刪／更多，順序＝TAB_* 常數）＋左側 ☰ 選單（內容＝MorePage）
├ BrowsePage              照片（預設）：日期格照片牆、搜尋、篩選、年月跳轉；長按多選＋左側相簿欄＋拖放；往右滑開相簿欄
├ AlbumsPage              相簿卡片牆、＋、建立／重新命名／複製／刪除
├ TrashPage               待刪清單與系統垃圾桶
└ MorePage                更多：資料夾、相似照片、備份與還原、設定（內嵌 SettingsPage）、說明

ViewerActivity          ★ 單張檢視＝整理（v1.3.0 起）：沒放大時左右換張、上滑待刪（10 秒復原列）、下滑編輯；
                          放大時滑動是拖曳。下方 資訊／喜好／加入相簿／編輯／分享／刪除；⋮ 保留、待刪、清除標記、編輯
EditorActivity            照片編輯：畫筆、螢光筆、方框、圓框、箭頭、文字、馬賽克框／筆、粗細滑桿、復原／重做；另存新照片（不覆蓋原檔）
InfoActivity              資訊（EXIF／影片 metadata）、位置、相簿、裝置 四分頁
AlbumActivity             單一相簿：封面＋數量、照片牆、新增照片／分享相簿、多選動作
PickerActivity            挑選要加入相簿的項目（pick mode 的 MediaGrid）
DupActivity               重複／相似照片掃描與分組

MediaGrid        ★ 共用照片牆（ListView）：日期格、月份標題、多選、範圍選取、雙指改欄數、拖放起點、往右滑
Flow             ★ 照片牆排版運算（純 Java，JVM 測試）
Albums           ★ 虛擬相簿：記憶體快取＋albums/album_items 表的唯一讀寫入口
AlbumDialogs       命名、加入相簿、改名／複製／刪除對話框
DragStack          多選照片疊成一疊拖曳（framework drag and drop＋收攏動畫）
AnnotateView       編輯器畫布：圖形以原圖像素座標儲存、清單快照式復原／重做、render() 輸出
Swipe              檢視器手勢判斷：鎖定方向、門檻、快速甩動（純 Java，JVM 測試）
Share              ACTION_SEND(_MULTIPLE)＋ClipData＋讀取授權
Backup             JSON 匯出／匯入（SAF），還原為合併
BackupMatch        備份項目 ↔ 目前項目的比對（純 Java，JVM 測試）
Geo                影片 ISO 6709 位置字串解析（純 Java）
Store            ★ 全 App 共用的照片清單與整理狀態（單例＋Listener），載入時一併載入相簿
Db                 SQLite（版本 3）
Media            ★ 所有 MediaStore 查詢與刪除／移動請求
Item / Thumbs / Prefs / ZoomImageView / Ui / Icons / T（色票）/ Page
（v1.3.0 移除的 SortPage／Card／SortStartDialog／StartPickActivity 在 git 歷史，tag v1.2.0 可找回）
```

### 名詞：「相簿」與「資料夾」刻意分開

- **資料夾**＝MediaStore 的 BUCKET（實體位置）。程式沿用舊名：`Item.bucket`、`Store.albums()`、
  `Store.Filter.album`、`MainActivity.moveToAlbum()` **全都是資料夾**。
- **相簿**＝`Albums` 管理的虛擬清單，不搬動檔案。
- 介面文字一律用「資料夾」「相簿」區分；改程式別混用。

### `Page` 生命週期

```java
public abstract View build();        // 第一次顯示時才建（view() 會快取）
public void onShow() / onHide()      // 分頁切換
public void onStoreChanged()         // Store 變動時刷新
public boolean onBack()              // 回傳 true 表示自己吃掉返回鍵
```

### `Store` 是唯一的真相來源

```java
Store.get()                      // 單例
.all / .byId / .state            // 全部項目（新到舊）、id 索引、整理狀態
.load(ctx, done)                 // 背景載入（MediaStore 查詢＋狀態＋相簿）
.setState / .setStates / .forget // 改狀態（寫 Db 並 notifyChanged）
.undo() / .canUndo()             // 復原堆疊（最多 80 步；v1.3.0 起檢視器的上滑待刪自己記前一個狀態，不用這個）
.filter(Filter)                  // 照片頁篩選與搜尋（含日期解析）
```

「從某張開始整理」在 v1.3.0 改成直接 `ViewerActivity.open(ctx, start, items)`：左右滑的範圍就是 items
（照片牆目前的清單、某個資料夾），`MainActivity.reviewFrom()` 是共用入口。

`Store.Filter`：`text`、`stateWanted`（-1 全部／0 未整理／1 已保留／2 待刪）、`onlyScreenshot`、`onlyVideo`、
`onlyLarge`（>8MB）、`onlyLine`、`album`（＝資料夾）、`sort`（0 最新／1 最舊／2 最大）。

### `Db` 五張表（版本 3）

```sql
status(media_id PRIMARY KEY, state, ts)   -- 0 未整理 1 已保留 2 待刪
trashed(media_id PRIMARY KEY, name, ...)  -- 本 App 送進系統垃圾桶的紀錄
ahash(media_id PRIMARY KEY, hash, sig)    -- 感知雜湊快取（hash=aHash, sig=dHash）
albums(id AUTOINCREMENT, name, kind, cover, created, updated)   -- kind 1 = 喜好項目（唯一、不能改名刪除）
album_items(album_id, media_id, ts, PRIMARY KEY(album_id, media_id))
```

相簿成員在照片被刪除時**不清掉**（丟進系統垃圾桶再還原時 id 不變，保留比較安全）；
顯示、計數、匯出都只看 `Store.byId` 裡還存在的項目。

### 檢視器手勢（v1.3.0 起固定，不可自訂）

`ZoomImageView` 只在 `atFit()`（縮放 ≤ 原始的 1.02 倍）時把單指滑動交給 `GestureListener`：
先用 `Swipe.lockAxis()` 鎖定橫向或直向，拖曳時照片跟著手指走，放開時 `Swipe.decide()` 決定
`NEXT／PREV／UP／DOWN／NONE`。放大時單指滑動走 `GestureDetector.onScroll` 拖曳。
第二根手指放下（縮放）會取消滑動；飛出動畫進行中（`flying`）不接受新滑動。

## 5. 關鍵設計決策（含原因與放棄的方案）

| # | 決定 | 原因 | 放棄的方案與原因 |
|---|---|---|---|
| D1 | 純 Framework、無 Gradle、npm 工具鏈 | 當初環境連不到 Google Maven，只能這樣建置 | Gradle＋AndroidX：環境無法下載 |
| D2 | 相簿做成**虛擬相簿**（DB 清單，不搬檔） | 參考 App 的「複製相簿」「不在任何相簿中」都建立在這個模型上；一張可在多本；不必每次跳系統確認 | 實體資料夾當相簿：每次搬檔都要系統確認，且一張只能在一處（保留為「移到資料夾」功能） |
| D3 | 外觀維持深色主題，只參考操作方式 | 倬說「不一定要重新大改界面，功能具備、操作類似即可」，且偏好深色 HSL 色票 | 照抄參考 App 的淺藍白外觀 |
| D4 | 網路權限**只用於 App 內更新**（v1.2.0 起；之前完全沒有網路權限） | 照片不會被上傳是本 App 的隱私承諾（README 寫明）；2026-10-18 倬決定加 INTERNET 只做更新檢查與下載，照片、相簿、位置都不經過網路 | 完全不加權限、只開瀏覽器到 Releases（倬選擇完整 App 內更新） |
| D5 | 位置分頁只顯示座標＋「在地圖 App 中開啟」 | 內嵌地圖需要網路權限與 API key，違反 D4 | 內嵌 Google 地圖 |
| D6 | 備份＝JSON 經系統檔案選擇器（SAF）匯出／匯入，還原為**合併** | 不需任何權限；可存到下載、雲端硬碟、Dropbox；換手機時用 檔名＋大小＋時間 比對 | 串接 Drive／Dropbox API：要網路權限、OAuth 與外部函式庫 |
| D7 | 不做參考 App 的「好友」「QR 碼掃描器」 | 好友需雲端帳號；QR 需相機權限，與 App 定位不符 | — |
| D8 | 照片牆點選由整個 MediaGrid 依座標判斷 | 倬實機回報多選時最右一欄點不到、似乎只有點圓圈才勾得到 | 每格掛 click listener |
| D9 | 關閉 ListView 快速捲動軸，改用年／月膠囊跳轉 | 快速捲動軸的觸控區吃掉最右一欄的點擊 | — |
| D10 | 格子寬度用權重平分＋正方形格子；圖示字級依實際格子大小等比例縮放 | 倬截圖回報：相簿欄出現時最右一欄被截斷、6 欄時日期字被切掉、勾選圓圈太大 | 預先算好像素寬度、固定 dp 尺寸 |
| D11 | 往右滑＝打開相簿整理（空選取、sticky）；往左滑只在**沒有選取**時收起 | 倬要求往右滑進入；避免誤滑把選好的照片取消 | 往左滑一律取消 |
| D12 | 拖曳到相簿時只改外框與底色，不放大卡片 | 放大會超出相簿欄被裁切（倬回報） | 放大 1.06 倍 |
| D13 | ~~「從某張開始整理」對話框~~ v1.3.0 起改成：在照片頁點開哪一張就從哪一張開始，左滑往舊、右滑往新；只看未整理用照片頁篩選 | 整理頁已併入單張檢視（D21） | 保留 4 選項對話框（檢視器裡已經能直接左右滑，多一層選單反而慢） |
| D14 | v1.2.0 起改用共用簽章 `hoonsor-release.jks`＋GitHub Actions 發布 | 免接線更新與 Agent API 都需要共用簽章；2026-10-18 倬決定換（一次性：備份 → 卸載 → 安裝 → 匯入）。2026-10-13 原決定暫不換已取消 | 繼續用自帶的 `keystore.p12`（CI 拿不到、無法和其他 APP 互通） |
| D15 | 簽章金鑰與密碼一律由環境變數提供（`SIGNING_*`；舊金鑰 `PB_KS_PASS`）；找不到金鑰時停止 | repo 是公開的，不能放密碼（2026-10-13 倬決定）；自動產生新金鑰曾造成 v1.0.6→v1.1.0 必須卸載 | 密碼寫死在 build.sh |
| D16 | 程式碼放在英文路徑 `D:\dev\APK-Photo`，中文資料夾只留筆記與舊快照 | 方便建置與安裝手機測試，並與其他 APK 專案的標準骨架一致（2026-10-18 確認已搬家；原 2026-10-13 決定暫不搬已取消） | 繼續在中文路徑 `30-手機照片APP` 開發 |
| D17 | 相簿內頁不提供「從這本相簿開始整理」 | 整理來源目前只支援資料夾與年月，硬做會從全部照片接續，名不符實 | 曾短暫加入選單後移除 |
| D18 | 單張檢視下方按鈕改為 資訊／喜好／加入相簿／分享／刪除，保留／待刪移到 ⋮ | 對齊參考 App 的四顆按鈕；原本五個整理動作放不下 | — |
| D19 | 刪除在檢視器可選「加入待刪」或「立即移到系統垃圾桶」 | 兩種習慣都保留；仍一律走系統確認 | — |
| D20 | LINE 篩選依 RELATIVE_PATH 或資料夾名為「LINE」 | LINE 存檔路徑是 Pictures/LINE、Movies/LINE | 依檔名判斷（不可靠） |
| D21 | v1.3.0 移除「整理」分頁，整理併進單張檢視：沒放大時上滑＝加入待刪（自動下一張、10 秒復原列）、下滑＝編輯；放大時滑動一律是拖曳 | 2026-10-26 倬要求：在照片頁看照片時就能整理，不必切分頁；待刪分頁保留，才知道去哪裡丟垃圾桶／永久清除 | 保留整理分頁並存（兩套手勢容易混淆）；手勢可自訂（左右已固定給換張，只剩上下兩個方向，固定比較不會誤觸） |
| D22 | 編輯器一律「另存新照片」：同資料夾、檔名加「_編輯_時間」、DATE_TAKEN 沿用原照片 | 覆蓋原檔要系統逐張確認，且改壞救不回來；沿用拍攝時間讓新照片排在原照片旁邊 | 覆蓋原檔；存到固定的「照片整理箱」資料夾（只在原資料夾不能寫入時才用） |
| D24 | 待刪的照片不在檢視器左右滑的範圍：開啟時濾掉、上滑時從清單移除（復原放回原位）；點開的那張本身是待刪時整份清單照原樣 | 倬回報：第 2 張加入待刪後，從第 3 張往回滑應直接到第 1 張 | 只標記不移除（倬覺得多餘）；連照片牆也隱藏待刪（未要求，照片頁仍可用「待刪」篩選） |
| D25 | 編輯器粗細用 1～100 滑桿，線寬＝照片短邊 × (0.15%～3%)、字級＝短邊 × (2%～12%)，旁邊用實際比例預覽 | 倬要求像畫筆那樣滑動調整，不要細中粗三段 | 細／中／粗三段按鈕 |
| D26 | 檢視器「一般瀏覽」模式（hidePending）在 onStoreChanged 與 onResume 都清掉待刪照片；`ViewerActivity.open()` 800ms 內不重複開 | 倬回報上滑待刪後往回滑「有時」還看得到帶待刪標籤的照片：原本只在開啟時過濾、只移除自己加的那張，快速點兩下疊開兩個檢視器、或在別處加入待刪時就會漏 | 只在開啟時過濾一次 |
| D27 | 馬賽克＝原圖縮成每格一像素的小圖，用不平滑的 BitmapShader 放大畫出（框選填矩形、筆刷畫粗線），格子＝短邊 2.5% | 不必另外存一張全尺寸馬賽克圖（省約 50MB）；取原圖顏色，蓋到其他標註也會遮住 | 高斯模糊（需 RenderEffect／自寫卷積，且模糊不如方格遮得乾淨） |
| D28 | 照片頁相簿欄是疊在照片牆上的一層，**跟著手指拉出／收起**（`MediaGrid.HDragListener`）：拖曳中照片牆維持全寬整片位移，放開依距離或速度補完；照片牆只在完全打開（變窄）與開始收起（恢復全寬）各排版一次 | 倬回報動畫卡，並給參考 App 錄影：原本每格畫面改相簿欄寬度，照片牆每格都整個重排；且要放開才開始動，手指和畫面脫節 | 每格改寬度（v1.1.6～v1.3.0 前）；放開後才播固定動畫 |
| D23 | 編輯器載入時最長邊上限 4096 px，用 ImageDecoder（自動套 EXIF 方向） | 4096×3072 ARGB 約 50MB，存檔還要複製一份，再大容易 OOM | 原尺寸載入；分塊（tile）編輯：複雜度太高 |

| D29 | 「送到資料包」只交讀取授權給賈維斯資料包（`Share.toDataPack`，manifest `<queries>` 判斷有沒有安裝），不在本 App 做 OCR 或連網 | 2026-10-31 倬定案：本 App 無 Gradle、無第三方函式庫，且承諾照片不經網路（D4） | 把 OCR 放進本 App：要重寫建置並打破 D4 |

**待倬決定（2026-10-10 討論過，尚未回覆）**
- 定期自動備份到雲端硬碟／Dropbox：建議做法是「SAF 取得長期寫入授權＋JobScheduler 定期覆寫同一檔案」，由雲端 App 負責上傳，本 App 仍不需網路權限。頻率未定。
- 串接 MiniMax M3（國際版，Anthropic 相容 API，支援圖片）判斷待刪照片：需要網路權限，打破 D4。
  選項 (a) 正式版加網路權限、AI 預設關閉；(b) 另出 AI 版；(c) 改做離線的模糊／過暗偵測。
  另需決定 AI 建議是直接進待刪，或先進「AI 建議」清單由倬確認。
  注意：倬的 APP 標準寫的是 OpenAI 相容 API＋`reasoning_split`，與當時查到的 Anthropic 相容端點不同，實作前再確認。

## 6. Always／Ask／Never

**Always**
- 介面、文件、回覆一律繁體中文（技術名詞保留英文）
- 每一版：改 `AndroidManifest.xml` 版號（設定頁版號從 PackageManager 讀，不用改）、更新 `PROJECT_STATUS.md`（寫**為什麼**）、同時產出正式版與 LAB 版、跑 `tests/run.sh`、做 dex 字串檢查
- 功能改變時更新 `README.md`；動作改變時同步 `docs/AGENT_API.md` 與 `docs/agent-manifest.json`；架構或踩坑時更新本檔
- 寫入／刪除媒體用 `it.uri()`，走 `Media` 的 PendingIntent 流程；狀態走 `Store`；相簿走 `Albums`
- 新的純邏輯抽成不引用 Android 的類別並補 JVM 測試
- 做不到就直說原因

**Ask（先問倬）**
- 新增任何權限，尤其是網路權限
- 更換簽章金鑰、套件名，或任何會逼倬卸載重裝的變更
- 會丟資料的 DB 結構變更
- 拿掉既有功能、改變檢視器手勢（D21）或長按語意
- 改建置方式（例如導入 Gradle；GitHub Actions 已於 v1.2.0 導入）

**Never**
- 提交 `keystore.p12`、任何密碼、API key（repo 是公開的）
- 讓 `build.sh` 自動產生新金鑰
- 靜默刪除檔案（一律系統確認）
- 逆向或複製任何商業 App 的程式與素材
- 重新打開 ListView 快速捲動軸、在照片牆格子上掛 click listener、用固定像素算格子寬度
- 把「相簿」與「資料夾」混用

## 7. 絕對不要重踩的坑

1. **刪除／移動必須用「媒體項目 URI」**（`images/media/<id>`、`video/media/<id>`），不是 Files 表的 `file/<id>`。
   用錯會丟 `IllegalArgumentException`，而且和權限無關（v1.0.1 倬實機截圖抓到）。`it.uri()` 給寫入、`it.fileUri()` 只給查詢。
2. **刪除不能靜默**：`Media.requestTrash/requestDelete/requestWrite` → 系統對話框 → `onActivityResult` 成功才 `Store.forget()`。
3. **系統垃圾桶只看得到自己丟的**：其他 App 丟的只有系統相簿看得見；`trashed` 表就是為了記住自己丟過哪些。
4. **Android 14「只選取部分照片」**：`Media.partialOnly()` 偵測到時必須引導改成「允許全部」。
5. **相似照片門檻**：aHash 漢明 >6、dHash >10、長寬比差 >0.18 即不相似（依成本順序）。雜湊寫入 `ahash` 快取，改演算法要讓舊快取失效。
6. **monochrome 圖示層的黑色不是透明**：系統會整層著色；挖洞只能用真透明（evenOdd 或反向繞行）；圖稿要在 108dp 畫布中央 66dp 安全圓內。
7. **記憶體**：`largeHeap="true"`；縮圖一律走 `Thumbs` 兩層 LruCache，不要自己 decode 全尺寸。
8. **檢視器滑動範圍**用 `ViewerActivity.open(ctx, item, list)`（id 清單放靜態欄位，避免塞爆 Intent）。
9. **`MediaGrid.addHeader()` 必須在第一次 `setItems()` 之前**（ListView 限制）。
10. **位置需要 `ACCESS_MEDIA_LOCATION` 執行期權限＋`MediaStore.setRequireOriginal()`**，否則 GPS 被抹掉。影片位置是 ISO 6709 字串（`Geo` 解析）。
11. **分享要帶 ClipData＋`FLAG_GRANT_READ_URI_PERMISSION`**，混合照片影片時 MIME 用 `*/*`，否則部分 LINE／雲端 App 讀不到。
12. **照片牆點選與排版**：點擊、長按、雙指縮放、往右滑都在 `MediaGrid.dispatchTouchEvent` 統一處理，用 `hitCell` 依座標換算格子；
    格子用 `weight=1`＋`SquareFrame`；勾選圓圈、影片圖示、狀態點、日期格字級在 `SquareFrame.applyScale()` 依實際像素計算。
    不要開快速捲動軸、不要在格子掛 listener、不要用固定像素或固定 dp（三者都被倬實機回報過）。
13. **長按有三種意思**：未多選→進入多選；多選中長按已選→`DragStack` 拖放（只有照片頁有相簿欄時）；多選中長按未選→範圍選取。
    拖放放開後用 `post` 再改畫面，不要在 `ACTION_DROP` 當下重建側欄；拖到相簿上不要放大卡片。
    點日期格＝整天全選／全取消（`setDateSelectEnabled(false)` 可關閉）。
14. **簽章金鑰遺失會逼使用者卸載**：v1.0.6 的金鑰在已回收的容器裡遺失，v1.1.0 起換了新金鑰，倬必須卸載重裝、整理進度歸零。
    舊的 `keystore.p12` 仍要保留（替還沒換裝的手機建置）；v1.2.0 起正式版用共用金鑰。Android 12+ 不同簽章的 APP 宣告同名權限會安裝失敗（日後 Agent API 用 signature 權限時要注意）。
15. **Agent API 的 signature 權限**：標準 Agent API 用 `protectionLevel="signature"`，大腦 APP 用共用金鑰簽，
    v1.2.0 起本 App 也用共用金鑰，已可互通（倬的手機要先完成一次「備份 → 卸載 → 安裝 → 匯入」換裝）。

## 8. 倬的偏好

- 繁體中文；深色主題、精緻 HSL 色票，**不用純色原色**（色票集中在 `T.java`）
- **不接受廉價 MVP 感**：按鈕要有邊框或對比、手繪向量圖示（`Icons.java`）、間距一致
- 功能要完整：CRUD 齊全、多選動作要和單張檢視一致
- 資訊密度高：雙指縮放改每列數量；長按多選；多選後長按已選項拖到相簿
- 常用操作減步驟（例如整理直接在單張檢視上下滑，不必切到另一個分頁）
- 參考常用 App 的操作方式，但不照抄外觀
- 刪除可復原、危險操作走系統確認
- SemVer `vX.Y.Z`，versionCode 由版號推導
- 每版 `PROJECT_STATUS.md` 要寫「為什麼」
- 做不到要直說原因，不給看起來可行但實際無效的做法
- 穩定、可靠優先

## 9. 姊妹專案

「手勢精靈」（`com.gesturebox.auto`）用同一套建置流程（`setup.sh`／`build.sh`／`pack.py` 可互換）。
可參考它的 `Backup.java`（zip 匯出入）與 `Vision.java`（可 JVM 測試的影像比對核心）。
