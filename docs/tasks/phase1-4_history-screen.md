# タスク: 歩行検知の履歴画面 (HistoryScreen) の追加とペジネーション実装

## 1. 目的と背景
AWS側の記録とアプリ側の動作を照らし合わせてテストするため、直近の「歩行検知」の履歴をアプリ内で確認できる画面を追加する。
実装コストの高いGPS情報などは除外し、「検知時刻」「解除時刻」「経過秒数」の3項目に絞る。また、データ量が増えても確認しやすいよう、10件ごとのペジネーション（ページ送り）を実装する。

## 2. 対象コンポーネント
- `/android-app` 配下のファイル
  - `SensorRepository.kt` (状態管理)
  - `SensorTrackingService.kt` (履歴データの生成)
  - `HistoryScreen.kt` (新規作成するUI)
  - `MainActivity.kt` (Navigationのタブ追加)

## 3. 実装要件

### 3-1. データモデルとRepositoryの拡張
- 検知履歴を保持するデータクラス `WalkHistoryItem` を作成する。
  - プロパティ: `detectTimeMs: Long`, `releaseTimeMs: Long`, `durationSeconds: Int`, `stepsDuringWalking: Int`
  - `stepsDuringWalking`: DETECT 発生時点から RELEASE 直前までの歩数差分（検知中に何歩歩いたか）
- `SensorRepository` に、この履歴のリストを保持する `StateFlow<List<WalkHistoryItem>>` を追加する。
- 新しい履歴が常のリストの先頭（インデックス0）に追加されるように更新関数を実装する。

### 3-2. Service側での履歴記録ロジック
- `SensorTrackingService` にて、歩行検知（DETECT）が開始された時刻および累計歩数を一時的に保持する。
  - `detectStartMs: Long?` — DETECT 時刻
  - `detectStartStepCount: Int?` — DETECT 時点の累計歩数
- 歩行検知が解除（RELEASE）されたタイミングで経過秒数と歩数差分を計算し、`WalkHistoryItem` のオブジェクトを生成して `SensorRepository` に追加する処理を実装する。
  - `stepsDuringWalking = (stepCount - detectStartStepCount).coerceAtLeast(0)`

### 3-3. HistoryScreen (履歴画面) の実装とペジネーション
- 新規に `HistoryScreen` コンポーザブルを作成する。
- `SensorRepository` から履歴リスト (`StateFlow`) を購読する。
- **ペジネーション仕様**:
  - 現在のページ数を管理するState (`currentPage`、初期値0) を持たせる。
  - 1ページあたりの表示件数は **10件** とする。
  - リスト全体から `drop(currentPage * 10).take(10)` のように抽出して `LazyColumn` で表示する。
- **UIコンポーネント**:
  - 各行（Card等）に、「検知時刻 (HH:mm:ss)」「解除時刻 (HH:mm:ss)」「経過時間 (〇〇秒)」「検知中歩数 (〇〇歩)」をわかりやすくレイアウトする。
  - 画面下部にページ送りコントローラー（「前の10件へ」「次の10件へ」ボタンと、現在のページ数/総ページ数の表示）を配置する。

### 3-4. Navigation への追加
- 既存の Bottom Navigation に「履歴」タブを追加し、MainScreen, LoggerScreen と共に切り替えられるようにルーティングを更新する。

## 4. 実行手順
1. ブランチ `feature/history-screen-pagination` を作成する。
2. 上記の要件に従い、Repositoryの拡張、Serviceの改修、HistoryScreenの作成を行う。
3. ペジネーションの境界値（データが0件のとき、10件未満のときなど）でクラッシュしないよう考慮する。
4. コードの変更をコミットし、完了を報告する。