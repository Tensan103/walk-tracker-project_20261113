# タスク: 歩行検知時の警告表示機能（トースト/フルスクリーン）と設定画面の実装

## 1. 目的と背景
歩行検知（DETECT）が発生した際、ユーザーが他のアプリを操作中であっても状態を通知・警告できるようにする。
用途に合わせて、警告を「なし」「トースト」「前面強制移動」の3種類から選択できる設定画面を実装する。

## 2. 対象コンポーネント
- `/android-app` 配下のファイル
  - `SensorRepository.kt` (設定値の保持)
  - `SensorTrackingService.kt` (警告の実行)
  - `SettingsScreen.kt` (新規作成するUI)
  - `MainActivity.kt` (Navigationのタブ追加、フルスクリーン表示の受け口)
  - `AndroidManifest.xml` (必要な権限の追加)

## 3. 実装要件

### 3-1. 権限 (Permissions) の追加
- バックグラウンドからの画面強制起動（Full-Screen Intent）を実現するため、`AndroidManifest.xml` に以下の権限を追加すること。
  - `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`

### 3-2. 設定値の管理
- 警告の種類を表す Enum クラス `AlertType` を作成する（`NONE`, `TOAST`, `FORCE_FOREGROUND`）。
- `SensorRepository` に現在の設定値を保持する状態（例: `StateFlow<AlertType>`）を追加する（初期値は `NONE`）。
- ※今回は永続化（DataStore等）は不要とし、アプリ終了でリセットされるインメモリ管理でよい。

### 3-3. 設定画面 (SettingsScreen) の実装
- 新規に `SettingsScreen` コンポーザブルを作成し、Bottom Navigationのタブに追加する。
- 画面内にラジオボタン、またはドロップダウンメニューを配置し、ユーザーが警告の種類（「なし」「トーストで通知」「検知画面を強制表示」）を選択・変更できるようにする。

### 3-4. Service側での警告実行ロジック
`SensorTrackingService` 内の「歩行検知 (DETECT)」の条件を満たしたタイミングで、Repositoryの設定値を読み取り、以下の処理を分岐実行する。

- **`NONE` (なし)**: 何もしない。
- **`TOAST` (トースト)**: `Toast.makeText()` を用いて "歩行を検知しました" と表示する（バックグラウンドからでも表示可能）。
- **`FORCE_FOREGROUND` (前面強制移動)**:
  - Android 10以降のバックグラウンド起動制限を回避するため、**Full-Screen Intentを付与した High Priority Notification (重要度「緊急」の通知)** を発行する手法を用いること。
  - PendingIntentを作成し、それを `setFullScreenIntent()` に指定した通知を発行することで、`MainActivity`（または専用のアラートActivity）が前面に強制的に起動するように実装する。

## 4. 実行手順
1. ブランチ `feature/alert-settings` を作成する。
2. 権限の追加、Repositoryの拡張を行う。
3. SettingsScreenを作成し、Navigationにルーティングを追加する。
4. Service側に警告分岐ロジックとFull-Screen Intent通知の実装を行う。
5. コードの変更をコミットし、完了を報告する。