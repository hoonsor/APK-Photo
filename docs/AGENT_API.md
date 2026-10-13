# 照片整理箱 Agent API（規劃中）

> **狀態：規劃中。** App 目前**尚未實作** Agent API，以下所有動作都還不能呼叫。
> `docs/agent-manifest.json` 是同一份清單的機器可讀版（每個動作都標 `"status": "planned"`）。
> 實作時 `describe` 的回傳必須與該 JSON 完全一致，並以單元測試比對。

讀者：接手實作的 AI、未來「AI 大腦 APP」的開發者。

## 1. 實作前提（重要）

- **簽章**：標準 Agent API 以 `protectionLevel="signature"` 的權限保護，大腦 APP 用共用金鑰 `hoonsor-release.jks` 簽。
  本 App 目前用自帶的 `keystore.p12`，簽章不同就無法互通。**實作前必須先換成共用簽章**；
  換簽章要卸載重裝，務必先在 App 內「更多 → 備份與還原 → 匯出備份檔」。
- **技術棧**：本 App 是純 Android Framework（無 AndroidX、無 Gradle），Provider 直接繼承 `android.content.ContentProvider` 即可。
- **寫入入口**：Provider 只負責解析與分派，動作一律走既有的 `Store`（整理狀態）、`Albums`（相簿）、`Media`（MediaStore 請求）、`Backup`，
  不要另寫一套資料存取。
- **刪除與搬移**：Android 11+ 規定刪除、搬移別人的媒體必須由使用者在系統對話框確認，
  所以 `trash_empty`、`photo_move_folder` 確認後也只能回 `NEEDS_UI`，由 App 開出系統畫面。

## 2. 協定（Hoonsor Agent API v1）

Manifest：
- 宣告權限 `tw.hoonsor.permission.AGENT`（`protectionLevel="signature"`）
- exported provider `.agent.AgentProvider`，`android:authorities="${applicationId}.agent"`（本 App：`com.photobox.sorter.agent`，LAB 版 `com.photobox.sorter.lab.agent`），
  `android:permission` 為上述權限，intent-filter action `tw.hoonsor.agent.PROVIDER`
- 本 App 用 aapt2 直接處理 manifest，沒有 `${applicationId}` 佔位符：authorities 要寫死，LAB 版由 build.sh 一併改名

`ContentProvider.call(method, arg, extras)`，結果放在回傳 Bundle 的 `json`：

| method | 輸入 | 回傳 |
|---|---|---|
| `describe` | — | `{api:1, app, package, version, description, actions:[{name, title, description, risk, params, examples}]}` |
| `execute` | extras `json`：`{api, requestId, action, params, dryRun, confirmToken, utterance}` | 成功 `{ok:true, requestId, summary, data, undo:{action, params}}`；失敗 `{ok:false, error:{code, message}}` |
| `health` | — | `{ok, issues:[...]}`（例如：沒有「允許全部」相片權限、只授權部分照片） |

錯誤碼：`INVALID_PARAMS`、`NOT_FOUND`、`AMBIGUOUS`（`data.candidates`）、`NEEDS_CONFIRMATION`（`data.confirmToken`）、
`NEEDS_UI`（`data.intentUri`）、`PERMISSION`、`INTERNAL`。

風險分級：
- `read`：直接執行
- `reversible`：直接執行，回傳 `undo`
- `destructive`：一律先回 `NEEDS_CONFIRMATION`，帶 `confirmToken` 再呼叫一次才執行

其他規則：背景執行緒、逾時 5 秒；設定頁要有「API 測試台」；debug 版可拿掉權限以便 `adb shell content call` 測試，release 必須有；
ColorOS 的「關聯啟動」可能擋住大腦喚醒本 App，健康檢查頁要引導開啟。

## 3. 共用參數

**`ids`**：照片／影片的 media id 陣列（由 `photo_list` 取得）。

**`filter`**（與 `ids` 二擇一，用條件選照片）：

