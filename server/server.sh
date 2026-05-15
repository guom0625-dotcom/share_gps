#!/bin/bash
# share-gps 서버 관리 스크립트
# 사용법: ./server.sh [start|stop|restart|status|logs|update]

SERVER_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="share-gps"

case "${1:-status}" in
  start)
    echo "▶ share-gps 시작..."
    cd "$SERVER_DIR"
    pm2 start ecosystem.config.cjs
    pm2 status "$APP_NAME"
    ;;
  stop)
    echo "■ share-gps 중지..."
    pm2 stop "$APP_NAME"
    ;;
  restart)
    echo "↺ share-gps 재시작..."
    pm2 restart "$APP_NAME"
    pm2 status "$APP_NAME"
    ;;
  status)
    pm2 status "$APP_NAME"
    ;;
  logs)
    pm2 logs "$APP_NAME" --lines "${2:-50}"
    ;;
  update)
    echo "⬇ 최신 코드 pull..."
    cd "$SERVER_DIR/.."
    git pull
    cd "$SERVER_DIR"
    npm install --omit=dev
    pm2 restart "$APP_NAME"
    pm2 status "$APP_NAME"
    ;;
  *)
    echo "사용법: $0 {start|stop|restart|status|logs|update}"
    echo ""
    echo "  start    서버 시작"
    echo "  stop     서버 중지"
    echo "  restart  서버 재시작"
    echo "  status   현재 상태 확인"
    echo "  logs [n] 최근 n줄 로그 출력 (기본 50)"
    echo "  update   git pull 후 재시작"
    exit 1
    ;;
esac
