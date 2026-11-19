# タスク: 環境依存変数（API_URL等）のハードコード廃止と環境変数ファイルへの切り出し

## 1. 目的と背景
プロジェクト全体（Androidアプリ、React管理画面、テスト用Makefile）にハードコードされている `API_URL` や `DEVICE_ID` などの環境依存変数を、各プラットフォーム標準の環境変数ファイルへ切り出す。
これにより、開発/本番環境の切り替えを容易にし、実URLや認証情報などがGitリポジトリに誤ってコミットされるのを防ぐ。

## 2. 対象コンポーネント
- ルート / `.gitignore`
- `/android-app` (Gradle / Kotlin)
- `/admin-web-viewer` (Vite / React / TypeScript)
- `Makefile` (ルートまたは `/backend` 配下)

## 3. 実装要件

### 3-1. Androidアプリ (`/android-app`)
- **`local.properties` の利用**:
  - `android-app/local.properties` に `API_URL=https://...` および（必要であれば）デフォルトの `DEVICE_ID=...` を記述する方式に変更する。
  - `android-app/local.properties.example` を新規作成し、ダミー値（例: `API_URL=https://your-api-id.execute-api.ap-northeast-3.amazonaws.com/prod`）を記載してGit管理対象とする。
- **`build.gradle.kts` (appモジュール) の改修**:
  - `buildFeatures { buildConfig = true }` を有効化する。
  - `local.properties` から `API_URL` 等を読み込み、`defaultConfig` 内で `buildConfigField("String", "API_URL", "\"$apiUrl\"")` として定数を生成する。
  - `local.properties` にキーが存在しない場合は空文字またはビルド時警告とし、ビルド自体はクラッシュしないよう安全にフォールバックする。
- **Kotlinコードの改修**:
  - APIクライアントやRepository等にハードコードされている URL 文字列をすべて `BuildConfig.API_URL` に置き換える。

### 3-2. 管理画面 (`/admin-web-viewer`)
- **Vite 環境変数の利用**:
  - `admin-web-viewer/.env.local` に `VITE_API_URL=https://...` を記載する方式に変更する。
  - `admin-web-viewer/.env.example` を新規作成し、ダミー値を記載してGit管理対象とする。
- **TypeScript/Reactコードの改修**:
  - ハードコードされている `API_URL` を `import.meta.env.VITE_API_URL` に置き換える。
  - `VITE_API_URL` が未設定の場合に分かりやすいエラーまたはコンソール警告を出すチェックを入れる。
  - 必要に応じて `src/vite-env.d.ts` に `ImportMetaEnv` の型定義を追加する。

### 3-3. テスト用 `Makefile`
- ルート（または `Makefile` と同階層）の `.env` ファイルを読み込めるよう、ファイル先頭付近に `-include .env` を追加する。
- `.env.example` を作成し、`API_URL` などのテンプレートを記載する。
- `Makefile` 内にハードコードされていた実URLがあれば、プレースホルダー（`https://xxxxxxxxxx...`）に戻す。

### 3-4. `.gitignore` とドキュメントの整備
- ルートおよび各サブディレクトリの `.gitignore` を確認し、以下の実ファイルが確実に除外されていることを保証する（`.example` ファイルは除外しないこと）。
  - `.env`
  - `.env.local`
  - `*.env.local`
  - `android-app/local.properties`
- 各コンポーネントの `README.md`（またはルート `README.md`）に、初回セットアップ手順（`.example` ファイルをコピーして実URLを設定する手順）を簡潔に追記する。

## 4. 実行手順
1. ブランチ `chore/extract-env-variables` を作成して切り替える。
2. プロジェクト全体からハードコードされた `execute-api` や `https://` のURL文字列をgrep等で洗い出す。
3. 上記要件に従い、Android / React / Makefile / `.gitignore` を改修し、各 `.example` ファイルを作成する。
4. 既存のローカル環境で動作するよう、現在の実URLをそれぞれの `.env.local` / `local.properties` / `.env` に転記する（これらはGitにステージングしないこと）。
5. `git status` で実URLを含むファイルが追跡対象外であることを確認し、`admin-web-viewer` の `npm run build` が通ることを検証する。
6. 変更をコミットし、洗い出したハードコード箇所と変更内容を報告する。