| 欄位 | 型別 | 說明 |
|---|---|---|
| `query` | string | 檔名、資料夾，或日期 `2023`／`2023-07`／`2023-07-15`（沿用照片頁搜尋規則） |
| `date_from`／`date_to` | date | 拍攝日期範圍 |
| `state` | `any`／`unsorted`／`kept`／`pending` | 整理狀態 |
| `type` | `any`／`photo`／`video` | 類型 |
| `source` | `any`／`screenshot`／`line`／`large` | 截圖、LINE、大檔案（>8MB） |
| `folder` | string | 實體資料夾名稱 |
| `album` | string | 相簿名稱 |

**`album`**：相簿名稱或 id。名稱對到多本時回 `AMBIGUOUS` 並列出候選。

注意名詞：**相簿**＝App 內的虛擬清單（不搬檔）；**資料夾**＝手機上的實體位置。兩者不可混用。

## 4. 動作一覽（全部規劃中）

| 名稱 | 標題 | 風險 | 範例句 |
|---|---|---|---|
| `photo_list` | 查詢照片 | read | 「列出昨天拍的照片」／「找出 2023 年 7 月的影片」／「有哪些從 LINE 存下來的照片」 |
| `photo_get` | 照片詳細資訊 | read | 「這張照片是在哪裡拍的」／「這張是什麼手機拍的」 |
| `photo_open` | 開啟照片 | read | 「打開最新的那張照片」 |
| `stats_get` | 整理統計 | read | 「我還有多少照片沒整理」／「待刪的照片總共多大」 |
| `folder_list` | 列出資料夾 | read | 「我手機有哪些相片資料夾」 |
| `album_list` | 列出相簿 | read | 「我有哪些相簿」／「列出相簿」 |
| `album_get` | 相簿內容 | read | 「大陸購物那本相簿有幾張」 |
| `album_create` | 建立相簿 | reversible | 「建立一本叫「日本旅行」的相簿」／「把今天拍的照片建成新相簿「生日會」」 |
| `album_rename` | 重新命名相簿 | reversible | 「把新相簿改名成鹿野苑」 |
| `album_duplicate` | 複製相簿 | reversible | 「複製一份旅行相簿」 |
| `album_add_photos` | 加入相簿 | reversible | 「把 10 月 7 日的照片加到生日會相簿」／「把剛剛那些照片放進喜好項目」 |
| `album_remove_photos` | 移出相簿 | reversible | 「把截圖從旅行相簿拿掉」 |
| `album_set_cover` | 設定封面 | reversible | 「用這張當相簿封面」 |
| `album_delete` | 刪除相簿 | destructive | 「刪除新相簿這本」 |
| `photo_favorite_set` | 加入／移出喜好 | reversible | 「把這張加到喜好」 |
| `photo_mark` | 標記整理狀態 | reversible | 「把上個月的截圖都標成待刪」／「這張標成保留」／「清除這些照片的標記」 |
| `sort_start` | 開始滑動整理 | read | 「從 2023 年 7 月開始往新的整理」／「整理截圖資料夾」 |
| `pending_list` | 查看待刪清單 | read | 「待刪清單有什麼」 |
| `trash_empty` | 清空待刪 | destructive | 「把待刪的都清掉」 |
| `photo_move_folder` | 移到資料夾 | destructive | 「把這些截圖搬到 Pictures/收據」 |
| `photo_share` | 分享照片 | read | 「把生日會相簿分享到 LINE」／「分享今天拍的照片」 |
| `duplicate_scan` | 找相似照片 | read | 「幫我找重複的照片」 |
| `backup_export` | 匯出備份 | reversible | 「備份我的相簿」 |
| `backup_import` | 從備份還原 | destructive | 「從備份還原相簿」 |

## 5. 各動作說明

### `photo_list` — 查詢照片（規劃中）

- 用途：依條件列出照片與影片（id、檔名、時間、資料夾、大小、整理狀態）
- 風險：read（直接執行）
- 參數：`filter`、`sort`、`limit`
- 範例句：「列出昨天拍的照片」／「找出 2023 年 7 月的影片」／「有哪些從 LINE 存下來的照片」

### `photo_get` — 照片詳細資訊（規劃中）

- 用途：取得單張的 EXIF、位置（需權限）、所在相簿與完整路徑
- 風險：read（直接執行）
- 參數：`id`（必填）
- 範例句：「這張照片是在哪裡拍的」／「這張是什麼手機拍的」

### `photo_open` — 開啟照片（規劃中）

