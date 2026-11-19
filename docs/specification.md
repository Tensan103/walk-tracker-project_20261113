# 歩きスマホ検知システム（Walk Tracker）仕様書

## 1. 概要・目的
本システムは、Android端末のセンサーを用いて「歩行中のスマートフォン操作（歩きスマホ）」を自動検知・警告し、その発生履歴をクラウド上で一元管理・可視化するためのシステムである。
スモールスタートかつ低コスト・高保守性を実現するため、AWSサーバーレスアーキテクチャおよびモノレポ構成を採用している。

---

## 2. システム構成要素
本システムは以下の3つのコンポーネントで構成される。

| コンポーネント | 技術スタック | 役割 |
| :--- | :--- | :--- |
| **Androidアプリ** (`/android-app`) | Kotlin, Jetpack Compose, Foreground Service | 画面ON時の歩行検知、警告表示、検知/解除イベントのAWS送信 |
| **バックエンド** (`/backend`) | AWS CDK (TypeScript), API Gateway, Lambda (Node.js 22.x), DynamoDB, Cognito, S3, CloudFront | API提供、認証・認可、データ永続化、管理画面ホスティング |
| **Web管理画面** (`/admin-web-viewer`) | React, TypeScript, Vite, aws-amplify, Vitest | 管理者ログイン、検知タイムライン・実績の可視化、デバイス管理 |

---

## 3. 機能要件

### 3-1. Androidアプリ
- **常駐・バックグラウンド監視**:
  - Foreground Service（`START_STICKY`）により、アプリのスワイプアウト後も監視を継続する。
  - 画面ON（`ACTION_SCREEN_ON`）で監視を開始し、画面OFF（`ACTION_SCREEN_OFF`）で監視を停止する。
- **歩きスマホ検知・警告**:
  - センサー（加速度・歩数検知）により設定された時間ウィンドウ・歩数しきい値を超過した場合に「検知中（`DETECT`）」へ遷移する。
  - 警告表示は設定により4種類（オーバーレイ、トースト等）から選択可能。
- **解除処理と画面OFF時の確実な送信**:
  - 歩行停止時、または検知中の画面OFF（`onDestroy`）時に「解除（`RELEASE`）」へ遷移し、長寿命CoroutineScopeを用いてAWSへ確実にPOST送信する。
- **設定の永続化と検証用スイッチ**:
  - 検知パラメータ、警告タイプ、監視ON/OFF状態は `SharedPreferences` に永続化する。
  - 検証端末での利便性のため、設定画面に監視機能のON/OFF（稼働中 ⇔ 待機中）切り替えスイッチを備える。

### 3-2. Web管理画面
全3画面構成とし、ペアリング・フィルタ・ソート・実績集計・20件ページネーションはすべてフロントエンド側で処理する。日付境界はすべて日本時間（JST: `00:00:00.000` 〜 `23:59:59.999`）基準とする。

1. **画面1「タイムライン」**
   - 同一 `deviceId` ごとに時系列昇順で `DETECT` と直後の `RELEASE` をペアリングして1行表示する。
   - 経過時間は「45秒」「2分15秒」のような時分秒形式で表示する。
   - 異常系1（対になる `RELEASE` がない `DETECT`）は解除日時・時間を `-` 表示、異常系2（単独 `RELEASE`）は非表示とする。
   - 検知日時（日付範囲指定）、デバイス名・ユーザー名（部分一致）による検索フィルタ、および各列ソート（`-` は常に末尾）を提供する。
2. **画面2「実績」**
   - デバイスごとの `DETECT` 件数を集計・一覧表示する（デフォルト期間：JST当月初日〜末日）。
   - `DevicesTable` 登録済みで検知0件のデバイス、および未登録 `deviceId` の検知もすべて表示対象とする。
   - 検知件数による昇順/降順ソート（デフォルト降順）を提供する。
3. **画面3「デバイス管理」**
   - `DevicesTable` に対する新規登録・一覧表示・編集・削除（CRUD）を行う。

---

## 4. 基本設計

