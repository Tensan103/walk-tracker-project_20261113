# Project History and Roadmap

## 1. 概要と目的
本プロジェクトは、スマートフォンの歩行検知センサー（STEP_DETECTOR）と加速度センサーを利用し、歩行行動を記録・クラウドへ送信・可視化するためのシステムである。最終的にはデバイスをポケットに入れた状態でのバックグラウンド行動認識・ログ収集を目的とする。

## 2. これまでの実装経緯と現状の仕様 (2026-11-23時点)

### フェーズ1: Androidローカル検証の完了
- **実装内容**: `android-app` にて、Jetpack Composeを用いたUIを構築。
- **センサー仕様**: `Sensor.TYPE_ACCELEROMETER` と `Sensor.TYPE_STEP_DETECTOR` を併用。
- **歩行判定ロジック**: スライディングウィンドウ方式を採用。直近5秒間（T=5）の歩行イベントをリストに保持し、3歩以上（S=3）に達した時点で「歩行検知 (DETECT)」と判定。5秒間新たな歩行イベントがない場合は「解除 (RELEASE)」と判定。
- **ローカルログ機能**: 計測中のセンサーデータ（加速度、歩数、イベント種別、タイムスタンプ）をスマホ内のCSVファイル（`/storage/emulated/0/Android/data/...`）に出力する機能を実装済み。

### フェーズ2: AWSバックエンドと通信モックの開通
- **実装内容**: `backend` にてAWS CDKを用いたインフラを構築。
- **リソース**:
  - API Gateway (REST API, CORS許可)
  - Lambda (Node.js 18.x, `write.js` / `read.js`)
  - DynamoDB (PartitionKey: `deviceId`, SortKey: `timestamp`)
  - S3 + CloudFront (静的ホスティング用)
- **通信ロジック**: Androidアプリ側から `OkHttp` を用いて、歩行検知時および解除時にJSONデータをAWS API GatewayへPOST送信する処理を開通済み。

### フェーズ3: 管理側簡易ビューワーの作成
- **実装内容**: `admin-web-viewer` にてReact(Vite)アプリを構築。
- **機能**: API Gateway経由でDynamoDBのデータを全件取得(Scan)し、デバイスID、イベントカテゴリ（DETECT/RELEASE）、タイムスタンプをテーブル形式で一覧表示する簡易ダッシュボード。

## 3. 今後のロードマップ (Next Steps)

AIエージェント（Claude Code）は、以下の優先順位に従って開発を支援すること。

### [優先度 高] Step 1: Androidアプリのバックグラウンド実行化 (Foreground Service)
- **課題**: 現在は画面が消灯するとOSの省電力機能によりセンサーの監視と通信が停止する。
- **要件**: Androidの `Foreground Service` を実装し、ステータスバーに永続的な通知を表示しながら、画面消灯状態（ポケットに入れた状態）でも歩行検知ロジックとAWSへのAPI送信が稼働し続けるように改修する。
- **対象**: `android-app`

### [優先度 中] Step 2: AWS側の認証認可の導入 (Amazon Cognito)
- **課題**: 現在のAPI Gatewayは認証なしのパブリックエンドポイントになっており、ダミーデバイスIDで送信されている。
- **要件**: 
  - CDKスタックにAmazon Cognito (User Pool / Identity Pool) を追加。
  - 管理Webビューワー（React）にログイン画面を実装し、Cognito連携を行う。
  - API GatewayにCognitoオーソライザーを設定し、セキュアな通信を確立する。
- **対象**: `backend`, `admin-web-viewer`

### [優先度 低] Step 3: DynamoDBのスキーマ最適化とデバイス管理
- **要件**: 現在の単一テーブルから、ユーザーとデバイスIDを紐づけるマッピングテーブルの追加、またはDynamoDBのGSI（グローバルセカンダリインデックス）を活用した効率的なクエリ（期間指定検索など）への拡張。