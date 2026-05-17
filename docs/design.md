# share_gps 설계 문서

> 가족 전용 위치공유 안드로이드 앱 · 학습/취미용 프로토타입
> 작성일: 2026-05-12

---

## 1. 개요 및 범위

- **사용자**: 가족 구성원만 (소수, 일반 공개 안 함)
- **목적**: 학습 + 가족 안전 확인 (Find My Friends 류, 단순화 버전)
- **플랫폼**: 안드로이드 전용 (iOS 미지원)
- **운영 모델**: 운영자(=본인)가 서버를 직접 관리, 가족에게 키를 발급

### 비목표 (의도적으로 안 하는 것)

- 자가가입/회원가입 흐름
- SMS·이메일 인증
- 비밀번호
- 일반 사용자 대상 배포
- iOS 지원
- 그룹/친구 관계의 복잡한 그래프 (가족 = 하나의 평면 그룹)

---

## 2. 운영/개발 환경

### 서버
- **하드웨어**: 안드로이드폰 (집 와이파이 상시 연결)
- **OS**: Termux 위 proot-distro로 Ubuntu 25.10 (aarch64)
- **자원**: 4코어 / RAM 7.1GB (가용 ~2.4GB) / Disk 162GB
- **동거 프로세스**: 텔레그램 봇들이 이미 가동 중 — 자원 부담 주의
- **이미 셋업됨**: Node v24, Python 3.13, git, ssh, tmux. SSH 포트 1개 포트포워딩.

### 클라이언트
- **하드웨어**: 안드로이드폰 (가족 구성원 각자)
- **로컬 빌드 환경 없음**: APK 빌드는 GitHub Actions에서만

### 외부 노출
- **방식**: 공유기 포트포워딩 + DDNS(DuckDNS 등) + Caddy(자동 TLS)
- **이유**: 본인이 이미 포트포워딩에 익숙. CGNAT 아님(SSH 작동 확인).
- **공인 IP 변동 대비**: DDNS 5분 주기 갱신

---

## 3. 기술 스택

### 서버
| 영역 | 선택 | 비고 |
|---|---|---|
| 런타임 | Node.js 24 + TypeScript | ARM 호환, 메모리 가벼움 |
| 웹 프레임워크 | Fastify 5 | 빠르고 가벼움 |
| 실시간 | WebSocket (`@fastify/websocket` 또는 `ws`) | |
| DB | SQLite (better-sqlite3) | 메모리 부담 적음, 백업 단순 |
| 검증 | zod | 요청 스키마 |
| 푸시 | FCM | 알림 (선택) |
| 리버스 프록시·TLS | Caddy | Let's Encrypt 자동 |
| 프로세스 관리 | pm2 | 자동 재시작 + 부팅 시 실행 |
| 스케줄 | node-cron | 30일 초과 데이터 정리 |

### 클라이언트
| 영역 | 선택 | 비고 |
|---|---|---|
| 언어/UI | Kotlin + Jetpack Compose | 네이티브 안드로이드 |
| 위치 | FusedLocationProviderClient | Google Play Services |
| 활동인식 | ActivityRecognitionClient | 적응형 샘플링 |
| 백그라운드 | Foreground Service + WorkManager | Doze 면제 |
| 로컬 DB | Room | 오프라인 큐 |
| 네트워킹 | Ktor Client (OkHttp engine) | HTTP + WS |
| 비밀저장 | EncryptedSharedPreferences (Android Keystore) | 키 보관 |
| 지도 | Naver Maps Android SDK | 한국 POI 우수 |

### 빌드/배포
- **빌드**: GitHub Actions (`ubuntu-latest` x86_64 러너)
- **서명**: keystore는 GitHub Secrets에 base64로
- **배포**: GitHub Releases — 앱이 Releases API 폴링해서 인앱 업데이트 안내