### 4-1. データモデル（DynamoDB）
運用をシンプルにするため、ユーザー情報と端末情報は単一テーブル（`DevicesTable`）で統合管理する。

| テーブル名 | パーティションキー | ソートキー | 主な属性 |
| :--- | :--- | :--- | :--- |
| **WalkEventsTable** | `deviceId` (String) | `timestamp` (Number / Unix ms) | `category` (`DETECT` / `RELEASE`) |
| **DevicesTable** | `deviceId` (String) | なし | `userName`, `deviceModel`, `status`, `createdAt` |

### 4-2. API設計（API Gateway + Lambda Node.js 22.x）
管理画面用APIとAndroid用APIで認証方式を明確に分離する。

| メソッド & パス | 担当Lambda | 認証方式 | 概要 |
| :--- | :--- | :--- | :--- |
| `POST /events` | `WriteEventLambda` | **API Key (`x-api-key`)** | Android端末からの検知・解除イベント登録 |
| `GET /events` | `ReadEventLambda` | **Cognito Authorizer (JWT)** | イベント一覧取得（`DevicesTable` と結合して返却） |
| `GET/POST/PUT /devices` | `DevicesApiLambda` | **Cognito Authorizer (JWT)** | デバイス（所有者）一覧取得・登録・更新 |
| `DELETE /devices/{deviceId}` | `DevicesApiLambda` | **Cognito Authorizer (JWT)** | デバイス削除 |

### 4-3. ホスティング・運用自動化設計
- **ホスティング**: 非公開 S3 バケット + CloudFront（OAC）による HTTPS 配信。SPAルーティング対応（403/404 → `/index.html` 200返却）。
- **環境変数管理**: 実URLやAPI KeyはGit管理外（`.env`, `.env.local`, `local.properties`）とし、CloudFormation Outputs から `scripts/sync-env.sh` 経由で一括同期する。
- **運用コマンド**: ルートの `Makefile` により、`make deploy`（バックエンド反映＋環境同期）、`make deploy-web`（Reactビルド＋S3同期＋CloudFrontキャッシュ削除）、`make test-post`（疎通確認）を自動化している。
- **品質担保**: 管理画面のコアロジック（ペアリング・JST境界・集計）に対して Vitest によるユニットテストを導入済み。

---

## 5. TODO（本番運用に向けた未実施項目）
現在は検証フェーズのため、以下の項目は意図的に未実施としている。本番移行前に順次対応すること。

### データ保護・コスト管理
- [ ] **RemovalPolicy の変更**: DynamoDB テーブルおよび Cognito User Pool を `DESTROY` から `RETAIN` へ変更する。
- [ ] **PITR（ポイントインタイムリカバリ）有効化**: DynamoDB の誤削除・破損に備え35日間の復元機能を有効化する。
- [ ] **過去ログの自動削除（TTL）**: `WalkEventsTable` の肥大化と Scan コスト増を防ぐため、保持期限（例：1年）による TTL を導入する。
- [ ] **CloudWatch Logs の保持期間設定**: 各 Lambda のロググループを無期限保存から 1〜3ヶ月保持へ変更する。
- [ ] **AWS Budgets / 障害アラート**: 月額予算アラートおよび Lambda エラー検知（CloudWatch Alarm + SNS）を設定する。

### セキュリティ強化
- [ ] **CORS オリジンの限定**: 検証用ローカル画面の利用終了後、`Access-Control-Allow-Origin: "*"` を CloudFront の `WEB_URL` のみに限定する。
- [ ] **管理者アカウントの MFA 有効化**: Cognito User Pool にて TOTP による多要素認証を必須化する。
- [ ] **Android端末認証の本格化**: 全端末共通の `x-api-key` から、端末単位で失効可能な認証方式（Cognito / 端末固有トークン等）への移行を検討する。

### 運用・配布自動化
- [ ] **CI/CD パイプライン構築**: 本番リポジトリ（GitHub / GitLab）確定後、`Makefile` のデプロイ処理をパイプラインへ移植する。
- [ ] **Androidアプリの配布・自動アップデート**: 社内配布規模に応じて、S3 + `version.json` による自前アップデート機構または MDM の導入を検討する。