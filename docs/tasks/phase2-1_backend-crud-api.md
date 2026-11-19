# タスク: ユーザー/デバイステーブルの追加とCRUD APIの実装

## 1. 目的と背景
Phase 2の管理基盤構築に向けて、現在の「歩行検知の生ログ」に「誰の、どの端末か」という意味づけを持たせる。
AWS CDKを用いて `Users` と `Devices` の2つのDynamoDBテーブルを新規作成し、管理画面（React）からこれらのデータを登録・編集・削除（CRUD）するための Lambda 関数と API Gateway のエンドポイントを構築する。

## 2. 対象コンポーネント
- `/backend` 配下のファイル
  - `lib/walk-tracker-backend-stack.ts` (CDKスタック)
  - `lambda/usersApi.js` (新規作成)
  - `lambda/devicesApi.js` (新規作成)

## 3. 実装要件

### 3-1. DynamoDB テーブルの追加 (CDK)
`walk-tracker-backend-stack.ts` に以下の2つのテーブルを追加すること。テスト環境のため `RemovalPolicy.DESTROY` を設定すること。

- **UsersTable**
  - Partition Key: `userId` (String)
  - 用途: ユーザー情報の管理 (`name`, `createdAt` など)
- **DevicesTable**
  - Partition Key: `deviceId` (String)
  - 用途: 端末とユーザーの紐付け (`userId`, `deviceName`, `createdAt` など)

### 3-2. Lambda 関数の作成 (CRUD処理)
新たに2つのLambda関数（Node.js 18.x, `@aws-sdk/lib-dynamodb`使用）を作成する。
1つの関数内で、API Gatewayから渡される `event.httpMethod` (GET, POST, PUT, DELETE) を判定し、それぞれの処理を実行するルーティング形式とすること。

- **`usersApi.js`**: `UsersTable` に対するCRUD処理。
  - `GET`: 全ユーザー一覧の取得 (Scan)
  - `POST`: 新規ユーザーの登録 (UUIDを生成して `userId` とする)
  - `PUT`: ユーザー情報の更新 (`event.body` に含まれる `userId` をキーにする)
  - `DELETE`: ユーザーの削除 (パスパラメータ等から渡される `userId` を使用)
- **`devicesApi.js`**: `DevicesTable` に対するCRUD処理。
  - `GET`: 全デバイス一覧の取得 (Scan)
  - `POST`: 新規デバイスの登録 (管理者が任意の `deviceId` と紐付ける `userId` を指定)
  - `PUT`: デバイス情報の更新
  - `DELETE`: デバイスの削除

### 3-3. API Gateway のエンドポイント追加 (CDK)
既存の REST API に対して、以下のリソースとメソッドを追加すること。
また、Reactからの呼び出しに備えてCORS設定（`defaultCorsPreflightOptions`）を適切に行うこと。

- `/users`
  - メソッド: `GET`, `POST`, `PUT`
- `/users/{userId}`
  - メソッド: `DELETE`
  - 統合先: `usersApi.js` Lambda
- `/devices`
  - メソッド: `GET`, `POST`, `PUT`
- `/devices/{deviceId}`
  - メソッド: `DELETE`
  - 統合先: `devicesApi.js` Lambda

※既存の `readLambda` や `writeLambda` に、追加したテーブルの読み取り権限（ReadData）を今の段階で付与しておく必要はない（後続のタスクで行う）。

## 4. 実行手順
1. ブランチ `feature/backend-crud-api` を作成して切り替える。
2. 上記の要件に従い、CDKスタックの修正とLambda関数の新規作成を行う。
3. `cd backend` にて `npx cdk diff` などを実行し、構文エラーがないか確認する。
4. 問題がなければ `npx cdk deploy --require-approval never` を実行してAWS環境を更新する。
5. デプロイ成功後、コードをコミットし、動作確認用のエンドポイント情報とともに完了を報告する。