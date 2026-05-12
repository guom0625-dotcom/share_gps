# share_gps

가족 전용 위치공유 안드로이드 앱 (학습/취미 프로토타입).

서버 관리자가 직접 키를 발급하면 가족이 앱에 입력해 사용하는 단순한 모델.
역할(parent/child)에 따라 실시간 위치 또는 30일 경로 이력을 조회할 수 있다.

> 전체 설계는 [docs/design.md](docs/design.md) 참고.

## 레포 구조

```
share_gps/
├── server/    # Node.js 24 + TypeScript + Fastify + SQLite
├── client/    # Android Kotlin + Jetpack Compose
├── docs/      # 설계 문서 / API 컨트랙트
└── .github/   # GitHub Actions (APK 빌드/배포)
```

## 빠른 시작

### 서버 (Termux Ubuntu)

```bash
cd server
cp .env.example .env
npm install
npm run dev                                         # 개발 모드
npm run admin -- create-user --name=아빠 --role=parent   # 사용자/키 발급
```

자세한 배포(Caddy, 포트포워딩, pm2)는 `docs/deploy.md` (작성 예정).

### 클라이언트

로컬 빌드 불필요. GitHub Actions가 푸시마다 서명된 APK를 빌드해 Releases에 게시.

가족 구성원:
1. GitHub Releases에서 최신 APK 다운로드 → 설치
2. 첫 실행 시 운영자에게 받은 키 입력
3. 위치/알림 권한 허용

## 핵심 결정 요약

| 영역 | 선택 |
|---|---|
| 사용자 | 가족만 (parent/child 비대칭 권한) |
| 외부 노출 | 공유기 포트포워딩 + Caddy + DDNS |
| 서버 | Node.js 24 + Fastify + SQLite |
| 클라이언트 | Kotlin + Jetpack Compose + Naver Maps |
| 빌드/배포 | GitHub Actions → GitHub Releases |
| 위치 전송 | 평시 30~60초 배치 / 능동 조회 시 3~5초 WS push |
| 이력 보존 | 모든 사용자 30일 (parent만 조회) |

## 라이선스

개인 학습 프로젝트.