### 의도적으로 제외
- Docker/Kubernetes — proot 환경 부적합, 자원 부담
- Postgres/PostGIS — 메모리 부담 (필요 시 후일 이전)
- 크로스플랫폼(Flutter/RN) — iOS 미지원이라 이점 없음
- 로컬 APK 빌드 — 메모리 부족, proot 호환성 이슈

---

## 4. 인증 및 권한 모델

### 핵심 원칙
- **자가가입 없음**. 운영자가 서버 CLI로 사용자 + 키를 직접 생성해 가족에게 전달
- **키 = 인증 + 권한**: 키마다 역할(role)이 사용자에 박혀있고, 모든 인가는 role 기반
- **비밀번호 없음**: 키 하나로 끝 (분실 시 운영자가 재발급)

### 역할 (비대칭)
| Role | 실시간 위치 조회 | 30일 경로 이력 조회 |
|---|---|---|
| `parent` | ✅ (모든 구성원) | ✅ (모든 구성원) |
| `child` | ✅ (모든 구성원) | ❌ |

### 키 형식
- `K-` 접두사 + 32자 base32 (256bit 무작위)
- 서버 DB에는 **SHA-256 해시만** 저장 (DB 유출 대비)
- 클라이언트는 `EncryptedSharedPreferences`에 평문 저장 (Android Keystore 기반 암호화)

### 키 라이프사이클
1. 운영자가 서버에서 `npm run admin -- create-user --name=아빠 --role=parent`
2. 1회만 평문 출력 → 카톡 등으로 전달
3. 가족이 앱 첫 실행 시 입력 → 토큰 영구 저장
4. 분실/유출 시 `npm run admin -- reset-key --user-id=<id>`
5. 가족 탈퇴 시 `npm run admin -- revoke-user --user-id=<id>` → 위치 이력도 즉시 삭제

### 데이터 보존
- 모든 사용자 위치 이력 **30일** 보존
- 매일 03:00 cron으로 30일 초과분 자동 삭제
- `mode in (paused, off)` 상태에선 위치 INSERT 자체 거부 (서버에서도 한 번 더)
- 사용자 revoke 시 해당 사용자 위치 이력 즉시 전체 삭제

---

## 5. 위치 전송 전략

### 두 가지 모드

#### 평시 모드 (디폴트)
앱이 백그라운드, 아무도 안 봄.

- **샘플링**: Activity Recognition으로 활동 상태 감지 후 적응형
  - 차량/걷기/자전거: 30~60초
  - 정지: 5분
- **필터**: 직전 위치와 20m 미만이면 콜백 무시
- **업로드**: 2분마다 또는 5건 모이면 `POST /locations/batch`
- **실행 환경**: Foreground Service + 지속 알림 1개

#### 능동 조회 모드
가족이 앱에서 특정 구성원을 탭하고 실시간 보는 동안.

- **트리거**: 시청자 클라가 WS로 `watch_start { targetUserId }`
- **신호 전달**: 서버가 대상자에게 `watching { viewerUserId }` push
- **대상자 클라**: 즉시 샘플링 10초로 가속, WS로 위치 push
- **종료**: 시청자 뒤로가기 → `watch_stop` → 평시 모드 복귀
- **안전장치**: 5~10분 무활동이면 서버가 자동 stop (배터리 보호)
- **다중 시청자**: 한 명이라도 보고 있으면 능동 모드 유지

### 오프라인 큐잉
- 모든 위치는 일단 Room DB에 INSERT
- WorkManager가 네트워크 복구되면 미전송분 FIFO 배치 업로드
- 큐 상한: 최근 7일 또는 1만 건 — 초과분 폐기

### 매니페스트 권한
```
ACCESS_FINE_LOCATION
ACCESS_BACKGROUND_LOCATION
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION
POST_NOTIFICATIONS
ACTIVITY_RECOGNITION
INTERNET
RECEIVE_BOOT_COMPLETED
```

