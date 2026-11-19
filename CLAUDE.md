# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Walk Tracker: a system that detects walking via smartphone sensors (STEP_DETECTOR / accelerometer), records events to an AWS serverless backend, and displays them in an admin web dashboard.

## Repository Structure

This is a hybrid monorepo with three independent sub-projects:

- **`android-app/`** — Native Android app (Kotlin, Jetpack Compose, OkHttp). Single-activity app (`MainActivity.kt`) with all sensor logic and UI in one file. Package: `com.example.walkdetection_20260818`.
- **`backend/`** — AWS CDK stack (TypeScript). Entry point: `bin/backend.ts` → stack in `lib/backend-stack.ts`. Lambda handlers (plain JS) in `lambda/`. Deploys DynamoDB, two Lambdas (read/write), API Gateway, S3, and CloudFront.
- **`admin-web-viewer/`** — React dashboard (Vite, TypeScript). Single-page app in `src/App.tsx` that fetches walk events from the API Gateway `/events` endpoint.

## Environment Constraints

Development runs on **Windows + WSL2 (Ubuntu)**:

- **CRITICAL**: Do NOT run Gradle builds or Android tests from WSL2 — the cross-filesystem boundary makes it extremely slow. Android tasks are handled manually in Android Studio on Windows.
- Node.js, AWS CLI, and CDK commands run freely in WSL2.

## Build & Dev Commands

### Backend (`cd backend`)
```bash
npm install
npm run build          # TypeScript type-check (tsc)
npm run test           # Jest unit tests
npx cdk synth          # Synthesize CloudFormation template
npx cdk diff           # Compare deployed stack with current state
npx cdk deploy         # Deploy to AWS — ask user before running
npx cdk bootstrap      # First-time only, one per account/region
```

### Admin Web Viewer (`cd admin-web-viewer`)
```bash
npm install
npm run dev            # Vite dev server with HMR
npm run build          # tsc + vite build
npm run lint           # ESLint
npm run preview        # Preview production build locally
```

### Android (`android-app/`)
Build and run exclusively from Android Studio on Windows. No CLI commands from WSL2.

## Architecture & Data Flow

```
Android App                    AWS                              React Dashboard
───────────                    ───                              ───────────────
STEP_DETECTOR sensor
  ↓
Sliding window (5s/3 steps)
  ↓
DETECT/RELEASE event
  ↓
OkHttp POST ──────→  API Gateway /events (POST)
                        ↓
                      WriteLambda → DynamoDB
                                    (PK: deviceId, SK: timestamp)
                      ReadLambda  ← DynamoDB (Scan, sorted desc)
                        ↑
                      API Gateway /events (GET) ←──── fetch()
                                                        ↓
                                                      Event table (JST)
```

**DynamoDB schema**: Partition key `deviceId` (String), sort key `timestamp` (Number). Each item has a `category` field (`"DETECT"` or `"RELEASE"`).

**Lambda handlers** (`backend/lambda/`): Plain Node.js 18.x using `@aws-sdk/client-dynamodb` v3. Both `read.js` and `write.js` use DynamoDB Document Client. Read currently uses Scan (full-table) — intentionally simple for the prototype phase.

**API Gateway**: REST API with CORS open to all origins. Single resource `/events` with POST (write) and GET (read).

**Static hosting**: S3 bucket + CloudFront distribution for the React app. Bucket has `DESTROY` removal policy (test environment).

## Key Configuration Points

- **API URL & Device ID**: Stored in env files — never hardcoded in source. After CDK redeploy, run `./scripts/sync-env.sh` from the project root to auto-update all three env files. See **Environment Variables** section below.
- **Walking detection parameters**: `T_SEC = 5` (window), `S_STEPS = 3` (threshold) in `SensorLoggerScreen()`.

## Environment Variables

API_URL などの環境依存変数はソースコードにハードコードせず、以下のファイルで管理する。

### ファイル対応表

| ファイル（実値・git除外） | テンプレート（git管理） | 変数名 | 参照箇所 |
|---|---|---|---|
| `backend/.env` | `backend/.env.example` | `API_URL`, `DEVICE_ID` | `backend/Makefile` テスト用 |
| `admin-web-viewer/.env.local` | `admin-web-viewer/.env.example` | `VITE_API_URL` | `import.meta.env.VITE_API_URL` |
| `android-app/local.properties` | `android-app/local.properties.example` | `API_URL`, `DEVICE_ID` | `BuildConfig.API_URL`, `BuildConfig.DEVICE_ID` |

