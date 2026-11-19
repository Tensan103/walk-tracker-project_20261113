# タスク: Androidアプリの x-api-key ヘッダー付与と Makefile のAPIキー対応 (Step 3-3)

## 1. 目的と背景
Step 3-1 で `POST /events` に API Gateway の API Key 認証（`apiKeyRequired: true`）を導入したため、現在の Android アプリおよび `Makefile` からの送信は `403 Forbidden` となる。
`scripts/sync-env.sh` によって `android-app/local.properties` とルート `.env` へ同期済みの `API_KEY` を読み込み、リクエストヘッダーに `x-api-key` を付与するように改修する。

## 2. 対象コンポーネント
- `/android-app` 配下のファイル
  - `app/build.gradle.kts` (`BuildConfig.API_KEY` の追加)
  - `POST /events` を送信している Kotlin コード（APIクライアント / Repository 等）
- `/Makefile` (`test-post` 等の API Key 対応)

## 3. 実装要件

### 3-1. Androidアプリ: `BuildConfig.API_KEY` の生成 (`app/build.gradle.kts`)
- 既存の `API_URL` の読み込み処理にならい、`local.properties` から `API_KEY` を読み込む。
- `defaultConfig` 内に `buildConfigField("String", "API_KEY", "\"$apiKey\"")` を追加する。
- `local.properties` に `API_KEY` が未設定の場合でもビルド自体はクラッシュせず、空文字へ安全にフォールバックすること。

### 3-2. Androidアプリ: `x-api-key` ヘッダーの付与
- `POST /events` を送信している HTTP リクエスト処理（`OkHttp` / `HttpURLConnection` 等）を確認し、リクエストヘッダーに以下を追加する。
  - `x-api-key: ${BuildConfig.API_KEY}`
- `BuildConfig.API_KEY` が空文字の場合は、送信前にログへ警告（`Log.w` 等）を出力しておくこと。
- ※APIキーの実際の値をログ（Logcat）に出力しないよう注意すること。

### 3-3. `Makefile` の API Key 対応
- 設定セクションに `API_KEY ?= ` を追加する（`.env` または実行時引数で上書き可能）。
- `check-api-key`（または `check-url` の拡張）を追加し、`test-post` 実行時に `API_KEY` が空であれば `make sync-env` を案内して終了するようにする。
- `test-post` の `curl` コマンドに `-H "x-api-key: $(API_KEY)"` を追加する。
- **Cognito 保護された `GET /events` (`test-get` / `test-e2e`) の整理**:
  - `GET /events` は Cognito Authorizer で保護されているため、`ID_TOKEN` が指定されている場合のみ `-H "Authorization: Bearer $(ID_TOKEN)"` を付与して実行できるようにする（未指定の場合はそのまま実行して `401` 確認に使えるか、ヘルプに注記を記載する）。
- `help` ターゲットの表示を更新し、セキュリティのため `API_KEY` の生値はそのまま出力せず、設定済みかどうか（例: `API_KEY = (set)` / `(not set)`）のみ表示する。

## 4. 実行手順
1. ブランチ `feature/android-and-makefile-apikey` を作成して切り替える。
2. `android-app/app/build.gradle.kts` および Kotlin の API 送信処理を改修する。
3. ルートの `Makefile` を改修する。
4. `make sync-env` で最新の `API_KEY` が `.env` と `android-app/local.properties` に同期されていることを確認し、`make test-post` が `HTTP Status: 200` で成功することを検証する。
5. （可能であれば）`./gradlew assembleDebug` 等で Android 側のビルドエラーがないことを確認する。
6. 変更をコミットし、Android Studio での Gradle Sync〜実機検証手順とともに完了を報告する。