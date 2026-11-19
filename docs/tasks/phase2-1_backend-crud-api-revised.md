# タスク: デバイス(兼ユーザー)管理テーブルの追加とCRUD APIの実装

## 1. 目的と背景
Phase 2の管理基盤構築に向けて、現在の「歩行検知の生ログ」に「誰の、どの端末か」という意味づけを持たせる。
運用をシンプルにするため、ユーザー管理とデバイス管理を1つのテーブルに統合した `DevicesTable` をAWS CDKで新規作成する。
また、管理画面（React）からこのデータを登録・編集・削除（CRUD）するための Lambda 関数と API Gateway のエンドポイントを構築する。

## 2. 対象コンポーネント
- `/backend` 配下のファイル
  - `lib/walk-tracker-backend-stack.ts` (CDKスタック)
  - `lambda/devicesApi.js` (新規作成)

## 3. 実装要件

### 3-1. DynamoDB テーブルの追加 (CDK)
`walk-tracker-backend-stack.ts` に以下のテーブルを追加すること。テスト環境のため `RemovalPolicy.DESTROY` を設定すること。

- **DevicesTable**
  - Partition Key: `deviceId` (String) - 例: "EMP-001" や UUID
  - 用途: 端末情報と所有者（ユーザー）情報の統合管理
  - 想定される属性（スキーマレスだが運用上想定するもの）:
    - `userName` (String): 所有者の名前
    - `deviceModel` (String): 端末の機種名等
    - `status` (String): "ACTIVE" などの運用ステータス
    - `createdAt` (Number): 登録日時

### 3-2. Lambda 関数の作成 (CRUD処理)
新たにLambda関数 `devicesApi.js`（Node.js 18.x, `@aws-sdk/lib-dynamodb`使用）を作成する。
1つの関数内で、API Gatewayから渡される `event.httpMethod` を判定し、以下のルーティング処理を実行すること。

- `GET`: 全デバイス一覧の取得 (Scan)
- `POST`: 新規デバイスの登録 (`event.body` に含まれる `deviceId`, `userName`, `deviceModel` 等を保存。`createdAt` は現在時刻を付与)
- `PUT`: デバイス情報（`userName` 等）の更新 (`event.body` に含まれる `deviceId` をキーにする)
- `DELETE`: デバイスの削除 (パスパラメータから渡される `deviceId` をキーにして削除)

### 3-3. API Gateway のエンドポイント追加 (CDK)
既存の REST API に対して、以下のリソースとメソッドを追加すること。
Reactからの呼び出しに備え、CORS設定（`defaultCorsPreflightOptions`）を適切に行うこと。

- `/devices`
  - メソッド: `GET`, `POST`, `PUT`
  - 統合先: `devicesApi.js` Lambda
- `/devices/{deviceId}`
  - メソッド: `DELETE`
  - 統合先: `devicesApi.js` Lambda

## 4. 実行手順
1. ブランチ `feature/backend-devices-api` を作成して切り替える。
2. 上記の要件に従い、CDKスタックの修正とLambda関数の新規作成を行う。
3. `cd backend` にて `npx cdk diff` 等を実行し、構文エラーがないか確認する。
4. 問題がなければ `npx cdk deploy --require-approval never` を実行してAWS環境を更新する。
5. デプロイ成功後、コードをコミットし、APIの動作確認方法（curlやPostmanの例）とともに完了を報告する。