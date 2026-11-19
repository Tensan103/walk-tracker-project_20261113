# ==============================================================================
# Walk Tracker — ルート Makefile
# ==============================================================================
# 【主なターゲット】
#   make sync-env      — CDK Outputs を取得し全 .env / local.properties を更新
#   make deploy        — CDK デプロイ + sync-env を一括実行
#   make deploy-web    — 管理画面をビルドして S3 に同期し CloudFront を無効化
# ==============================================================================

.PHONY: help sync-env deploy deploy-web

help:
	@echo "========================================"
	@echo " Walk Tracker Makefile"
	@echo "========================================"
	@echo ""
	@echo "  make sync-env"
	@echo "      CloudFormation Outputs から API_URL / API_KEY / Cognito / S3・CF 設定を"
	@echo "      .env / admin-web-viewer/.env.local / android-app/local.properties に同期"
	@echo ""
	@echo "  make deploy"
	@echo "      CDK デプロイ（backend/ 配下）+ sync-env を一括実行"
	@echo ""
	@echo "  make deploy-web"
	@echo "      1. sync-env で最新設定を取得"
	@echo "      2. admin-web-viewer の本番ビルド（dist/ 生成）"
	@echo "      3. S3 にアップロード（--delete で古いファイルを削除）"
	@echo "      4. CloudFront キャッシュを無効化"
	@echo "      5. WEB_URL を表示"
	@echo ""

sync-env:
	./scripts/sync-env.sh

deploy:
	cd backend && npx cdk deploy --require-approval never
	$(MAKE) sync-env

deploy-web:
	@echo ">>> [1/4] 環境変数を同期中..."
	$(MAKE) sync-env
	@echo ""
	@echo ">>> [2/4] 管理画面をビルド中..."
	cd admin-web-viewer && npm run build
	@echo ""
	@echo ">>> [3/4] S3 にアップロード中..."
	@( . ./.env && aws s3 sync admin-web-viewer/dist "s3://$${WEB_BUCKET_NAME}" --delete )
	@echo ""
	@echo ">>> [4/4] CloudFront キャッシュを無効化中..."
	@( . ./.env && aws cloudfront create-invalidation \
		--distribution-id "$${CLOUDFRONT_DISTRIBUTION_ID}" \
		--paths "/*" \
		--query "Invalidation.Id" --output text )
	@echo ""
	@echo ">>> デプロイ完了!"
	@( . ./.env && echo "    WEB_URL: $${WEB_URL}" )
