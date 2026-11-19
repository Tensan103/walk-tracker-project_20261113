
```markdown
# 実開発向け環境（ローカルPC2 ＋ AWS環境B）移行・環境再現ガイド

## 0. 前提ツール（PC2 / WSL2側）
作業開始前に、PC2（WSL2 / Ubuntu）に以下がインストールされていること。
- Git
- Node.js（v22.x 推奨） / npm
- AWS CLI v2
- `make`, `jq`, `curl`
- Android Studio（Windows側）

---

## Part 1: 【手動】リポジトリ取得・AWS環境Bの認証・ブートストラップ

誤って検証用AWS環境Aへデプロイする事故を防ぐため、このパートは必ず手動でアカウントIDを目視確認しながら実施する。

- [ ] **1. リポジトリのクローン**
  ```bash
  git clone <リポジトリURL>
  cd <プロジェクトディレクトリ>

```

* [ ] **2. AWS環境Bの認証設定**
```bash
aws configure
# または SSO の場合: aws sso login --profile <環境Bプロファイル>

```


* [ ] **3. 接続先アカウント・リージョンの目視確認（重要）**
```bash
aws sts get-caller-identity
aws configure get region

```


* 出力された `Account` ID が **AWS環境B** であることを確認する。
* リージョンが想定通りか確認する（環境Aと異なる場合は Part 2 のプロンプトに明記する）。


* [ ] **4. CDK の初回ブートストラップ**
```bash
cd backend
npm install
npx cdk bootstrap
cd ..

```



---

## Part 2: 【Claude Code】セットアップ・デプロイ・疎通確認

Part 1 が完了したら、PC2 のプロジェクトルートで Claude Code を起動し、以下のプロンプトをそのまま渡す。

> **Claude Code へのプロンプト（コピペ用）:**
> 新しい開発環境（AWS環境B）への初回セットアップとデプロイを行います。
> `CLAUDE.md` およびルートの `Makefile`、`scripts/sync-env.sh` を確認したうえで、以下の手順を順番に実行してください。
> ※AWS認証と `cdk bootstrap` は完了済みです。認証情報（アクセスキー等）は出力しないでください。
> 1. **前提確認と依存インストール**
> * `node -v`, `aws --version`, `jq --version` を確認する。
> * `/backend` および `/admin-web-viewer` で `npm install` を実行する。
> * `scripts/sync-env.sh` に実行権限（`chmod +x`）があることを確認する。
> 
> 
> 2. **バックエンド構築と環境変数同期**
> * `make deploy` を実行し、AWS環境Bへの CDK デプロイと各環境ファイル（`.env`, `admin-web-viewer/.env.local`, `android-app/local.properties`）への同期を完了させる。
> * ※もし `STACK_NAME` やリージョン差異でエラーが出た場合は原因を報告して修正案を提示すること。
> 
> 
> 3. **管理画面のビルド＆S3/CloudFront公開**
> * `cd admin-web-viewer && npm test` を実行し、Vitest のユニットテストがすべてパスすることを確認する。
> * `make deploy-web` を実行し、S3 同期と CloudFront キャッシュ無効化を完了させる。
> 
> 
> 4. **疎通確認と初期管理者作成コマンドの提示**
> * `make test-post` を実行し、`x-api-key` 付きで `HTTP Status: 200` が返ることを確認する。
> * 最後に、以下の情報をまとめて報告すること。
> * 公開された管理画面の `WEB_URL`
> * 新しい `USER_POOL_ID` に対して初期管理者ユーザーを作成するための `aws cognito-idp admin-create-user` コマンド例（メールアドレス部分はプレースホルダーにする）
> 
> 
> 
> 
> 
> 

---

## Part 3: 【手動】Cognitoログイン確認・Android Studio 同期

Claude Code の作業が完了したら、最後の手動確認を行う。

* [ ] **1. 初期管理者ユーザーの作成とログイン確認**
* Claude Code が提示した `aws cognito-idp admin-create-user` コマンドを実行し、管理者ユーザーを作成する。
* 出力された `WEB_URL`（CloudFront）をブラウザで開き、ログイン後に「タイムライン」「実績」「デバイス管理」の3画面が表示されることを確認する。


* [ ] **2. Android Studio の初回オープン（`sdk.dir` 生成）**
* Windows 側の Android Studio で `/android-app` を開く。
* `android-app/local.properties` に `sdk.dir=...` が自動生成されたことを確認する。


* [ ] **3. 環境変数の再同期と実機テスト**
* WSL2 側で念のため以下を実行し、`sdk.dir` を維持したまま `API_URL` / `API_KEY` が入っていることを確認する。
```bash
make sync-env

```


* Android Studio で **Sync Project with Gradle Files** を実行する。
* 検証用スマホを接続してビルド・インストールし、歩行検知（`DETECT` / `RELEASE`）が新しい AWS環境B の管理画面に表示されることを確認する。



---

## トラブルシューティング（移行時の注意点）

* **リージョンが環境Aと異なる場合**:
* `Makefile` のデフォルト値や `backend/` 内にリージョン固有の記述（例：`ap-northeast-3`）が残っていないか確認する。


* **`make deploy-web` で古い画面が出る場合**:
* CloudFront のキャッシュ無効化（Invalidation）の反映には 1〜2 分かかることがあるため、少し待ってからスーパーリロード（`Ctrl + Shift + R`）を試す。