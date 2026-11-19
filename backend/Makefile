# ==============================================================================
# Walk Tracker API 疎通確認用 Makefile
# ==============================================================================
# 【使い方】
#   1. 以下の API_URL をデプロイ済みの API Gateway エンドポイントに書き換えるか、
#      実行時に引数・環境変数で指定してください。（末尾のスラッシュは不要）
#
#   2. 実行例:
#      make help                                  # このヘルプを表示
#      make test-post                             # デフォルト値で DETECT を送信
#      make test-post CATEGORY=RELEASE            # RELEASE を送信
#      make test-post DEVICE_ID=EMP-001           # デバイスIDを指定して送信
#      make test-get                              # 履歴一覧を取得
#      make test-e2e                              # POST → GET を連続実行
#      make test-get API_URL=https://xxx.../prod  # URLを都度指定して実行
# ==============================================================================

# .env ファイルがあれば読み込む（API_URL / DEVICE_ID を定義できる）
-include .env

# デフォルト設定（.env または環境変数 API_URL があればそちらを優先）
API_URL   ?= https://xxxxxxxxxx.execute-api.ap-northeast-3.amazonaws.com/prod
DEVICE_ID ?= test-device-001
CATEGORY  ?= DETECT

# jqがインストールされていればJSONを整形、なければそのまま出力
JQ := $(shell command -v jq 2> /dev/null)
FORMAT_JSON = $(if $(JQ),| jq ., )

.PHONY: help test-post test-get test-e2e check-url

help: ## 使い方と利用可能なコマンド一覧を表示
	@echo "========================================"
	@echo " Walk Tracker API テストコマンド"
	@echo "========================================"
	@echo "現在の設定:"
	@echo "  API_URL   = $(API_URL)"
	@echo "  DEVICE_ID = $(DEVICE_ID)"
	@echo "  CATEGORY  = $(CATEGORY)"
	@echo ""
	@echo "コマンド一覧:"
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  make %-12s %s\n", $$1, $$2}'
	@echo ""
	@echo "実行例:"
	@echo "  make test-post DEVICE_ID=EMP-001 CATEGORY=DETECT"
	@echo "  make test-e2e API_URL=https://your-api-id.execute-api.ap-northeast-3.amazonaws.com/prod"

check-url:
	@if echo "$(API_URL)" | grep -q "xxxxxxxxxx"; then \
		echo "エラー: API_URL が未設定です。Makefile内を書き換えるか、API_URL=... を指定してください。"; \
		exit 1; \
	fi

test-post: check-url ## POST /events の単体テスト（現在時刻のUnixミリ秒を自動生成）
	@TIMESTAMP=$$(date +%s%3N); \
	echo ">>> POST $(API_URL)/events (deviceId=$(DEVICE_ID), category=$(CATEGORY), timestamp=$$TIMESTAMP)"; \
	curl -sS -w "\nHTTP Status: %{http_code}\n" \
		-X POST "$(API_URL)/events" \
		-H "Content-Type: application/json" \
		-d "{\"deviceId\":\"$(DEVICE_ID)\",\"timestamp\":$$TIMESTAMP,\"category\":\"$(CATEGORY)\"}"

test-get: check-url ## GET /events の単体テスト（登録済み履歴を取得）
	@echo ">>> GET $(API_URL)/events"
	@curl -sS -X GET "$(API_URL)/events" $(FORMAT_JSON)
	@echo ""

test-e2e: check-url ## POST (DETECT) → 1秒待機 → GET の一連の疎通確認を実行
	@$(MAKE) --no-print-directory test-post API_URL=$(API_URL) DEVICE_ID=$(DEVICE_ID) CATEGORY=DETECT
	@echo ""
	@sleep 1
	@$(MAKE) --no-print-directory test-get API_URL=$(API_URL)