### 권한 요청 흐름
1. 앱 첫 실행: 권한 요청 없음
2. 키 입력 완료 후: 설명 화면 → 위치 "앱 사용 중에만" 요청
3. 며칠 후 자연스러운 시점에 "항상 허용" 업그레이드 유도
4. 알림 권한: 키 입력 직후 함께 요청
5. **"항상 허용"을 첫 단계에서 절대 요구하지 않기** (안드 14+는 한 번 거부되면 재요청 불가)

### 배터리/데이터 목표
| 항목 | 목표 |
|---|---|
| 배터리 (평시) | 시간당 < 2% (정지 시 < 0.5%) |
| 배터리 (능동) | 시간당 < 5% |
| 셀룰러 데이터 | 일 1MB 미만 |
| RAM | < 30MB |

### 사용자 UX 흐름
```
[앱 진입] → [가족 리스트]
   👤 아빠   📍 회사 부근 · 30초 전
   👤 엄마   📍 집 · 2분 전
   👤 동생   📍 학교 · 방금
        ↓ (한 명 탭)
[네이버 지도 풀스크린, 10초마다 갱신]
"○○를 실시간으로 보는 중"
        ↓ (뒤로가기)
[가족 리스트 복귀] → 대상자는 평시 모드로
```

---

## 6. 레포 구조

모노레포. 서버/클라이언트 API 컨트랙트가 한 PR로 안 깨지게.

```
share_gps/
├── server/                       # Node.js + TypeScript + Fastify
│   ├── src/
│   │   ├── index.ts              # Fastify 부트, WS, DB
│   │   ├── auth.ts               # Bearer 키 검증 미들웨어
│   │   ├── db.ts                 # better-sqlite3 + 마이그레이션 러너
│   │   ├── routes/
│   │   │   ├── family.ts         # GET /family, GET /me
│   │   │   ├── locations.ts      # POST /locations/batch, GET /locations/:id/history
│   │   │   └── shareState.ts     # POST /me/share-state
│   │   ├── ws/
│   │   │   ├── server.ts         # WS 서버, 인증, 라우팅
│   │   │   └── watching.ts       # 시청 세션 관리 (메모리 Map)
│   │   ├── admin/
│   │   │   ├── createUser.ts     # CLI: 사용자 등록 + 키 발급
│   │   │   ├── listUsers.ts
│   │   │   ├── revokeUser.ts
│   │   │   ├── resetKey.ts
│   │   │   └── updateUser.ts     # CLI: 이름/역할 수정
│   │   └── jobs/
│   │       └── pruneOldLocations.ts   # 매일 03:00 cron
│   ├── migrations/
│   │   └── 001_init.sql
│   ├── package.json
│   ├── tsconfig.json
│   ├── .env.example
│   └── ecosystem.config.cjs      # pm2 설정
│
├── client/                       # Android Kotlin + Compose
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── kotlin/com/sharegps/
│   │       │   ├── MainActivity.kt
│   │       │   ├── ShareGpsApp.kt
│   │       │   ├── data/
│   │       │   │   ├── KeyStore.kt          # EncryptedSharedPreferences
│   │       │   │   ├── ApiClient.kt         # Ktor
│   │       │   │   ├── WebSocketClient.kt
│   │       │   │   └── LocationQueueDao.kt  # Room
│   │       │   ├── location/
│   │       │   │   ├── LocationService.kt   # Foreground Service
│   │       │   │   ├── LocationCollector.kt
│   │       │   │   ├── ActivityMonitor.kt
│   │       │   │   └── UploadWorker.kt      # WorkManager
│   │       │   ├── ui/
│   │       │   │   ├── enroll/EnrollScreen.kt
│   │       │   │   ├── home/FamilyListScreen.kt
│   │       │   │   ├── live/LiveMapScreen.kt
│   │       │   │   ├── history/HistoryScreen.kt   # parent only
│   │       │   │   └── settings/SettingsScreen.kt
│   │       │   └── update/UpdateChecker.kt
│   │       └── res/...
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle/wrapper/
│   ├── gradlew
│   └── local.properties.example
│
├── docs/
│   ├── design.md                 # 이 문서
│   ├── api.md                    # API 컨트랙트 (단일 진실 출처)
│   └── deploy.md                 # 서버 배포 메모
│
├── .github/
│   └── workflows/
│       ├── build-apk.yml
│       └── server-ci.yml
│
├── .gitignore
└── README.md
```

