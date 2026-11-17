# Walk Tracker System

スマートフォンの歩行検知センサー（STEP_DETECTOR）を利用して歩行状態を検知し、AWSサーバーレス環境に記録・可視化するフルスタックシステムです。

## 📂 プロジェクト構成 (モノレポ)

```text
walk-tracker-project_20261123/
├── android-app/         # Androidアプリ (Kotlin / Jetpack Compose)
├── backend/             # AWSバックエンド (AWS CDK / TypeScript)
└── admin-web-viewer/    # 管理用Webビューワー (React / TypeScript / Vite)

```

## 🛠 開発環境の前提条件

本プロジェクトは **Windows (Android Studio) + WSL2 (AWS/Node.js/Claude Code)** のハイブリッド環境での開発を前提としています。

* **Android開発**: Windowsネイティブ上の Android Studio を使用（USBデバッグ用）。
* **バックエンド/フロントエンド開発**: WSL2 (Ubuntu) を使用。
* Node.js (v18.x以上推奨)
* AWS CLI (認証情報設定済み)
* AWS CDK CLI (`npm install -g aws-cdk`)



## 🚀 デプロイと実行手順

### 1. バックエンド (AWS CDK)

WSL2のターミナルから実行します。DynamoDB, Lambda, API Gateway, S3, CloudFrontを一括構築します。

```bash
cd backend
npm install
npx cdk bootstrap # 初回のみ
npx cdk deploy

```

*デプロイ完了後、ターミナルに出力される `ApiUrl` と `CloudFrontUrl` をメモしてください。*

### 2. 管理用Webビューワー (React)

WSL2のターミナルから実行します。ビルドした静的ファイルをS3にアップロードし、CloudFront経由で配信します。

```bash
cd admin-web-viewer
npm install
# src/App.tsx 内の API_URL を CDKのApiUrl に書き換える
npm run build
# CDKで作成されたS3バケットへ同期 (バケット名はご自身の環境に合わせて変更)
aws s3 sync dist/ s3://YOUR_S3_BUCKET_NAME/

```

*ブラウザから `CloudFrontUrl` にアクセスして動作を確認します。*

### 3. Androidアプリ

WindowsのAndroid Studioから実行します。

1. Android Studioで `android-app` フォルダを開きます。
2. `MainActivity.kt` 内の `awsApiUrl` を、CDKで生成された `ApiUrl` + `/events` に書き換えます。
3. USBケーブルで実機（Pixel 7a 等）を接続し、Run (Shift + F10) を実行します。

## ⚠️ 現在の制約事項

* Androidアプリは現状フォアグラウンド（画面点灯時）のみで動作します。画面消灯時（スリープ）にはOSの制限によりセンサーイベントが一時停止します。