- 用途：在 App 內開啟某張照片的檢視畫面（會回 NEEDS_UI）
- 風險：read（直接執行）
- 參數：`id`（必填）
- 範例句：「打開最新的那張照片」

### `stats_get` — 整理統計（規劃中）

- 用途：照片／影片總數、未整理、已保留、待刪數量與可釋放空間、相簿數
- 風險：read（直接執行）
- 參數：無
- 範例句：「我還有多少照片沒整理」／「待刪的照片總共多大」

### `folder_list` — 列出資料夾（規劃中）

- 用途：列出手機上的實體資料夾（相機、截圖、LINE…）與數量
- 風險：read（直接執行）
- 參數：`limit`
- 範例句：「我手機有哪些相片資料夾」

### `album_list` — 列出相簿（規劃中）

- 用途：列出所有相簿（含喜好項目）與照片、影片數量
- 風險：read（直接執行）
- 參數：無
- 範例句：「我有哪些相簿」／「列出相簿」

### `album_get` — 相簿內容（規劃中）

- 用途：列出某本相簿裡的項目
- 風險：read（直接執行）
- 參數：`album`（必填）、`limit`
- 範例句：「大陸購物那本相簿有幾張」

### `album_create` — 建立相簿（規劃中）

- 用途：建立新相簿，可同時放入照片
- 風險：reversible（直接執行並回 undo）
- 參數：`name`（必填）、`ids`、`filter`
- 範例句：「建立一本叫「日本旅行」的相簿」／「把今天拍的照片建成新相簿「生日會」」
- 備註：undo：album_delete（只刪相簿，不刪照片）

### `album_rename` — 重新命名相簿（規劃中）

- 用途：改相簿名稱（喜好項目不能改）
- 風險：reversible（直接執行並回 undo）
- 參數：`album`（必填）、`name`（必填）
- 範例句：「把新相簿改名成鹿野苑」
- 備註：undo：album_rename 回原名

### `album_duplicate` — 複製相簿（規劃中）

- 用途：複製出一本內容相同的新相簿
- 風險：reversible（直接執行並回 undo）
- 參數：`album`（必填）、`name`
- 範例句：「複製一份旅行相簿」
- 備註：undo：album_delete 新相簿

### `album_add_photos` — 加入相簿（規劃中）

- 用途：把照片放進相簿（不搬檔案，同一張可在多本）
- 風險：reversible（直接執行並回 undo）
- 參數：`album`（必填）、`ids`、`filter`
- 範例句：「把 10 月 7 日的照片加到生日會相簿」／「把剛剛那些照片放進喜好項目」
- 備註：undo：album_remove_photos 同一批

### `album_remove_photos` — 移出相簿（規劃中）

- 用途：把照片從相簿移出（檔案本身不受影響）
- 風險：reversible（直接執行並回 undo）
- 參數：`album`（必填）、`ids`、`filter`
- 範例句：「把截圖從旅行相簿拿掉」
- 備註：undo：album_add_photos 同一批

### `album_set_cover` — 設定封面（規劃中）

- 用途：指定相簿封面
- 風險：reversible（直接執行並回 undo）
- 參數：`album`（必填）、`id`（必填）
- 範例句：「用這張當相簿封面」
- 備註：undo：album_set_cover 回原封面

### `album_delete` — 刪除相簿（規劃中）

- 用途：刪除相簿本身；照片與影片不會被刪
- 風險：destructive（一律先確認）
- 參數：`album`（必填）
- 範例句：「刪除新相簿這本」
- 備註：一律先回 NEEDS_CONFIRMATION；undo 需重建相簿與成員，回傳時附上成員清單

### `photo_favorite_set` — 加入／移出喜好（規劃中）

- 用途：把照片加入或移出「喜好項目」
- 風險：reversible（直接執行並回 undo）
- 參數：`ids`（必填）、`on`
- 範例句：「把這張加到喜好」
- 備註：undo：photo_favorite_set 反向

### `photo_mark` — 標記整理狀態（規劃中）

- 用途：標記為保留、待刪，或清除標記（不會刪檔）
- 風險：reversible（直接執行並回 undo）
- 參數：`ids`、`filter`、`state`（必填）
- 範例句：「把上個月的截圖都標成待刪」／「這張標成保留」／「清除這些照片的標記」
- 備註：undo：photo_mark 回各自原狀態

