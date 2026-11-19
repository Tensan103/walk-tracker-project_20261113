#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# CDK の CfnOutput から設定値を取得し、各環境の .env / local.properties に反映する
# 使い方: ./scripts/sync-env.sh [STACK_NAME]
# ==============================================================================

STACK_NAME="${1:-BackendStack}"

# CloudFormation から全 Output を一括取得
echo ">>> Fetching outputs from CloudFormation stack: ${STACK_NAME}..."
OUTPUTS=$(aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --query "Stacks[0].Outputs" \
  --output json)

get_output() {
  local key="$1"
  echo "${OUTPUTS}" | python3 -c \
    "import sys,json; outputs=json.load(sys.stdin); print(next((o['OutputValue'] for o in outputs if o['OutputKey']=='${key}'), ''))"
}

RAW_API_URL=$(get_output "ApiUrl")
if [[ -z "${RAW_API_URL}" || "${RAW_API_URL}" == "None" ]]; then
  echo "エラー: スタック '${STACK_NAME}' から ApiUrl を取得できませんでした。"
  exit 1
fi
API_URL="${RAW_API_URL%/}"
echo ">>> Resolved API_URL: ${API_URL}"

USER_POOL_ID=$(get_output "UserPoolId")
USER_POOL_CLIENT_ID=$(get_output "UserPoolClientId")
API_KEY_ID=$(get_output "ApiKeyId")

if [[ -n "${API_KEY_ID}" ]]; then
  echo ">>> Fetching API key value for ApiKeyId: ${API_KEY_ID}..."
  API_KEY=$(aws apigateway get-api-key \
    --api-key "${API_KEY_ID}" \
    --include-value \
    --query "value" \
    --output text)
  echo ">>> API_KEY resolved."
else
  API_KEY=""
fi

# キーが存在すれば置換、なければ追記するヘルパー関数
upsert_env() {
  local file="$1"
  local key="$2"
  local value="$3"

  touch "${file}"
  if grep -q "^${key}=" "${file}"; then
    sed "s|^${key}=.*|${key}=${value}|" "${file}" > "${file}.tmp" && mv "${file}.tmp" "${file}"
  else
    [[ -s "${file}" && -n "$(tail -c 1 "${file}")" ]] && echo "" >> "${file}"
    echo "${key}=${value}" >> "${file}"
  fi
  echo "  [Updated] ${file} (${key})"
}

# 各ファイルへ反映
WEB_BUCKET_NAME=$(get_output "WebBucketName")
CLOUDFRONT_DISTRIBUTION_ID=$(get_output "CloudFrontDistributionId")
WEB_URL=$(get_output "WebUrl")

upsert_env ".env"                          "API_URL"                      "${API_URL}"
[[ -n "${API_KEY}" ]]                     && upsert_env ".env" "API_KEY"                      "${API_KEY}"
[[ -n "${USER_POOL_ID}" ]]                && upsert_env ".env" "USER_POOL_ID"                 "${USER_POOL_ID}"
[[ -n "${USER_POOL_CLIENT_ID}" ]]         && upsert_env ".env" "USER_POOL_CLIENT_ID"          "${USER_POOL_CLIENT_ID}"
[[ -n "${WEB_BUCKET_NAME}" ]]             && upsert_env ".env" "WEB_BUCKET_NAME"              "${WEB_BUCKET_NAME}"
[[ -n "${CLOUDFRONT_DISTRIBUTION_ID}" ]] && upsert_env ".env" "CLOUDFRONT_DISTRIBUTION_ID"   "${CLOUDFRONT_DISTRIBUTION_ID}"
[[ -n "${WEB_URL}" ]]                     && upsert_env ".env" "WEB_URL"                      "${WEB_URL}"

upsert_env "admin-web-viewer/.env.local"   "VITE_API_URL"                 "${API_URL}"
[[ -n "${USER_POOL_ID}" ]]       && upsert_env "admin-web-viewer/.env.local" "VITE_USER_POOL_ID"           "${USER_POOL_ID}"
[[ -n "${USER_POOL_CLIENT_ID}" ]] && upsert_env "admin-web-viewer/.env.local" "VITE_USER_POOL_CLIENT_ID"   "${USER_POOL_CLIENT_ID}"

upsert_env "android-app/local.properties"  "API_URL"                      "${API_URL}"
[[ -n "${API_KEY}" ]]            && upsert_env "android-app/local.properties" "API_KEY"                    "${API_KEY}"

echo ">>> すべての環境変数ファイルの同期が完了しました。"
