# タスク: 歩行検知時の警告表示機能（4種）と権限チェック・設定画面の実装

## 1. 目的と背景
歩行検知（DETECT）が発生した際、ユーザーが他のアプリを操作中であっても状態を通知・警告できるようにする。
警告手段として、「なし」「トースト」「前面強制移動 (Full-Screen Intent)」「システム警告ウィンドウ (オーバーレイ)」の4種類から選択できる設定画面を実装する。
特に後者2つはOSの強力な制限を受けるため、設定画面で選択時に適切な権限チェックとユーザー誘導を行う。

## 2. 対象コンポーネント
- `/android-app` 配下のファイル
  - `SensorRepository.kt` (設定値の保持)
  - `SensorTrackingService.kt` (警告の実行)
  - `SettingsScreen.kt` (UIと権限ハンドリング)
  - `AndroidManifest.xml` (権限の追加)

## 3. 実装要件

### 3-1. 権限 (Permissions) の追加
`AndroidManifest.xml` に以下の権限を追加すること。
- `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`
- `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`

### 3-2. 設定値の管理
- 警告の種類を表す Enum クラス `AlertType` を作成する（`NONE`, `TOAST`, `FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`）。
- `SensorRepository` に現在の設定値を保持する `StateFlow<AlertType>` を追加する（初期値は `NONE`、インメモリ管理で可）。

### 3-3. SettingsScreen における権限チェックと設定ロジック
ラジオボタン等で `AlertType` を選択した際、以下の権限チェックを行い、権限がない場合はOSの設定画面へユーザーを誘導すること。

- **`FULL_SCREEN_INTENT` 選択時**:
  - Android 14 (API 34) 以上の場合、`NotificationManager.canUseFullScreenIntent()` でチェック。
  - falseの場合: 設定値は更新せず、`Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` のIntentを発行して設定画面へ誘導する。
- **`SYSTEM_ALERT_WINDOW` 選択時**:
  - `Settings.canDrawOverlays(context)` でチェック。
  - falseの場合: 設定値は更新せず、`Settings.ACTION_MANAGE_OVERLAY_PERMISSION` のIntent（自アプリのパッケージURIを付与）を発行して誘導する。

※ユーザーが設定画面から戻ってきた際の再チェック、または選択操作の再試行により、権限が許可されていればRepositoryの値を更新する仕様とする。

### 3-4. Service側での警告実行ロジック
`SensorTrackingService` にて歩行検知（DETECT）条件を満たした際、Repositoryの `AlertType` に応じて以下を実行する。

- **`NONE`**: 何もしない。
- **`TOAST`**: `Toast.makeText()` で警告を表示する。
- **`FULL_SCREEN_INTENT`**:
  - NotificationChannel（重要度: HIGH）を作成し、PendingIntentを `setFullScreenIntent()` に指定した通知を発行して、Activityを前面に強制起動する。
- **`SYSTEM_ALERT_WINDOW`**:
  - `WindowManager` を取得し、`LayoutParams.TYPE_APPLICATION_OVERLAY` を用いて、最前面に警告View（例: 赤背景に「歩行検知！」と書かれたシンプルなレイアウト）を追加する。
  - 追加したViewは、タップされた時や数秒後（RELEASE時など）に `windowManager.removeView()` で適切に破棄し、画面に残り続けないよう制御すること。

## 4. 実行手順
1. ブランチ `feature/alert-settings-advanced` を作成する。
2. Manifestへの権限追加、Repositoryの拡張を行う。
3. SettingsScreenを作成し、UIと権限チェック・設定画面誘導ロジックを実装する。
4. Service側に4パターンの警告分岐ロジック（NotificationおよびWindowManagerによるView操作）を実装する。
5. コードの変更をコミットし、完了を報告する。