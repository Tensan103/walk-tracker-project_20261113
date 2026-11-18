# タスク: 画面ON/OFF連動によるサービス制御とUI改修

## 1. 目的と背景
ユーザーが手動で「開始/停止」ボタンを押す運用を廃止し、スマートフォンの画面状態（スクリーンON/OFF）に完全に連動して歩行検知サービスが自動で稼働・停止するように改修する。
また、動作確認のため、分離したMainScreen（歩行検知画面）にも現在の累計歩数を表示する。

## 2. 対象コンポーネント
- `/android-app` 配下のファイル
  - `MainActivity.kt` (または `Application` クラス)
  - `MainScreen.kt` (本番用UI)
  - `SensorTrackingService.kt`
  - 新規作成する `BroadcastReceiver`

## 3. 実装要件

### 3-1. 画面ON/OFFの検知とサービスの自動制御 (BroadcastReceiver)
- 画面のON/OFFイベント（`Intent.ACTION_SCREEN_ON` および `Intent.ACTION_SCREEN_OFF`）を受信する `BroadcastReceiver` (例: `ScreenStateReceiver`) を実装すること。
- **受信時の動作**:
  - `SCREEN_ON` 受信時: Foreground Service (`SensorTrackingService`) を開始 (`startService` または `startForegroundService`) する。
  - `SCREEN_OFF` 受信時: Foreground Service を停止 (`stopService`) する。
- **レシーバーの登録場所**:
  - `ACTION_SCREEN_ON`/`OFF` は Manifest での静的登録ができない（OS仕様）ため、コード上で動的（動的登録）に行うこと。
  - `MainActivity` のライフサイクル（`onCreate`等）や、アプリ全体の `Application` クラスで登録し、アプリのプロセスが生きている限り監視できるように設計すること。

### 3-2. MainScreen (歩行検知画面) のUI改修
- 従来の「開始/停止」ボタン、およびトグル用のStateやロジックを `MainScreen` から完全に削除すること（自動化されるため不要）。
- 動作確認のため、現在のステータス（「監視中」/「歩行検知！」）の下に、`SensorRepository` (またはViewModel) から取得した「現在の累計歩数」を大きく表示すること。

### 3-3. ロガー画面（LoggerScreen）の仕様調整（留意事項）
- 画面OFF時にサービスが自動停止すると、LoggerScreenでのロギング処理も停止する挙動となるが、今回は仕様として許容する。（画面がONのときだけロガーが動く設計でOK）

## 4. 実行手順
1. ブランチ `feature/screen-state-sync` を作成する。
2. 上記の要件に従い、BroadcastReceiverの実装とUIの不要コード削除・歩数追加を行う。
3. アプリケーション層での動的登録漏れがないか確認する。
4. コードの変更をコミットし、完了を報告する。