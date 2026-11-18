# タスク: Androidアプリのバックグラウンド実行化 (Foreground Service)

## 1. 目的と背景
画面消灯時（スリープ状態）でも、歩行検知センサー（STEP_DETECTOR）と加速度センサー（ACCELEROMETER）の監視を継続し、設定条件（T秒間にS歩）を満たした際にAWSへAPIリクエストを送信できるようにする。
そのために、既存の `MainActivity` に記述されているセンサー処理を切り離し、`Foreground Service` として常駐稼働させるアーキテクチャへのリファクタリングを行う。

## 2. 対象コンポーネント
- `/android-app` 配下の Kotlin ファイル および `AndroidManifest.xml`

## 3. 実装要件

### 3-1. 権限 (Permissions) の追加
Android 14 (API 34) 以降の要件を満たすため、`AndroidManifest.xml` に以下の権限を追加すること。
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
- 歩数計やヘルスケア関連のセンサーを利用するため、適切な Foreground Service Type の権限（例: `FOREGROUND_SERVICE_HEALTH` または `FOREGROUND_SERVICE_SPECIAL_USE`。API仕様に基づき最適なものを選択すること）。
- `ACTIVITY_RECOGNITION` （既存のものを維持）。

### 3-2. SensorService (Foreground Service) の作成
- `Service` を継承した `SensorTrackingService` (または適切な命名) を新規作成する。
- サービス起動時（`onStartCommand`）に、Notification APIを用いてステータスバーに永続的な通知（Notification）を表示し、`startForeground()` を呼び出すこと。
- 通知のタイトルは「歩行検知システム稼働中」、テキストは「センサーを監視しています」とする（NotificationChannelの作成も忘れずに実装すること）。
- 現在 `MainActivity` にある以下のロジックをサービス内に移植する。
  - `SensorManager` による `TYPE_ACCELEROMETER` と `TYPE_STEP_DETECTOR` のリスナー登録・解除処理。
  - T秒間にS歩を検知するスライディングウィンドウロジック。
  - CSVファイルへのログ書き込み処理（Coroutines利用）。
  - OkHttpを用いたAWS APIへの非同期POST送信処理（DETECT/RELEASE）。

### 3-3. MainActivity と UI の連携
- `MainActivity` の「記録を開始 / 停止」ボタンを押した際、`Intent` を用いて Foreground Service を `startService` / `stopService` するよう修正する。
- サービスがバックグラウンドで処理している「現在の加速度」や「累計歩数」の値を `MainActivity` のJetpack Compose UIにリアルタイムに反映させるための通信手段を実装する。
  - ※推奨: `StateFlow` または `SharedFlow` などのKotlin Coroutines機能を利用したシングルトン（Repositoryパターン等）経由での状態共有、もしくは `LocalBroadcastManager` など、軽量でComposeと相性の良い手法を採用すること。

## 4. 制約事項・注意点
- バッテリー消費を抑えるため、サービスが不要にWakeLockを取得し続ける実装は避けること（センサーリスナーが稼働していれば歩行イベントはOSから通知される前提とする）。
- Androidのビルドや実行はユーザーがWindows側で行うため、**Claudeはコードの変更のみを行い、`./gradlew` コマンド等をWSL上で実行しようとしないこと。**

## 5. 作業手順
1. Gitブランチ `feature/android-foreground` を作成して切り替える。
2. 上記の実装要件を満たすコードの修正・新規作成を行う。
3. すべての実装が完了したら、`git add .` および `git commit -m "feat: Implement Foreground Service for sensor tracking"` を実行する。
4. 変更の要点をユーザーに日本語で簡潔に報告する。