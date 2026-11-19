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



## ⚙️ 環境変数ファイルの運用ルール

### ファイル一覧

| ファイル | 用途 | 含まれる変数 | Git |
|---|---|---|---|
| `backend/.env` | Makefile テスト用 | `API_URL`, `DEVICE_ID` | 除外 |
| `admin-web-viewer/.env.local` | React/Vite ビルド用 | `VITE_API_URL` | 除外 |
| `android-app/local.properties` | Android BuildConfig 生成用 | `API_URL`, `DEVICE_ID` | 除外 |

各ファイルに対応する **`.example` ファイル**（ダミー値入り）はGit管理対象です。  
**実URLを含むファイルは絶対にコミットしないこと。**

### 初回セットアップ（CDKデプロイ後）

**方法A — 自動同期スクリプト（推奨）**

```bash
# プロジェクトルートから実行する
# CloudFormation の CfnOutput (ApiUrl) を取得し、3つの環境変数ファイルを一括更新する
./scripts/sync-env.sh

# または make ターゲット経由
make -f backend/Makefile sync-env
```

**方法B — 手動設定**

```bash
# 各 .example をコピーして実URLを書き込む
cp admin-web-viewer/.env.example admin-web-viewer/.env.local
cp android-app/local.properties.example android-app/local.properties
cp backend/.env.example backend/.env
# 3ファイルそれぞれの API_URL を CDK 出力の ApiUrl に書き換える
```

### CDK 再デプロイ後の更新

```bash
# デプロイ → 環境変数同期を一括実行（プロジェクトルートから）
make -f backend/Makefile deploy

# またはデプロイと同期を個別に実行
cd backend && npx cdk deploy --require-approval never
cd .. && ./scripts/sync-env.sh
```

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
npm run build
# CDKで作成されたS3バケットへ同期 (バケット名はご自身の環境に合わせて変更)
aws s3 sync dist/ s3://YOUR_S3_BUCKET_NAME/

```

> **API URL の設定**: `./scripts/sync-env.sh` を実行済みであれば `admin-web-viewer/.env.local` に `VITE_API_URL` が設定されており、手動編集は不要です。

*ブラウザから `CloudFrontUrl` にアクセスして動作を確認します。*

### 3. Androidアプリ

WindowsのAndroid Studioから実行します。

1. Android Studioで `android-app` フォルダを開きます。
2. `android-app/local.properties` に `API_URL` が設定されていることを確認します（`./scripts/sync-env.sh` 実行済みなら自動設定済み）。
3. USBケーブルで実機（Pixel 7a 等）を接続し、Run (Shift + F10) を実行します。

## ⚠️ 現在の制約事項

* Androidアプリは現状フォアグラウンド（画面点灯時）のみで動作します。画面消灯時（スリープ）にはOSの制限によりセンサーイベントが一時停止します。