### `sort_start` — 開始滑動整理（規劃中）

- 用途：切到整理分頁，從某張或某天開始往舊／往新整理（會回 NEEDS_UI）
- 風險：read（直接執行）
- 參數：`from_id`、`from_date`、`direction`、`include`、`folder`
- 範例句：「從 2023 年 7 月開始往新的整理」／「整理截圖資料夾」

### `pending_list` — 查看待刪清單（規劃中）

- 用途：列出待刪清單與可釋放空間
- 風險：read（直接執行）
- 參數：`limit`
- 範例句：「待刪清單有什麼」

### `trash_empty` — 清空待刪（規劃中）

- 用途：把待刪清單送進系統垃圾桶（或依設定永久刪除），需要系統確認畫面
- 風險：destructive（一律先確認）
- 參數：`permanent`
- 範例句：「把待刪的都清掉」
- 備註：先回 NEEDS_CONFIRMATION，確認後回 NEEDS_UI（Android 一定要使用者在系統對話框按允許）

### `photo_move_folder` — 移到資料夾（規劃中）

- 用途：實際搬移檔案到另一個資料夾，需要系統確認畫面
- 風險：destructive（一律先確認）
- 參數：`ids`、`filter`、`folder`（必填）
- 範例句：「把這些截圖搬到 Pictures/收據」
- 備註：先回 NEEDS_CONFIRMATION，再回 NEEDS_UI

### `photo_share` — 分享照片（規劃中）

- 用途：開啟系統分享選單分享選取的照片（LINE、Dropbox、雲端硬碟）
- 風險：read（直接執行）
- 參數：`ids`、`filter`、`album`
- 範例句：「把生日會相簿分享到 LINE」／「分享今天拍的照片」
- 備註：回 NEEDS_UI（分享選單需使用者選目標）

### `duplicate_scan` — 找相似照片（規劃中）

- 用途：開啟重複／相似照片掃描畫面
- 風險：read（直接執行）
- 參數：無
- 範例句：「幫我找重複的照片」
- 備註：回 NEEDS_UI

### `backup_export` — 匯出備份（規劃中）

- 用途：把相簿、喜好與整理標記匯出成備份檔
- 風險：reversible（直接執行並回 undo）
- 參數：無
- 範例句：「備份我的相簿」
- 備註：目前需使用者在檔案選擇器選位置，回 NEEDS_UI；未來若做自動備份可直接寫入已授權的檔案

### `backup_import` — 從備份還原（規劃中）

- 用途：從備份檔合併還原相簿與標記
- 風險：destructive（一律先確認）
- 參數：無
- 範例句：「從備份還原相簿」
- 備註：先回 NEEDS_CONFIRMATION，再回 NEEDS_UI（選檔）

## 6. 回傳範例

```jsonc
// execute：album_add_photos 成功
{"ok": true, "requestId": "r-102", "summary": "已把 12 張加入「生日會」（3 張原本就在裡面）",
 "data": {"album": {"id": 7, "name": "生日會"}, "added": 12, "already": 3},
 "undo": {"action": "album_remove_photos", "params": {"album": "7", "ids": [101, 102]}}}

// execute：album_delete 第一次呼叫
{"ok": false, "error": {"code": "NEEDS_CONFIRMATION", "message": "刪除相簿「新相簿」（10 張照片、0 部影片的清單），照片本身不會刪除"},
 "data": {"confirmToken": "c-8f2a"}}

// execute：trash_empty 確認後
{"ok": false, "error": {"code": "NEEDS_UI", "message": "需要在系統對話框允許刪除 42 個項目"},
 "data": {"intentUri": "intent:#Intent;component=com.photobox.sorter/.MainActivity;S.open=trash;end"}}
```

## 7. 實作順序建議

1. 換共用簽章（前提）
2. `describe`＋`health`＋read 類（`photo_list`、`stats_get`、`album_list`、`album_get`、`folder_list`、`pending_list`）
3. reversible 類（相簿、標記、喜好），每個都回 `undo`
4. destructive 與 NEEDS_UI 類
5. 設定頁「API 測試台」；把 `filter` 解析抽成純 Java 類別並補 JVM 測試
