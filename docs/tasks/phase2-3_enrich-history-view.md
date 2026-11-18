# タスク: 歩行履歴一覧へのユーザー名・端末情報の結合表示 (Backend + Frontend)

## 1. 目的と背景
Step 2-1, 2-2 で整備した `DevicesTable` の情報（所有者名・端末モデル）を、既存の「歩行検知履歴画面」に反映させる。
管理者が履歴一覧を見た際に、生の `deviceId` だけでなく「誰の・どの端末の検知履歴か」を一目で判別できるようにする。

## 2. 対象コンポーネント
- `/backend`
  - `lib/walk-tracker-backend-stack.ts` (CDKスタック: IAM権限と環境変数の追加)
  - `lambda/read.js` (履歴取得Lambda: 2テーブルの結合処理)
- `/admin-web-viewer`
  - 履歴一覧を表示しているコンポーネント（`src/App.tsx` または履歴画面用コンポーネント）

## 3. 実装要件

### 3-1. バックエンド: CDKスタックの更新 (`/backend`)
`walk-tracker-backend-stack.ts` にて、既存の `ReadEventLambda`（履歴取得用Lambda）に対して以下を追加すること。
- `DevicesTable` への読み取り権限を付与する（`devicesTable.grantReadData(readLambda)`）。
- 環境変数に `DEVICES_TABLE_NAME: devicesTable.tableName` を追加する。

### 3-2. バックエンド: 履歴取得Lambdaの改修 (`lambda/read.js`)
- `WalkEventsTable` と `DevicesTable` の両方を `ScanCommand` で取得する（`Promise.all` を用いて並列実行すること）。
- 取得した `DevicesTable` のデータを、`deviceId` をキーとしたルックアップ用マップ（オブジェクトまたは `Map`）に変換する。
- `WalkEventsTable` の各レコードに対して、対応するデバイス情報をマージしてレスポンスに含めること。
  - 追加するフィールド:
    - `userName`: 紐づくユーザー名（`DevicesTable` に未登録の `deviceId` の場合は `"未登録"` を設定）
    - `deviceModel`: 紐づく端末モデル（未登録の場合は `"-"` を設定）
- 既存の `timestamp` 降順（最新順）ソートおよび CORS ヘッダー（`Access-Control-Allow-Origin: "*"`）は維持すること。

### 3-3. フロントエンド: 履歴一覧UIの更新 (`/admin-web-viewer`)
- `GET /events` のレスポンス型定義（`WalkEvent` 等）に `userName: string` および `deviceModel: string` を追加する。
- 歩行履歴テーブルの列構成を更新し、以下の項目を表示すること。
  1. **検知日時 (JST)**
  2. **所有者 (userName)** ※`"未登録"` の場合はグレー文字などで視覚的に区別しやすくする
  3. **端末モデル (deviceModel)**
  4. **デバイスID (deviceId)**
  5. **イベント (DETECT / RELEASE)**
- 既存の「最新データに更新」ボタンおよびローディング/エラー表示の挙動は維持すること。

## 4. 実行手順
1. ブランチ `feature/enrich-history-view` を作成して切り替える。
2. `/backend` のCDKスタックと `lambda/read.js` を改修する。
3. `cd backend` で `npx cdk diff` を確認し、問題なければ `npx cdk deploy --require-approval never` でAWS環境を更新する。
4. `/admin-web-viewer` の型定義と履歴テーブルUIを改修し、`npm run build` でTypeScriptのビルドエラーがないことを確認する。
5. 変更をすべてコミットし、完了内容と動作確認手順（未登録デバイスと登録済みデバイスの表示確認）を報告する。