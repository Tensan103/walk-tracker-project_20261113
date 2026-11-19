#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# CDK の CfnOutput から API_URL を取得し、各環境の .env / local.properties に反映する
# 使い方: ./scripts/sync-env.sh [STACK_NAME] [OUTPUT_KEY]
# ==============================================================================

STACK_NAME="${1:-BackendStack}"       # 実際のCDKスタック名に合わせて変更
OUTPUT_KEY="${2:-ApiUrl}"             # CfnOutput の Logical ID / Export名に合わせて変更

# 1. CloudFormation から Output を取得
echo ">>> Fetching ${OUTPUT_KEY} from CloudFormation stack: ${STACK_NAME}..."
RAW_URL=$(aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --query "Stacks[0].Outputs[?OutputKey=='${OUTPUT_KEY}'].OutputValue" \
  --output text)

if [[ -z "${RAW_URL}" || "${RAW_URL}" == "None" ]]; then
  echo "エラー: スタック '${STACK_NAME}' からキー '${OUTPUT_KEY}' を取得できませんでした。"
  echo "ヒント: 以下のコマンドで正確なスタック名とOutputKeyを確認してください。"
  echo "  aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query 'Stacks[0].Outputs'"
  exit 1
fi

# 末尾のスラッシュを除去（CDKの RestApi.url は末尾に / が付くため統一する）
API_URL="${RAW_URL%/}"
echo ">>> Resolved API_URL: ${API_URL}"

# キーが存在すれば置換、なければ追記するヘルパー関数
upsert_env() {
  local file="$1"
  local key="$2"
  local value="$3"

  touch "${file}"
  if grep -q "^${key}=" "${file}"; then
    # Linux (WSL2) / macOS 両対応のため一時ファイル経由で置換
    sed "s|^${key}=.*|${key}=${value}|" "${file}" > "${file}.tmp" && mv "${file}.tmp" "${file}"
  else
    # ファイル末尾に改行がない場合も考慮して追記
    [[ -s "${file}" && -n "$(tail -c 1 "${file}")" ]] && echo "" >> "${file}"
    echo "${key}=${value}" >> "${file}"
  fi
  echo "  [Updated] ${file} (${key})"
}

# 2. 各ファイルへ反映
upsert_env ".env"                          "API_URL"      "${API_URL}"
upsert_env "admin-web-viewer/.env.local"   "VITE_API_URL" "${API_URL}"
upsert_env "android-app/local.properties"  "API_URL"      "${API_URL}"

echo ">>> すべての環境変数ファイルの同期が完了しました。"