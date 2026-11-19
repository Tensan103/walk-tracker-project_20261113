# タスク: フォアグラウンド処理の安定化・設定永続化・検証用スイッチの実装

## 1. 目的と背景
実機検証で見つかったフォアグラウンドサービス周りの3つの課題を解消し、意図通りに動作し続ける状態にする。
1. 「検知中」に画面をOFFにした際、`RELEASE` を確実に記録・AWS送信する。
2. アプリをスワイプアウト（タスクキル）しても動作を継続させ、検知設定・警告設定が初期化されないよう永続化する。
3. 意図せず「待機中」になる現象を防ぎつつ、検証用端末で一時停止できる「監視ON/OFFスイッチ」を設定画面に追加する。

## 2. 対象コンポーネント
- `/android-app` 配下のファイル
  - `WalkTrackerApplication.kt` (長寿命CoroutineScope、起動時の画面状態チェック、Receiver制御)
  - `SensorRepository.kt` (`SharedPreferences` による設定の永続化と読み込み)
  - `SensorTrackingService.kt` (`onDestroy` 時の `RELEASE` 強制送信、`START_STICKY` 設定)
  - `SettingsScreen.kt` (検証用「監視ON/OFF」スイッチの追加)
  - `MainActivity.kt` (画面表示時のサービス状態同期)

## 3. 実装要件

### 3-1. 設定値の永続化 (`SharedPreferences`)
- `SensorRepository`（または新規作成する `SettingsPreferences` クラス）にて、`SharedPreferences` を用いて以下の設定値を保存・復元できるようにする。
  - 検知パラメータ（時間ウィンドウ・歩数しきい値など、現在設定可能な項目すべて）
  - 警告表示タイプ (`AlertType`)
  - 監視機能の有効/無効フラグ (`isMonitoringEnabled: Boolean`, 初期値 `true`)
- `Application.onCreate()` のタイミングで `SensorRepository.init(applicationContext)` 等を呼び出し、保存済みの設定値を `StateFlow` に復元すること。
- 設定画面から値が変更された際は、メモリ上の `StateFlow` と `SharedPreferences` の両方を即時更新すること。

### 3-2. 画面OFF時の `RELEASE` 強制送信とスワイプアウト対策
- **長寿命スコープの用意**: `WalkTrackerApplication` に `val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` を定義する。
- **`onDestroy()` での解除処理**:
  - `SensorTrackingService.onDestroy()` 内で、現在の状態が「歩行検知中（DETECT）」であるかを判定する。
  - 検知中の場合、即座に解除処理を実行し、経過秒数を計算して `SensorRepository` の履歴に追加するとともに、**`applicationScope.launch` を使って AWS (`POST /events`, `category = "RELEASE"`) へ送信**する（サービス自身のスコープでキャンセルされるのを防ぐため）。
  - オーバーレイが表示中の場合は、`windowManager.removeView()` で確実に消去する。
- **スワイプアウト継続**:
  - `SensorTrackingService.onStartCommand()` の戻り値を `START_STICKY` にする。
  - `onTaskRemoved()` ではサービスを停止しないこと。

### 3-3. 「待機中」になる問題の修正と検証用スイッチの追加
- **設定画面 (`SettingsScreen`) へのスイッチ追加**:
  - 画面上部に「監視機能を有効にする（検証用）」の `Switch` を配置する（現在の状態が「稼働中」か「待機中」かも併記）。
  - スイッチを **OFF** にした時: `isMonitoringEnabled = false` を保存し、稼働中の `SensorTrackingService` を即座に `stopService()` で停止する（明示的な待機中状態）。
  - スイッチを **ON** にした時: `isMonitoringEnabled = true` を保存し、即座に `ContextCompat.startForegroundService()` でサービスを開始する。
- **意図しない「待機中」の防止**:
  - `WalkTrackerApplication.onCreate()` および `MainActivity.onResume()` にて、`isMonitoringEnabled == true` かつ `PowerManager.isInteractive == true`（現在画面がON）である場合、サービスが未起動であれば `startForegroundService()` を呼ぶようにする。
  - `ScreenStateReceiver` の `ACTION_SCREEN_ON` 受信時は、`isMonitoringEnabled == true` の場合のみサービスを起動する。
  - Android 12以降のバックグラウンド起動制限による `ForegroundServiceStartNotAllowedException` でクラッシュしないよう、`startForegroundService()` 呼び出し箇所は `try-catch` で保護し、例外時はログ出力を行うこと。

## 4. 実行手順
1. ブランチ `feature/foreground-stability` を作成して切り替える。
2. 上記要件に従い、`SharedPreferences` 永続化、`onDestroy()` での `RELEASE` 送信、起動チェック・検証用スイッチを実装する。
3. ビルドエラーがないことを確認し、変更をコミットして完了を報告する。