---

## 7. DB 스키마 (SQLite)

```sql
-- migrations/001_init.sql

-- 가족 구성원
CREATE TABLE users (
    id          TEXT PRIMARY KEY,                         -- ulid
    name        TEXT NOT NULL,
    role        TEXT NOT NULL CHECK(role IN ('parent','child')),
    created_at  INTEGER NOT NULL,                         -- unix ms
    revoked_at  INTEGER                                   -- NULL이면 활성
);

-- 인증 키 (실키 평문 저장 안 함)
CREATE TABLE auth_keys (
    key_hash       TEXT PRIMARY KEY,                      -- SHA-256(key)
    user_id        TEXT NOT NULL REFERENCES users(id),
    created_at     INTEGER NOT NULL,
    last_used_at   INTEGER,
    revoked_at     INTEGER
);
CREATE INDEX idx_auth_keys_user ON auth_keys(user_id);

-- 위치 이력 (30일 보존)
CREATE TABLE locations (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      TEXT    NOT NULL REFERENCES users(id),
    lat          REAL    NOT NULL,
    lng          REAL    NOT NULL,
    accuracy     REAL,
    activity     TEXT,
    battery      INTEGER,
    recorded_at  INTEGER NOT NULL,                        -- 단말 시계
    received_at  INTEGER NOT NULL                         -- 서버 시계
);
CREATE INDEX idx_locations_user_time ON locations(user_id, recorded_at DESC);

-- 최신 위치 (조회 최적화)
CREATE TABLE current_location (
    user_id      TEXT PRIMARY KEY REFERENCES users(id),
    lat          REAL    NOT NULL,
    lng          REAL    NOT NULL,
    accuracy     REAL,
    activity     TEXT,
    battery      INTEGER,
    recorded_at  INTEGER NOT NULL
);

-- 공유 상태 (일시정지/정확도)
CREATE TABLE share_state (
    user_id         TEXT PRIMARY KEY REFERENCES users(id),
    mode            TEXT NOT NULL DEFAULT 'sharing'
                    CHECK(mode IN ('sharing','paused','off')),
    precision_mode  TEXT NOT NULL DEFAULT 'exact'
                    CHECK(precision_mode IN ('exact','approximate')),
    paused_until    INTEGER,                              -- NULL이면 수동해제
    updated_at      INTEGER NOT NULL
);
```

### 데이터 정책
- 키는 평문 저장 안 함 (sha256 해시만)
- `locations` INSERT 시 `current_location`도 UPSERT
- `mode in (paused, off)`인 사용자의 위치는 INSERT 거부
- 매일 03:00 cron: `DELETE FROM locations WHERE recorded_at < (now - 30d)`
- 사용자 revoke 시: 해당 사용자 위치 이력 즉시 전체 삭제

### 추정 용량
- 의미있는 이동만 저장(>50m): 1인당 ~2MB/월
- 가족 5명 × 30일 = **~10MB** — SQLite로 충분

---

## 8. API 컨트랙트

### 인증
모든 HTTP 요청: `Authorization: Bearer <key>` 헤더.
서버는 SHA-256 해시 후 `auth_keys` 조회 → 활성 여부 확인 → `users` 조인.

### REST 엔드포인트

```
GET /me
  → { id, name, role,
      shareState: { mode, precisionMode, pausedUntil } }

GET /family
  → [
      {
        id, name, role,
        shareMode: 'sharing'|'paused'|'off',
        current: { lat, lng, accuracy, recordedAt } | null
      }, ...
    ]
  # 메인 가족 리스트 화면용

POST /locations/batch
  body: [
    { lat, lng, accuracy, activity, battery, recordedAt }, ...
  ]
  → { saved: N, rejected: N }
  # mode가 paused/off면 reject

GET /locations/:userId/history?from=<ms>&to=<ms>
  Auth: parent role ONLY
  → [ { lat, lng, accuracy, recordedAt }, ... ]
  # 최대 30일 이내

POST /me/share-state
  body: { mode, precisionMode, pausedUntilMinutes? }
  → { ok: true }

GET /version
  → { server: '1.0.0', minClient: '1.0.0' }
```

