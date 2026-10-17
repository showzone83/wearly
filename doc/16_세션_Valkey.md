# 16. 로그인 세션을 Valkey로 · 로그인 사용자 확인 · 강제 로그아웃

> 백엔드(`backend/`), 관리자 사이트(`admin/`), 앱(`mobile/`), compose가 함께 따르는 계약이다. 2026-10-17 결정.

## 1. 배경

Refresh 토큰을 지금은 DB(`tb_refresh_token`)에 둔다. 만료된 행이 쌓이고, "지금 로그인한 사용자"를 보거나 강제로 끊는 기능이 없다. Valkey(Redis 호환)로 옮겨 세션을 관리하고, 관리자 사이트에서 로그인 세션을 보고 끊을 수 있게 한다.

## 2. 인프라

- compose에 `valkey` 서비스: 이미지 `valkey/valkey:8`(태그는 최신 안정판 확인), 컨테이너 `wearly-valkey`, 호스트 포트 `6380`(다른 Redis와 겹치지 않게), **AOF 켜기**(`--appendonly yes`), 볼륨 `wearly-valkeydata`, 헬스체크 `valkey-cli ping`. 백엔드는 valkey가 healthy일 때 시작.
- 백엔드 설정: `spring.data.redis.host/port`(로컬 기본 `localhost:6380`, docker 프로필 `valkey:6379`), 비밀번호는 로컬에서는 없음(운영은 yml·환경변수).
- 테스트: Testcontainers로 Valkey 컨테이너(`valkey/valkey:8`).

## 3. 저장 구조 (키 접두사 `wearly:`)

| 키 | 값 | TTL |
|---|---|---|
| `wearly:rt:{tokenHash}` | 해시: `userId`, `sessionId`, `status`(ACTIVE·ROTATED·REVOKED), `replacedBy`, `createdAt` | Refresh 만료까지 (회전·폐기된 것도 만료까지 남겨 재사용을 감지) |
| `wearly:session:{sessionId}` | 해시: `userId`, `currentHash`, `client`(APP·ADMIN·WEB), `userAgent`, `ip`, `createdAt`(로그인), `lastRefreshAt`, `lastSeenAt` | Refresh 만료까지, 회전마다 연장 |
| `wearly:user:{userId}:sessions` | 집합: sessionId | 가장 늦은 세션 만료까지 |
| `wearly:user:{userId}:invalid-before` | 문자열: epoch 초 | Access 토큰 수명 + 여유 |

- `sessionId` = 지금의 family_id 개념(로그인 한 번 = 세션 하나). 회전해도 같은 세션.
- 재사용 감지: 이미 ROTATED인 토큰이 오면 그 세션 전체를 끊고 `REFRESH_REUSED`(지금과 같음).
- `lastSeenAt`: 인증된 요청마다 갱신하되 같은 세션은 1분에 한 번만 쓴다.
- **즉시 무효화**: 강제 로그아웃·정지·탈퇴 때 `invalid-before`를 지금 시각으로 두고, JWT 인증에서 `iat < invalid-before`인 Access 토큰은 401 `TOKEN_INVALID`. Access 토큰에 `sid`(sessionId) 클레임을 넣어 세션 하나만 끊을 때는 그 세션을 지우고 `sid`로 확인한다(`session` 키가 없으면 401).
- `tb_refresh_token`은 새 마이그레이션에서 지운다(로컬 개발 단계라 기존 로그인은 한 번 끊겨도 된다 — 서버를 올리면 모두 다시 로그인).
- 로그인·로그아웃·강제 로그아웃·재사용 감지는 DB에 기록한다: 강제 로그아웃은 `tb_admin_audit_log`(`SESSION_REVOKE`·`USER_SESSIONS_REVOKE`), 그 밖은 새 테이블 `tb_auth_event`(user_id, type: LOGIN·LOGOUT·REFRESH_REUSED·FORCED_LOGOUT, session_id, client, ip, user_agent, created_at) — 회원별 최근 로그인 이력용.

## 4. API

### 인증 (기존 경로·응답 그대로)
- 로그인·가입 요청 헤더로 `client` 구분: `X-Wearly-Client: app|admin|web`(없으면 User-Agent로 추정). IP는 `X-Forwarded-For` 첫 값 또는 원격 주소.

### 관리자 (`/api/v1/admin`, ADMIN)
| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/sessions?q=&client=&page=&size=` | `Page<{ sessionId, userId, email, name, role, client, userAgent, ip, createdAt, lastRefreshAt, lastSeenAt, current }>` — `current`는 요청한 관리자 자신의 세션. 정렬: lastSeenAt 최신순 |
| GET | `/users/{id}/sessions` | 그 회원의 세션 목록 + 최근 로그인 이력 20개(`tb_auth_event`) |
| DELETE | `/sessions/{sessionId}` | 204. 그 세션만 끊음. 자기 현재 세션은 400 |
| DELETE | `/users/{id}/sessions` | 204. 그 회원의 모든 세션 끊고 `invalid-before` 설정. 자기 자신은 400 |

- 대시보드에 `activeSessions`(살아 있는 세션 수), `activeUsers24h`(24시간 안에 활동한 회원 수) 추가.
- 회원 정지·탈퇴 때 그 회원의 세션을 모두 끊는다(지금 DB에서 하던 것과 같게).

## 5. 관리자 사이트

- 메뉴 "로그인 사용자": 세션 표(회원, 역할, 기기 종류 아이콘, 기기 요약(User-Agent에서 iOS·Android·Chrome 등), IP, 로그인 시각, 마지막 활동 "3분 전"), 검색·기기 필터, 30초마다 새로 고침.
- 행마다 "로그아웃 시키기", 회원 이름 옆 "모든 기기에서 로그아웃". 확인 대화상자 후 실행, 결과 토스트. 내 현재 세션은 "현재 세션" 배지와 버튼 비활성.
- 회원 화면의 회원 상세(또는 행 펼치기)에 그 회원의 세션과 최근 로그인 이력.

## 6. 앱

- 요청 헤더 `X-Wearly-Client: app`.
- 강제 로그아웃되면 다음 API 요청이 401 `TOKEN_INVALID` → 이미 있는 "로그인이 풀렸어요" 흐름으로 로그인 화면. 문구: "다른 곳에서 로그아웃되었어요. 다시 로그인해 주세요."
