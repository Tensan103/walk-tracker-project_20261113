# ==============================================================================
# Walk Tracker API 疎通確認用 Makefile
# ==============================================================================
# 【使い方】
#   1. make sync-env（プロジェクトルートから）で .env を最新化してから実行する。
#   2. 実行例:
#      make help                            # このヘルプを表示
#      make test-post                       # API Key 付きで DETECT を送信
#      make test-post CATEGORY=RELEASE      # RELEASE を送信
#      make test-get ID_TOKEN=<JWT>         # Cognito トークン付きで GET /events
#      make test-e2e ID_TOKEN=<JWT>         # POST → GET の一連テスト
# ==============================================================================

# .env ファイルがあれば読み込む（プロジェクトルートから実行する場合は root .env を参照）
-include .env

# デフォルト設定（.env または実行時引数で上書き可能）
API_URL   ?= https://xxxxxxxxxx.execute-api.ap-northeast-3.amazonaws.com/prod
DEVICE_ID ?= test-device-001
CATEGORY  ?= DETECT
API_KEY   ?=
ID_TOKEN  ?=

STACK_NAME ?= BackendStack

# jq がインストールされていれば JSON を整形
JQ := $(shell command -v jq 2> /dev/null)
FORMAT_JSON = $(if $(JQ),| jq ., )

.PHONY: help sync-env deploy test-post test-get test-e2e check-url check-api-key

help: ## 使い方と利用可能なコマンド一覧を表示
	@echo "========================================"
	@echo " Walk Tracker API テストコマンド"
	@echo "========================================"
	@echo "現在の設定:"
	@echo "  API_URL   = $(API_URL)"
	@echo "  DEVICE_ID = $(DEVICE_ID)"
	@echo "  CATEGORY  = $(CATEGORY)"
	@if [ -n "$(API_KEY)" ]; then echo "  API_KEY   = (set)"; else echo "  API_KEY   = (not set) ← make sync-env を実行してください"; fi
	@if [ -n "$(ID_TOKEN)" ]; then echo "  ID_TOKEN  = (set)"; else echo "  ID_TOKEN  = (not set) ← GET /events は 401 になります"; fi
	@echo ""
	@echo "コマンド一覧:"
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  make %-14s %s\n", $$1, $$2}'
	@echo ""
	@echo "実行例:"
	@echo "  make test-post                              # DETECT を送信（API_KEY 必須）"
	@echo "  make test-post CATEGORY=RELEASE             # RELEASE を送信"
	@echo "  make test-get ID_TOKEN=\$$(aws cognito-idp initiate-auth ... | jq -r '.AuthenticationResult.IdToken')"
	@echo "  make test-e2e ID_TOKEN=<JWT>                # POST + GET を一括検証"

check-url:
	@if echo "$(API_URL)" | grep -q "xxxxxxxxxx"; then \
		echo "エラー: API_URL が未設定です。make sync-env を実行してください。"; \
		exit 1; \
	fi

check-api-key:
	@if [ -z "$(API_KEY)" ]; then \
		echo "エラー: API_KEY が未設定です。make sync-env を実行してから再試行してください。"; \
		exit 1; \
	fi

test-post: check-url check-api-key ## POST /events の単体テスト（x-api-key 付き）
	@TIMESTAMP=$$(date +%s%3N); \
	echo ">>> POST $(API_URL)/events (deviceId=$(DEVICE_ID), category=$(CATEGORY), timestamp=$$TIMESTAMP)"; \
	curl -sS -w "\nHTTP Status: %{http_code}\n" \
		-X POST "$(API_URL)/events" \
		-H "Content-Type: application/json" \
		-H "x-api-key: $(API_KEY)" \
		-d "{\"deviceId\":\"$(DEVICE_ID)\",\"timestamp\":$$TIMESTAMP,\"category\":\"$(CATEGORY)\"}"

test-get: check-url ## GET /events の単体テスト（ID_TOKEN 未指定なら 401 確認用）
	@echo ">>> GET $(API_URL)/events"
	@if [ -n "$(ID_TOKEN)" ]; then \
		curl -sS -X GET "$(API_URL)/events" -H "Authorization: Bearer $(ID_TOKEN)" $(FORMAT_JSON); \
	else \
		echo "注意: ID_TOKEN が未指定のため Authorization ヘッダーなしで送信します（401 確認用）"; \
		curl -sS -o /dev/null -w "HTTP Status: %{http_code}\n" -X GET "$(API_URL)/events"; \
	fi
	@echo ""

test-e2e: check-url check-api-key ## POST (DETECT) → 1秒待機 → GET の一連の疎通確認
	@$(MAKE) --no-print-directory test-post API_URL=$(API_URL) DEVICE_ID=$(DEVICE_ID) CATEGORY=DETECT
	@echo ""
	@sleep 1
	@$(MAKE) --no-print-directory test-get API_URL=$(API_URL) ID_TOKEN=$(ID_TOKEN)

sync-env: ## CloudFormation の CfnOutput から各 .env / local.properties を同期
	./scripts/sync-env.sh $(STACK_NAME)

deploy: ## backend を CDK デプロイし、続けて環境変数ファイルを自動同期
	cd backend && npx cdk deploy --require-approval never
	$(MAKE) --no-print-directory sync-env
