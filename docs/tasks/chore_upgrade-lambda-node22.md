# タスク: Backend Lambda ランタイムの Node.js 22.x への統一・更新

## 1. 目的と背景
AWSによるサポートが終了している Node.js 18.x を使用している Lambda 関数について、長期サポート対象の **Node.js 22.x (`nodejs22.x`)** へ更新し、バックエンド全体のランタイムバージョンを統一する。

## 2. 対象コンポーネント
- `/backend` 配下のファイル
  - `lib/` 配下の CDK スタック定義（`Runtime.NODEJS_18_X` 等の指定箇所すべて）
  - `package.json`（必要に応じて `aws-cdk-lib` のバージョン確認）
  - `lambda/` 配下の関数コード（互換性チェック）

## 3. 実装要件

### 3-1. Lambda ランタイムの更新 (CDK)
- `/backend` 配下で定義されているすべての Lambda 関数（`WriteEventLambda`, `ReadEventLambda`, `DevicesApiLambda` 等）のランタイム指定を確認する。
- `lambda.Runtime.NODEJS_18_X`（および `NODEJS_20_X` があればそれも）をすべて **`lambda.Runtime.NODEJS_22_X`** に変更する。
- もし現在の `aws-cdk-lib` のバージョンが古く `Runtime.NODEJS_22_X` が存在しない場合は、`aws-cdk-lib` を最新安定版へアップデートするか、コンパイルエラーが出ない形で対応すること。

### 3-2. Lambda 関数コードの互換性確認
- `backend/lambda/` 配下の各ファイルについて、Node.js 22.x で非推奨・廃止されたAPIや、AWS SDK v2 (`aws-sdk`) への依存が残っていないか確認する（すべて `@aws-sdk/*` の v3 であること）。
- 問題があれば修正する。

### 3-3. デプロイと疎通確認
- `cd backend` で `npx cdk diff` を実行し、意図しないリソース削除（テーブル再作成等）が発生せず、Lambda の `Runtime` 変更のみであることを確認する。
- ルートの `Makefile`（`make deploy` または `npx cdk deploy --require-approval never`）で AWS 環境を更新する。
- デプロイ後、`make test-e2e`（および `/devices` の GET 確認等）を実行し、すべての Lambda 関数がエラーなく応答することを確認する。

## 4. 実行手順
1. ブランチ `chore/upgrade-lambda-node22` を作成して切り替える。
2. CDK スタック内のランタイム定義を洗い出し、`Runtime.NODEJS_22_X` へ統一する。
3. `npx cdk diff` で差分を確認し、安全にデプロイを実行する。
4. `make test-e2e` 等で疎通確認を行い、CloudWatch Logs にランタイム起因のエラーが出ていないことを確認する。
5. 変更をコミットし、更新した関数一覧と動作確認結果を報告する。