以下の内容を `docs/tasks/phase3-1_backend-auth-and-apikey.md` として保存し、Claude Codeに読み込ませてください。

※API GatewayのAPIキー実値はCloudFormationの `CfnOutput` から直接取得できないため、CDKからは `ApiKeyId` を出力し、`scripts/sync-env.sh` 内で `aws apigateway get-api-key --include-value` を呼んで自動取得する構成にしています。

```markdown
# タスク: Cognito認証・APIキー保護のCDK構築と環境変数同期スクリプトの拡張 (Step 3-1)

## 1. 目的と背景
Phase 3 のセキュリティ強化の第1段階として、バックエンドAPIに2系統の認証を導入する。
1. **管理画面用API (`GET /events`, `/devices` 系)**: Amazon Cognito User Pool によるJWT認証で保護する。
2. **Androidアプリ用API (`POST /events`)**: API Gateway の API Key + Usage Plan で保護する。

あわせて、新規作成する Cognito / API Key の設定値を `CfnOutput` に追加し、`scripts/sync-env.sh` で各環境ファイルへ一括同期できるようにする。

## 2. 対象コンポーネント
- `/backend/lib/walk-tracker-backend-stack.ts` (CDKスタック)
- `/scripts/sync-env.sh` (環境変数同期スクリプト)
- `/.env.example`
- `/admin-web-viewer/.env.example`
- `/android-app/local.properties.example`

## 3. 実装要件

### 3-1. Cognito User Pool と Authorizer の構築 (CDK)
`walk-tracker-backend-stack.ts` に以下を追加すること。

- **User Pool (`cognito.UserPool`)**:
  - `selfSignUpEnabled: false`（業務向けのため管理者によるユーザー作成のみ許可）
  - `signInAliases: { email: true }`
  - `passwordPolicy`: 最低8文字、大文字・小文字・数字を含む標準的な設定
  - `removalPolicy: RemovalPolicy.DESTROY`（検証環境のため）
- **User Pool Client (`userPool.addClient`)**:
  - `generateSecret: false`（React SPA からの直接認証のため必須）
  - `authFlows: { userSrp: true, userPassword: true }`
- **Cognito Authorizer (`apigateway.CognitoUserPoolsAuthorizer`)**:
  - 上記の User Pool を紐付けて作成する。

### 3-2. API Gateway のエンドポイント保護と CORS 対応 (CDK)
- **管理画面用エンドポイントへの Cognito 適用**:
  - `GET /events`
  - `GET /devices`, `POST /devices`, `PUT /devices`
  - `DELETE /devices/{deviceId}`
  - 上記すべてのメソッドオプションに `authorizer` および `authorizationType: apigateway.AuthorizationType.COGNITO` を設定する。
- **Android用エンドポイントへの API Key 適用**:
  - `POST /events` に `apiKeyRequired: true` を設定する（Cognito Authorizer は付けない）。
  - `apigateway.ApiKey` および `apigateway.UsagePlan`（例: スロットリング `rateLimit: 10, burstLimit: 20`、クォータ `limit: 10000, period: Period.MONTH` 等）を作成し、APIステージとAPI Keyを紐付ける。
- **CORS 設定の確認・補強**:
  - `defaultCorsPreflightOptions.allowHeaders` に `Authorization` および `x-api-key` が含まれていることを保証する（`Cors.DEFAULT_HEADERS` に加えて明示的に指定）。
  - 認証エラー（401 / 403）時にもブラウザ側でCORSエラーにならないよう、API Gateway の `addGatewayResponse` で `DEFAULT_4XX` / `DEFAULT_5XX` に `Access-Control-Allow-Origin: "'*'"` を付与する。

### 3-3. CfnOutput の追加 (CDK)
既存の `ApiUrl` に加え、以下の3つの `CfnOutput` を追加すること。
- `UserPoolId`: `userPool.userPoolId`
- `UserPoolClientId`: `userPoolClient.userPoolClientId`
- `ApiKeyId`: `apiKey.keyId`

### 3-4. `scripts/sync-env.sh` と `.example` ファイルの更新
- `scripts/sync-env.sh` を拡張し、CloudFormation の Outputs から以下を取得する。
  - `ApiUrl`, `UserPoolId`, `UserPoolClientId`, `ApiKeyId`
- `ApiKeyId` を用いて、以下の AWS CLI コマンドで API Key の**実値**を取得する。
  ```bash
  aws apigateway get-api-key --api-key "${API_KEY_ID}" --include-value --query "value" --output text

```

* 取得した値を `upsert_env` 関数で各ファイルへ反映する。
* `.env`: `API_URL`, `API_KEY`, `USER_POOL_ID`, `USER_POOL_CLIENT_ID`
* `admin-web-viewer/.env.local`: `VITE_API_URL`, `VITE_USER_POOL_ID`, `VITE_USER_POOL_CLIENT_ID`
* `android-app/local.properties`: `API_URL`, `API_KEY`


* 各 `.example` ファイル（`.env.example`, `admin-web-viewer/.env.example`, `android-app/local.properties.example`）にも、追加されたキーのダミー値を追記する。

## 4. 実行手順

1. ブランチ `feature/backend-auth-and-apikey` を作成して切り替える。
2. `backend/lib/walk-tracker-backend-stack.ts` を改修し、`cd backend && npx cdk diff` で意図しないテーブル削除等がないことを確認する。
3. `scripts/sync-env.sh` および各 `.example` ファイルを更新する。
4. `make deploy`（CDKデプロイ＋環境変数同期）を実行し、各 `.env` / `local.properties` に新しい値が正しく書き込まれることを確認する。
5. 以下の疎通確認を `curl` 等で実施する。
* 認証ヘッダーなしで `GET /events` → `401 Unauthorized` になること。
* APIキーなしで `POST /events` → `403 Forbidden` になること。
* `x-api-key` ヘッダー付きで `POST /events` → `200 OK` になること。


6. 変更をコミットし、動作確認結果と「次のStep 3-2でログインテストするためのCognito管理者ユーザー作成用AWS CLIコマンド例」を報告する。