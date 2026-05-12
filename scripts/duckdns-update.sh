#!/bin/bash
# DuckDNS IP 갱신 스크립트 — cron에 등록해서 5분마다 실행
# 토큰: https://www.duckdns.org 로그인 후 상단에 표시
DOMAIN="guom0625"
TOKEN="${DUCKDNS_TOKEN:-YOUR_TOKEN_HERE}"

result=$(curl -s "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=")
echo "$(date '+%Y-%m-%d %H:%M:%S') $result" >> /tmp/duckdns.log
