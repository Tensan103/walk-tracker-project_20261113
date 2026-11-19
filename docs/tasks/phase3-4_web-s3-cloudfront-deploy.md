# タスク: 管理画面の S3 + CloudFront ホスティング構築とデプロイ自動化 (Step 3-4)

## 1. 目的と背景
React 管理画面（`/admin-web-viewer`）を AWS 上で HTTPS 公開するため、CDK で非公開 S3 バケット + CloudFront（OAC）を構築する。
あわせて、将来の CI/CD（GitHub Actions / GitLab CI 等）への移植も見据え、ビルドから S3 同期・CloudFront キャッシュ無効化までを 1 コマンドで実行できる `make deploy-web` を整備する。

## 2. 対象コンポーネント
- `/backend/lib/walk-tracker-backend-stack.ts` (S3 + CloudFront 構築と CfnOutput 追加)
- `/scripts/sync-env.sh` (S3バケット名・Distribution ID・Web URL の同期追加)
- `/Makefile` (`deploy-web` ターゲットの追加)
- `/.env.example` (追加環境変数のテンプレート追記)

## 3. 実装要件

### 3-1. S3 + CloudFront の構築 (CDK)
`walk-tracker-backend-stack.ts` に以下を追加すること。

- **S3 バケット (`s3.Bucket`)**:
  - `blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL`（パブリックアクセス完全禁止）
  - `removalPolicy: RemovalPolicy.DESTROY`
  - `autoDeleteObjects: true`（スタック削除時に中身ごとクリーンに削除できるようにする）
- **CloudFront Distribution (`cloudfront.Distribution`)**:
  - オリジンには OAC（Origin Access Control）を使用すること（`origins.S3BucketOrigin.withOriginAccessControl(webBucket)` など、現在の `aws-cdk-lib` バージョンで推奨される OAC 方式を採用）。
  - `defaultRootObject: "index.html"`
  - `viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS`
  - **SPA ルーティング対応 (`errorResponses`)**:
    - HTTP ステータス `403` および `404` に対して、`/index.html` をステータス `200` で返す設定を追加する。
- **CfnOutput の追加**:
  - `WebBucketName`: S3 バケット名
  - `CloudFrontDistributionId`: CloudFront の Distribution ID
  - `WebUrl`: `https://${distribution.distributionDomainName}`

### 3-2. `scripts/sync-env.sh` と `.env.example` の更新
- `scripts/sync-env.sh` で新たに `WebBucketName`, `CloudFrontDistributionId`, `WebUrl` を CloudFormation Outputs から取得する。
- ルートの `.env` に以下のキーを `upsert_env` で書き込む。
  - `WEB_BUCKET_NAME`
  - `CLOUDFRONT_DISTRIBUTION_ID`
  - `WEB_URL`
- `/.env.example` にも上記3キーのプレースホルダーを追記する。

### 3-3. `Makefile` への `deploy-web` ターゲット追加
- `.PHONY` に `deploy-web` を追加し、以下の処理を一括実行するターゲットを実装する。
  1. `make sync-env` を実行し、最新の環境変数（`admin-web-viewer/.env.local` およびルート `.env`）を同期する。
  2. `cd admin-web-viewer && npm run build` で本番用静的ファイルをビルドする（`dist/` 生成）。
  3. `.env` から読み込んだ `WEB_BUCKET_NAME` に対して `aws s3 sync admin-web-viewer/dist s3://<WEB_BUCKET_NAME> --delete` を実行する。
     - ※`make` 開始時点の変数ではなく、`sync-env` 実行後の最新の `.env` 値を参照できるよう、サブシェル内で `. ./.env` で読み込むこと。
  4. `aws cloudfront create-invalidation --distribution-id <CLOUDFRONT_DISTRIBUTION_ID> --paths "/*"` でキャッシュを無効化する。
  5. 完了メッセージとともに `WEB_URL` を表示する。
- `help` ターゲットの表示・実行例にも `deploy-web` を反映する。

## 4. 実行手順
1. ブランチ `feature/web-s3-cloudfront-deploy` を作成して切り替える。
2. `backend/lib/walk-tracker-backend-stack.ts` を改修し、`cd backend && npx cdk diff` で既存テーブルやAPIに影響がないことを確認する。
3. `scripts/sync-env.sh`, `.env.example`, `Makefile` を更新する。
4. `make deploy` でバックエンド（S3 + CloudFront含む）をデプロイし、環境変数が同期されることを確認する。
5. `make deploy-web` を実行し、管理画面のビルド・S3アップロード・CloudFrontキャッシュ無効化が成功することを確認する。
6. 変更をコミットし、出力された `WEB_URL` と動作確認手順（HTTPSアクセス → Cognitoログイン → 各画面の表示確認）を報告する。