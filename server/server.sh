#!/bin/bash
# share-gps 서버 관리 스크립트 (share-gps + caddy)
# 사용법: ./server.sh [start|stop|restart|status|logs|update]

SERVER_DIR="$(cd "$(dirname "$0")" && pwd)"

case "${1:-status}" in
  start)
    echo "▶ 서버 시작..."
    cd "$SERVER_DIR"
    pm2 start ecosystem.config.cjs
    pm2 status
    ;;
  stop)
    echo "■ 서버 중지..."
    pm2 stop share-gps caddy
    ;;
  restart)
    echo "↺ 서버 재시작..."
    pm2 restart share-gps caddy
    pm2 status
    ;;
  status)
    pm2 status
    ;;
  logs)
    pm2 logs "${2:-share-gps}" --lines "${3:-50}"
    ;;
  update)
    echo "⬇ 최신 코드 pull..."
    cd "$SERVER_DIR/.."
    git pull
    cd "$SERVER_DIR"
    npm install --omit=dev
    pm2 restart share-gps
    pm2 status
    ;;
  *)
    echo "사용법: $0 {start|stop|restart|status|logs|update}"
    echo ""
    echo "  start          서버 시작 (share-gps + caddy)"
    echo "  stop           서버 중지 (share-gps + caddy)"
    echo "  restart        서버 재시작 (share-gps + caddy)"
    echo "  status         현재 상태 확인"
    echo "  logs [앱] [n]  로그 출력 (앱: share-gps|caddy, 기본 50줄)"
    echo "  update         git pull 후 share-gps 재시작"
    exit 1
    ;;
esac
