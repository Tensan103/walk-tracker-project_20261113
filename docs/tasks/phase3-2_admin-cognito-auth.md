# タスク: 管理画面 (React) への Cognito ログイン認証と Authorization ヘッダー付与 (Step 3-2)

## 1. 目的と背景
Step 3-1 でバックエンドの管理用API（`GET /events`, `/devices` 系）を Amazon Cognito Authorizer で保護したため、現在の管理画面からは `401 Unauthorized` となる。
`/admin-web-viewer` に `aws-amplify` と `@aws-amplify/ui-react` を導入し、ログイン画面の実装および全APIリクエストへの JWT (`idToken`) 付与を行う。

## 2. 対象コンポーネント
- `/admin-web-viewer` 配下のファイル
  - `package.json` (`aws-amplify`, `@aws-amplify/ui-react` の追加)
  - `src/vite-env.d.ts` (環境変数の型定義追加)
  - `src/main.tsx` または `src/aws-config.ts` (Amplify 初期化設定)
  - `src/utils/apiClient.ts` (新規作成: 認証ヘッダー付き fetch ラッパー)
  - `src/App.tsx` (`<Authenticator>` によるラップとサインアウトボタン追加)
  - 各画面コンポーネント（既存の `fetch` 呼び出しを認証付きクライアントへ置き換え）

## 3. 実装要件

### 3-1. パッケージ導入と Amplify 初期化
- `/admin-web-viewer` に `aws-amplify` および `@aws-amplify/ui-react` をインストールする。
- `src/vite-env.d.ts` に `VITE_USER_POOL_ID` と `VITE_USER_POOL_CLIENT_ID` の型定義を追加する。
- `Amplify.configure(...)` で Cognito User Pool の設定を行う（Amplify v6 形式：`Auth.Cognito.userPoolId`, `Auth.Cognito.userPoolClientId` を `import.meta.env` から読み込む）。
- 環境変数が未設定の場合は分かりやすいエラーメッセージまたは警告を表示すること。

### 3-2. ログイン画面とサインアウト UI (`src/App.tsx`)
- `@aws-amplify/ui-react/styles.css` をインポートし、`<Authenticator hideSignUp>` で管理画面全体をラップする。
  - ※Cognito 側で `selfSignUpEnabled: false` のため、必ず `hideSignUp` を指定して新規登録タブを非表示にすること。
- ログイン後のヘッダー（ナビゲーションバー付近）に、ログイン中のユーザー情報（メールアドレス等）と **「ログアウト」ボタン**（`signOut` 呼び出し）を配置する。

### 3-3. 認証ヘッダー付き API クライアント (`src/utils/apiClient.ts`)
- `aws-amplify/auth` の `fetchAuthSession()` を用いて現在のセッションから `idToken` を取得する共通関数（例: `authFetch(path, options)`）を作成する。
- リクエストヘッダーに `Authorization: Bearer ${idToken}` を自動付与して API Gateway を呼び出す。
  - ※API Gateway の Cognito Authorizer は `idToken` を検証するため、`accessToken` ではなく `idToken` を使用すること。
- 既存のタイムライン画面・実績画面・デバイス管理画面のすべての `fetch` 呼び出し（`GET /events`, `GET/POST/PUT/DELETE /devices`）を、この共通クライアント経由に統一する。

## 4. 実行手順
1. ブランチ `feature/admin-cognito-auth` を作成して切り替える。
2. `aws-amplify` と `@aws-amplify/ui-react` をインストールする。
3. Amplify 初期化設定、`<Authenticator hideSignUp>` ラップ、ログアウトボタン、および `authFetch` を実装する。
4. `cd admin-web-viewer` にて `npm test`（Vitest）と `npm run build` を実行し、テストのパスと TypeScript ビルドエラーがないことを確認する。
5. 変更をコミットし、ローカル（`npm run dev`）でのログイン〜各画面の動作確認手順とともに完了を報告する。