### Git ルール

- 実値ファイル（`.env`, `.env.local`, `local.properties`）は **絶対にコミットしない**。各 `.gitignore` と root `.gitignore` で除外済み。
- `.example` ファイル（ダミー値入り）はGit管理対象。変数を追加したら対応する `.example` も更新すること。

### 同期ワークフロー

CDK デプロイ後は `./scripts/sync-env.sh` を**プロジェクトルートから**実行すると、CloudFormation の `ApiUrl` 出力を取得して3ファイルを一括更新する。

```bash
# 方法A: スクリプトを直接実行（プロジェクトルートから）
./scripts/sync-env.sh [STACK_NAME] [OUTPUT_KEY]
# デフォルト: BackendStack, ApiUrl

# 方法B: make ターゲット経由（プロジェクトルートから）
make -f backend/Makefile sync-env   # 同期のみ
make -f backend/Makefile deploy     # CDK deploy + 同期を一括実行
```

> **新しい環境変数を追加するときの手順**:
> 1. `scripts/sync-env.sh` の `upsert_env` 呼び出しに追加
> 2. 対応する `.example` ファイルにキーとダミー値を追記
> 3. Android の場合は `app/build.gradle.kts` の `buildConfigField` にも追加

## Development Workflow

When implementing a feature that spans the stack:
1. Modify the CDK backend first and deploy.
2. Update the React frontend to match API changes.
3. Update the Android Kotlin code last (user builds it in Android Studio).

## Tech Stack Rules

- **Android**: Kotlin coroutines for async. Jetpack Compose only (no XML layouts). OkHttp for networking.
- **CDK**: AWS CDK v2 (`aws-cdk-lib`). Read the relevant service README from `node_modules/aws-cdk-lib/<module>/README.md` before writing new constructs.
- **React**: Functional components with hooks. TypeScript strict mode.

## Planned Roadmap (from `docs/project_history.md`)

1. **[High]** Android Foreground Service — keep sensors and API calls alive when screen is off.
2. **[Medium]** Amazon Cognito auth — secure API Gateway, add login to the React dashboard.
3. **[Low]** DynamoDB schema optimization — GSI for time-range queries, device-to-user mapping.

## Files and Directories to Ignore

Unless explicitly requested by the user, do not inspect, search, index, or analyze the following files and directories:

### Node.js / TypeScript artifacts

- **/node_modules/**
- **/dist/**
- **/coverage/**
- **/.vite/**
- **/.npm/**

### Android build artifacts

- **android-app/.gradle/**
- **android-app/build/**
- **android-app/app/build/**
- **android-app/.cxx/**
- **android-app/captures/**

### AWS CDK artifacts

- **backend/cdk.out/**
- **backend/.cdk.staging/**

### Infrastructure artifacts

- **/.terraform/**
- **/*.tfstate**
- **/*.tfstate.backup**

### Generated files

- **/*.apk**
- **/*.aab**
- **/*.jar**
- **/*.class**
- **/*.so**
- **/*.dex**

### Logs

- **/*.log**
- **npm-debug.log***
- **yarn-error.log***

### Cache directories

- **/.cache/**
- **/.tmp/**
- **/tmp/**

### Lock files

Read package lock files only when dependency investigation is required:

- package-lock.json
- yarn.lock
- pnpm-lock.yaml

### Exceptions

The following locations are considered source code and should always be preferred:

- android-app/app/src/**
- backend/bin/**
- backend/lib/**
- backend/lambda/**
- admin-web-viewer/src/**
- docs/**

Ignore generated outputs even if they contain copies of source code.

## Search Strategy

When investigating an issue:

1. Start from the smallest relevant sub-project.
2. Avoid repository-wide searches unless the issue spans multiple components.
3. Prefer targeted file reads over recursive directory scans.
4. Read only files directly related to the task.

## Secrets and Credentials

Never read, print, modify, commit, or expose credentials from:

- .env
- .env.*
- *.pem
- *.key
- ~/.aws/credentials
- ~/.aws/config

Use placeholders when discussing secrets.