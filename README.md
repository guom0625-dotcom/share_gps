# share_gps

가족 전용 위치공유 안드로이드 앱 (학습/취미 프로토타입).

서버 관리자가 직접 키를 발급하면 가족이 앱에 입력해 사용하는 단순한 모델.
역할(parent/child)에 따라 실시간 위치 또는 30일 경로 이력을 조회할 수 있다.

## 주요 기능

- **실시간 위치 공유** — WebSocket 기반, 지도에 가족 위치 표시
- **GPS 정확도 원** — 정확도 반경을 지도에 시각화
- **이동 속도 표시** — 마커에 🚶 이동 중 / 🚗 Nkm/h 자동 표시
- **배터리 표시** — 동적 아이콘(Battery0Bar~BatteryFull)으로 잔량 표시
- **위치 이력** — 롱프레스 → 달력 → 날짜 선택 → 경로선 + 체류 마커(시간/머문 시간 표시)
- **가로 모드** — 리스트 좌측, 지도 우측 레이아웃 자동 전환
- **배터리 경고** — 상대방 배터리 20% 이하일 때 추적 전 확인 팝업
- **배터리 절약** — ActivityRecognition으로 정지 감지 시 GPS 자동 중단
- **아바타** — 본인 아바타 탭으로 사진 변경, 지도 마커에 사진 표시
- **인앱 업데이터** — 새 버전 감지 시 자동 알림 및 다운로드
- **30일 자동 정리** — 서버에서 30일 초과 위치 기록 주기적 삭제

## 레포 구조

```
share_gps/
├── server/    # Node.js 24 + TypeScript + Fastify + SQLite
├── client/    # Android Kotlin + Jetpack Compose + Naver Maps
├── docs/      # 설계 문서 / API 컨트랙트
└── .github/   # GitHub Actions (APK 빌드/배포)
```

## 빠른 시작

### 서버 (Termux Ubuntu)

```bash
cd server
cp .env.example .env
npm install

# 사용자 및 키 발급
node --experimental-strip-types src/admin/cli.ts create-user --name=아빠 --role=parent

# PM2로 실행
pm2 start ecosystem.config.cjs

# 또는 관리 스크립트 사용
./server.sh start      # 시작
./server.sh restart    # 재시작
./server.sh logs       # 로그 확인
./server.sh update     # git pull 후 재시작
```

### 클라이언트

로컬 빌드 불필요. GitHub Actions가 태그 푸시마다 서명된 APK를 빌드해 Releases에 게시.

가족 구성원:
1. GitHub Releases에서 최신 APK 다운로드 → 설치
2. 첫 실행 시 운영자에게 받은 키 입력
3. 위치 / 활동 인식 / 알림 권한 허용

## 핵심 결정 요약

| 영역 | 선택 |
|---|---|
| 사용자 | 가족만 (parent/child 비대칭 권한) |
| 외부 노출 | 공유기 포트포워딩 + Caddy + DDNS |
| 서버 | Node.js 24 + Fastify + SQLite |
| 클라이언트 | Kotlin + Jetpack Compose + Naver Maps |
| 빌드/배포 | GitHub Actions → GitHub Releases |
| 위치 전송 | 평시 10분 배치 / 능동 조회 시 10초 간격 고정밀 GPS / 정지 시 중단 |
| 이력 보존 | 30일 (parent는 전원 조회, child는 본인만) |

## 라이선스

개인 학습 프로젝트.
