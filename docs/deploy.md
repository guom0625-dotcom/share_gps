# 서버 배포 메모

서버폰: aarch64 Android + Termux + Ubuntu proot

## 1. DuckDNS 설정

1. https://www.duckdns.org 접속 → Google/GitHub 로그인
2. 서브도메인 `guom0625` 등록 → `guom0625.duckdns.org`
3. 상단 **token** 복사

토큰을 `.env`에 저장:
```bash
# server/.env
DUCKDNS_TOKEN=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
HOST=127.0.0.1   # Caddy가 외부 담당
```

IP 갱신 스크립트 cron 등록 (5분마다):
```bash
chmod +x /root/projects/share_gps/scripts/duckdns-update.sh
crontab -e
# 다음 줄 추가:
# */5 * * * * DUCKDNS_TOKEN=<토큰> /root/projects/share_gps/scripts/duckdns-update.sh
```

첫 갱신 확인:
```bash
DUCKDNS_TOKEN=<토큰> /root/projects/share_gps/scripts/duckdns-update.sh
cat /tmp/duckdns.log   # "OK" 나오면 성공
```

---

## 2. 공유기 포트포워딩

공유기 관리 페이지(보통 192.168.0.1 또는 192.168.1.1) → 포트포워딩:

| 외부 포트 | 내부 IP          | 내부 포트 | 용도     |
|----------|-----------------|---------|---------|
| 443      | 서버폰 로컬IP    | 443     | 앱 HTTPS |
| 80       | 서버폰 로컬IP    | 80      | Let's Encrypt 인증 |
| 기존SSH  | 서버폰 로컬IP    | 22      | SSH     |

서버폰 로컬IP 확인 (Termux에서):
```bash
ip addr show wlan0 | grep "inet "
# 또는 안드로이드 설정 → Wi-Fi → 연결된 네트워크 → IP 주소
```

> 서버폰에 **고정 IP** 할당 권장 (공유기 DHCP 설정에서 MAC 주소로 고정)

---

## 3. Caddy 실행

```bash
# 처음 실행 (Let's Encrypt 인증서 자동 발급 — 포트포워딩 먼저 완료 후)
caddy run --config /root/projects/share_gps/server/Caddyfile

# 백그라운드 (pm2로 관리)
pm2 start "caddy run --config /root/projects/share_gps/server/Caddyfile" --name caddy
pm2 save
```

---

## 4. Node.js 서버 pm2 실행

```bash
cd /root/projects/share_gps/server
cp .env.example .env      # 토큰 등 실제 값으로 수정
pm2 start ecosystem.config.cjs
pm2 save

# 로그 확인
pm2 logs share-gps
```

---

## 5. 동작 확인

```bash
# 외부에서
curl https://guom0625.duckdns.org/health
# → {"ok":true,"time":...}

# 사용자 등록
cd /root/projects/share_gps/server
npm run admin -- create-user --name=아빠 --role=parent
```

---

## 6. Termux 재부팅 후 자동 시작

Termux의 `~/.bashrc` 또는 별도 시작 스크립트:
```bash
# proot 진입 후 자동 실행
pm2 resurrect   # 저장된 pm2 프로세스 복원
```
