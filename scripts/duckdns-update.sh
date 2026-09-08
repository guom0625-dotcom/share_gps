#!/bin/bash
# DuckDNS IP 갱신 스크립트 — 5분마다 실행 (pm2 cron_restart 또는 cron)
# 토큰: https://www.duckdns.org 로그인 후 상단에 표시
DOMAIN="guom0625"

# 토큰은 환경변수 우선, 없으면 server/.env 에서 읽음
ENV_FILE="$(cd "$(dirname "$0")/../server" && pwd)/.env"
if [ -z "$DUCKDNS_TOKEN" ] && [ -f "$ENV_FILE" ]; then
  DUCKDNS_TOKEN=$(sed -n 's/^DUCKDNS_TOKEN=//p' "$ENV_FILE" | tr -d '"'"'" | head -1)
fi
TOKEN="${DUCKDNS_TOKEN:-YOUR_TOKEN_HERE}"

result=$(curl -s "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=")
echo "$(date '+%Y-%m-%d %H:%M:%S') $result" >> /tmp/duckdns.log