### WebSocket `/ws`

```
# Client → Server
{ type: "auth", key: "<key>" }                       # 첫 메시지
{ type: "location", lat, lng, accuracy, recordedAt } # 능동 모드 push
{ type: "watch_start", targetUserId }
{ type: "watch_stop",  targetUserId }
{ type: "ping" }

# Server → Client
{ type: "auth_ok", userId, role }
{ type: "auth_fail", reason }
{ type: "watching",      viewerUserId }              # 누군가 너를 보기 시작
{ type: "watching_stop", viewerUserId }
{ type: "location_update", userId, lat, lng, accuracy, recordedAt }
{ type: "pong" }
```

### Admin CLI (서버에서 직접)

```bash
npm run admin -- create-user  --name=아빠 --role=parent
# → "사용자 생성. 키: K-7f3a-b9e2-... (재출력 불가)"

npm run admin -- list-users
npm run admin -- reset-key   --user-id=<id>
npm run admin -- revoke-user --user-id=<id>             # 위치 이력도 같이 삭제
npm run admin -- update-user --user-id=<id> [--name=<이름>] [--role=parent|child]
```

---

## 9. 보안 베이스라인

- [ ] 모든 통신 HTTPS 강제 (Caddy + Let's Encrypt)
- [ ] 키는 서버 DB에 SHA-256 해시로만 저장
- [ ] 클라 키는 EncryptedSharedPreferences (Android Keystore)
- [ ] 위치 이력 30일 자동 삭제
- [ ] 일시정지 중 위치 전송 자체 차단 (클라 + 서버)
- [ ] 사용자 탈퇴/폐기 시 위치 이력 즉시 삭제
- [ ] API 키 / NAVER_MAP_CLIENT_ID 절대 레포 커밋 금지 (`.env`, `local.properties`)
- [ ] GitHub Secrets로 서명 keystore 관리
- [ ] CORS는 운영 도메인만 허용 (또는 비활성)

---

## 10. 부트스트랩 단계

1. **루트 셋업**: `.gitignore`, `README.md`, GitHub 레포 + git init
2. **서버 골격**: `package.json`, `tsconfig.json`, `db.ts`, `index.ts`, `migrations/001_init.sql`, `.env.example`
3. **어드민 CLI**: `create-user` 동작 확인
4. **인증 + 핵심 API**: `auth.ts`, `/me`, `/family`, `/locations/batch`, `/locations/:id/history`
5. **WebSocket + 시청 세션**: `ws/server.ts`, `ws/watching.ts`
6. **외부 노출**: DuckDNS 발급, Caddyfile, 공유기 포트포워딩 443 → 서버폰
7. **안드로이드 스켈레톤**: Gradle/Compose 셋업, EnrollScreen, GitHub Actions 빌드 워크플로우
8. **위치 수집**: LocationService, Room 큐, WorkManager 업로드
9. **가족 리스트 + 네이버 지도**: FamilyListScreen, LiveMapScreen, WS 능동 모드
10. **권한 UX + 일시정지 + 인앱 업데이터**: 권한 안내 흐름, 토글 UI, GitHub Releases 폴링

---

## 11. 향후 확장 후보 (v2 이후, 지금은 안 함)

- iOS 클라이언트
- 개인별 ON/OFF (현재는 그룹 단위만)
- 지오펜싱 + 도착/이탈 알림
- 일정 공유
- 위치 이력 시각화 (히트맵, 통계)
- 조회 로그 (누가 언제 내 위치를 봤는지 — 신뢰 UX)
- PostgreSQL/PostGIS 이전 (가족 외로 확장 시)
