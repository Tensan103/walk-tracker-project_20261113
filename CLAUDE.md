# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Walk Tracker: a system that detects walking via smartphone sensors (STEP_DETECTOR / accelerometer), records events to an AWS serverless backend, and displays them in an admin web dashboard.

## Repository Structure

This is a hybrid monorepo with three independent sub-projects:

- **`android-app/`** — Native Android app (Kotlin, Jetpack Compose, OkHttp). Single-activity app (`MainActivity.kt`) with all sensor logic and UI in one file. Package: `com.example.walkdetection_20260818`.
- **`backend/`** — AWS CDK stack (TypeScript). Entry point: `bin/backend.ts` → stack in `lib/backend-stack.ts`. Lambda handlers (plain JS) in `lambda/`. Deploys DynamoDB, three Lambdas (read/write/devices), API Gateway (with Cognito Authorizer + API Key), Cognito User Pool, S3 (OAC), and CloudFront.
- **`admin-web-viewer/`** — React dashboard (Vite, TypeScript, aws-amplify). Single-page app in `src/App.tsx` with Cognito login, 3-tab navigation (タイムライン/実績/デバイス管理), fetches from API Gateway using Cognito JWT.

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
Android App (Foreground Svc)         AWS                              React Dashboard
────────────────────────────         ───                              ───────────────
STEP_DETECTOR sensor
  ↓
Sliding window (5s/3 steps)
  ↓
DETECT/RELEASE event
  ↓
OkHttp POST ──x-api-key──→  API Gateway /events (POST)
                               ↓ API Key 検証
                             WriteEventLambda → WalkEventsTable
                                                (PK: deviceId, SK: timestamp)
                             ReadEventLambda  ← WalkEventsTable + DevicesTable
                               ↑ Cognito JWT 検証
                             API Gateway /events (GET) ←──── authFetch()
                             API Gateway /devices (CRUD) ←──── authFetch()
                                                                   ↓
                                                      Cognito User Pool (login)
                                                      タイムライン/実績/デバイス管理
```

**DynamoDB schema**:
- `WalkEventsTable`: PK `deviceId` (String), SK `timestamp` (Number). Attribute: `category` (`"DETECT"` or `"RELEASE"`).
- `DevicesTable`: PK `deviceId` (String). Attributes: `userName`, `deviceModel`, `status`, `createdAt`.

**Lambda handlers** (`backend/lambda/`): Plain Node.js 22.x using `@aws-sdk/client-dynamodb` v3 Document Client.
- `write.js` — POST /events (API Key protected)
- `read.js` — GET /events (Cognito JWT protected, joins DevicesTable)
- `devicesApi.js` — CRUD /devices (Cognito JWT protected)

**API Gateway**: REST API with CORS open to all origins. Resources: `/events` (POST/GET), `/devices` (GET/POST/PUT), `/devices/{deviceId}` (DELETE). POST /events requires API Key (`x-api-key`); all admin endpoints require Cognito JWT (`Authorization: Bearer`). GatewayResponse DEFAULT_4XX/5XX adds CORS headers on auth errors.

**Authentication split**:
- Android → `x-api-key` header + UsagePlan (rate/burst/monthly quota)
- Admin web → Cognito User Pool (`selfSignUpEnabled: false`), `aws-amplify` v6, JWT sent as `Authorization: Bearer`

**Static hosting**: Private S3 bucket + CloudFront with OAC (`S3BucketOrigin.withOriginAccessControl()`). `REDIRECT_TO_HTTPS`. SPA error responses: 403/404 → `/index.html` 200.

## Key Configuration Points

- **API URL & secrets**: Stored in env files — never hardcoded in source. After CDK redeploy, run `make sync-env` from the project root to auto-update all env files. See **Environment Variables** section below.
- **Walking detection parameters**: `T_SEC = 5` (window), `S_STEPS = 3` (threshold) in `SensorLoggerScreen()`.

## Environment Variables

API_URL などの環境依存変数はソースコードにハードコードせず、以下のファイルで管理する。

### ファイル対応表

| ファイル（実値・git除外） | テンプレート（git管理） | 変数名 | 参照箇所 |
|---|---|---|---|
| `.env`（プロジェクトルート） | `.env.example` | `API_URL`, `API_KEY`, `USER_POOL_ID`, `USER_POOL_CLIENT_ID`, `WEB_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID`, `WEB_URL` | `Makefile` (deploy-web用), `backend/Makefile` テスト用 |
| `admin-web-viewer/.env.local` | `admin-web-viewer/.env.example` | `VITE_API_URL`, `VITE_USER_POOL_ID`, `VITE_USER_POOL_CLIENT_ID` | `import.meta.env.*` |
| `android-app/local.properties` | `android-app/local.properties.example` | `API_URL`, `DEVICE_ID`, `API_KEY` | `BuildConfig.API_URL`, `BuildConfig.DEVICE_ID`, `BuildConfig.API_KEY` |

### Git ルール

- 実値ファイル（`.env`, `.env.local`, `local.properties`）は **絶対にコミットしない**。各 `.gitignore` と root `.gitignore` で除外済み。
- `.example` ファイル（ダミー値入り）はGit管理対象。変数を追加したら対応する `.example` も更新すること。

### 同期ワークフロー

CDK デプロイ後は**プロジェクトルートの `Makefile`** のターゲットを使用する。`scripts/sync-env.sh` が CloudFormation Outputs を python3 で一括取得し、API Key 実値も解決して3ファイルを更新する。

```bash
# プロジェクトルートから実行
make sync-env         # 同期のみ（CloudFormation → .env / .env.local / local.properties）
make deploy           # CDK deploy + sync-env を一括実行
make deploy-web       # sync-env → React build → S3 sync → CloudFront invalidation
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

**Documentation sync**: After every feature addition or change, update these three files to reflect the new state:
- `docs/specification.md` — human-readable spec (功能要件/設計/TODO)
- `docs/architecture.drawio` — system architecture diagram
- `docs/walk-tracker-process.bpmn` — business process flow diagram

## Tech Stack Rules

- **Android**: Kotlin coroutines for async. Jetpack Compose only (no XML layouts). OkHttp for networking.
- **CDK**: AWS CDK v2 (`aws-cdk-lib`). Read the relevant service README from `node_modules/aws-cdk-lib/<module>/README.md` before writing new constructs.
- **React**: Functional components with hooks. TypeScript strict mode.

## Planned Roadmap (from `docs/project_history.md`)

1. **[Done]** Android Foreground Service — sensors and API calls stay alive when screen is off.
2. **[Done]** Amazon Cognito auth — API Gateway secured; Cognito login added to React dashboard.
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

### Work notes (non-source)

- **memo/**

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