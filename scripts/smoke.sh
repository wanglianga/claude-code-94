#!/usr/bin/env bash
# 端到端冒烟：需先 docker compose up -d 且应用健康检查通过。
# 用法：
#   export CC_PUBLISH_PORT=$(docker compose port app 8080 | cut -d: -f2)
#   bash scripts/smoke.sh
set -euo pipefail

export CC_PUBLISH_PORT="${CC_PUBLISH_PORT:-3194}"
export CC_BASE_URL="${CC_BASE_URL:-http://host.docker.internal:${CC_PUBLISH_PORT}}"

HERE="$(cd "$(dirname "$0")" && pwd)"
python3 "${HERE}/smoke.py"
