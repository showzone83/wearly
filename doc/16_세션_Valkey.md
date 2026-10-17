# 16. 로그인 세션을 Valkey로 · 로그인 사용자 확인 · 강제 로그아웃

> 백엔드(`backend/`), 관리자 사이트(`admin/`), 앱(`mobile/`), compose가 함께 따르는 계약이다. 2026-10-17 결정, 같은 날 구현(백엔드·하네스).
>
> **처음 안과 달라진 점**: `wearly:user:{userId}:invalid-before` 키는 쓰지 않는다. Access 토큰의 `sid`(세션 ID)로 매 요청 세션이 살아 있는지 확인해서, 세션 하나든 회원 전체든 끊는 즉시 그 Access 토큰이 401이 된다(3장 "즉시 무효화").

## 1. 배경

Refresh 토큰을 DB(`tb_refresh_token`)에 두었더니 만료된 행이 쌓이고, "지금 로그인한 사용자"를 보거나 강제로 끊는 기능이 없었다. Valkey(Redis 호환)로 옮겨 세션을 관리하고, 관리자 사이트에서 로그인 세션을 보고 끊을 수 있게 한다.

## 2. 인프라

- compose `valkey` 서비스: 이미지 `valkey/valkey:9.1`(2026-10 기준 최신 안정판, 9.2는 rc), 컨테이너 `wearly-valkey`, 호스트 포트 **6380**→6379(다른 Redis와 겹치지 않게), **AOF 켜기**(`--appendonly yes`), 볼륨 `wearly-valkeydata`, 헬스체크 `valkey-cli ping`. backend는 `depends_on: valkey: condition: service_healthy`.
- 백엔드: `spring-boot-starter-data-redis`(Lettuce). `application.yml`의 `spring.data.redis.host/port/database`(로컬 기본 `localhost:6380`, DB 0), docker 프로필 `valkey:6379`(`application-docker.yml`). 비밀번호는 로컬에 없음(운영은 yml·환경변수 `SPRING_DATA_REDIS_*`).
- 하네스: 같은 `wearly-valkey`를 쓰되 **DB 1**(`SPRING_DATA_REDIS_DATABASE=1`)을 쓰고 시작 때 그 DB만 `FLUSHDB`. 없으면 `docker compose up -d valkey`로 띄운다.
- 테스트: Testcontainers `GenericContainer`(`valkey/valkey:9.1`, `support/TestValkey`). 응답 스냅샷 테스트는 DB 2를 비우고 쓴다. `WEARLY_TEST_VALKEY_HOST`(·`_PORT`)를 주면 컨테이너 대신 그 서버.
- `/actuator/health`에 Valkey 상태가 들어간다(Valkey가 죽으면 DOWN).

## 3. 저장 구조 (키 접두사 `wearly:`)

시각 값은 모두 **epoch 밀리초 문자열**이다. KEYS 명령은 쓰지 않는다.

| 키 | 값 | TTL |
|---|---|---|
| `wearly:rt:{tokenHash}` | 해시: `userId`, `sessionId`, `status`(ACTIVE·ROTATED·REVOKED), `replacedBy`(다음 토큰 해시), `createdAt` | 발급 때 정한 Refresh 만료(`PEXPIREAT`). 회전·폐기돼도 만료까지 남겨 재사용을 감지 |
| `wearly:session:{sessionId}` | 해시: `userId`, `currentHash`, `client`(APP·ADMIN·WEB), `userAgent`(300자까지), `ip`(45자까지), `createdAt`(로그인), `lastRefreshAt`(갱신 전에는 없음), `lastSeenAt` | 마지막 회전 + Refresh 수명 (회전마다 연장) |
| `wearly:user:{userId}:sessions` | 집합: sessionId | 가장 늦은 세션 만료까지 (`PEXPIRETIME`보다 늦을 때만 연장) |
| `wearly:sessions` | 정렬 집합: sessionId, score = `lastSeenAt` | 없음. 관리자 목록 정렬·페이징용 |
| `wearly:sessions:expiry` | 정렬 집합: sessionId, score = 세션 만료 시각 | 없음. 목록·통계를 읽을 때 `now`보다 이른 멤버를 두 인덱스에서 지운다 |

- `sessionId` = UUID 문자열(로그인 한 번 = 세션 하나, 예전 `family_id`). 가입도 로그인처럼 새 세션을 만든다. 회전해도 같은 세션.
- **원자성**: 만들기·회전·끊기·인증 확인·인덱스 정리는 Lua 스크립트 하나씩으로 한다(`ValkeyScripts`). 회전은 "그 토큰이 ACTIVE이고 세션의 `currentHash`일 때만" 바꾸는 compare-and-set이라, 같은 토큰으로 동시에 두 번 갱신하면 한쪽만 성공한다. 진 쪽은 다시 읽어 ROTATED를 보고 재사용으로 처리한다(이전 DB 행 잠금과 같은 결과: 401 `REFRESH_REUSED`, 그 세션 끊김).
- **재사용 감지**: 이미 ROTATED인 토큰이 오면 그 세션을 끊고(세션 삭제 + 현재 토큰 REVOKED) 401 `REFRESH_REUSED`. 다른 세션은 그대로.
- **세션 끊기**(로그아웃·강제 로그아웃·정지·재사용 감지): 세션 해시를 지우고, 회원 집합·두 인덱스에서 빼고, 현재 토큰을 REVOKED로 둔다. 그 뒤 그 Refresh 토큰은 401 `REFRESH_INVALID`.
- **즉시 무효화**: Access 토큰에 `sid` 클레임이 있다. JWT 인증(서명·만료·발급자 확인 뒤)이 매 요청 `wearly:session:{sid}`가 있고 그 `userId`가 토큰 `sub`와 같은지 본 다음 회원 상태(DB)를 본다. 세션이 없거나 `sid`가 없는 옛 토큰이면 401 `TOKEN_INVALID`. 그래서 세션 하나·회원 전체 강제 로그아웃, 로그아웃, 정지, 탈퇴가 남은 Access 수명과 상관없이 바로 적용된다. (만료된 Access 토큰은 이 확인 전에 `TOKEN_EXPIRED`.)
- **`lastSeenAt`**: 로그인·갱신 때 쓰고, 인증 확인 때는 같은 세션에서 **1분에 한 번만** 고친다(`wearly:sessions` score도 함께).
- `tb_refresh_token`은 `V10__auth_session_valkey.sql`에서 지웠다(로컬 개발 단계라 기존 로그인은 한 번 끊긴다).
- **로그인 이력** `tb_auth_event`(같은 마이그레이션): `auth_event_id`, `user_id`(FK, 회원 삭제 시 CASCADE), `type`(LOGIN·LOGOUT·REFRESH_REUSED·FORCED_LOGOUT, CHECK), `session_id`, `client`(CHECK), `ip`, `user_agent`, `created_at`. 인덱스 `(user_id, created_at DESC, auth_event_id DESC)`.
  - LOGIN: 가입·로그인. LOGOUT: 회원이 직접 로그아웃. REFRESH_REUSED: 재사용 감지(요청한 기기의 IP·UA). FORCED_LOGOUT: 관리자 강제 로그아웃·회원 정지(끊긴 세션의 기기 정보, 세션마다 한 줄).
  - 탈퇴한 회원 행에는 넣지 않는다(INSERT … WHERE EXISTS). 탈퇴 때는 이력 없이 세션만 지운다.
  - 관리자가 누가 끊었는지는 `tb_admin_audit_log`에 따로 남는다(4장).

## 4. API

### 인증 (기존 경로·응답 그대로)

- 가입·로그인·갱신·로그아웃 요청 헤더로 기기 구분: `X-Wearly-Client: app|admin|web`(대소문자 무시). 없거나 모르는 값이면 User-Agent로 추정: `Mozilla/`가 들어 있으면 WEB, 아니면 APP(React Native의 CFNetwork·okhttp, 서버 클라이언트). IP는 `X-Forwarded-For` 첫 값 또는 원격 주소. 세션의 기기 정보는 로그인 때 정한다(갱신 때 바꾸지 않음).
- CORS 허용 요청 헤더에 `X-Wearly-Client`를 넣었다(관리자 사이트·Expo 웹).
- Access 토큰 클레임: `sub`, `iss`, `role`, **`sid`**, `iat`, `exp`. `exp`는 초 단위로 **올림**해서 실제 수명이 설정값 이상이다(전에는 버림이라 PT1S 테스트가 가끔 바로 만료됐다).

### 관리자 (`/api/v1/admin`, ADMIN)

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/sessions?q=&client=&page=&size=` | 200 `{ items: AdminSession[], page, size, total }` (기존 관리자 목록과 같은 모양, page 0부터·size 기본 20·최대 100). 정렬: lastSeenAt 최신순. `q`: 이메일·이름 부분 일치(대소문자 무시). `client`: APP·ADMIN·WEB, 그 밖은 400 `VALIDATION_FAILED`(`client`) |
| GET | `/users/{id}/sessions` | 200 `{ sessions: AdminSession[], events: LoginEvent[] }` — 그 회원의 살아 있는 세션(최신순) + 최근 로그인 이력 20개(최신순). 없는 회원 404 |
| DELETE | `/sessions/{sessionId}` | 204. 그 세션만 끊음(FORCED_LOGOUT). 자기 현재 세션은 400(`sessionId`), 없거나 이미 끊긴 세션 404 |
| DELETE | `/users/{id}/sessions` | 204. 그 회원의 모든 세션 끊음(세션마다 FORCED_LOGOUT). 자기 자신은 400(`id`), 없는 회원 404. 세션이 없어도 204 |

```ts
interface AdminSession {
  sessionId: string;
  userId: string;
  email: string | null;          // DB의 회원 정보 (탈퇴했으면 null)
  name: string | null;
  role: 'USER' | 'ADMIN' | null;
  client: 'APP' | 'ADMIN' | 'WEB';
  userAgent: string | null;
  ip: string | null;
  createdAt: string;             // 로그인 시각 (ISO-8601 UTC)
  lastRefreshAt: string | null;  // 갱신한 적 없으면 null
  lastSeenAt: string;
  current: boolean;              // 요청한 관리자 자신의 세션 (Access 토큰 sid)
}
interface LoginEvent {
  type: 'LOGIN' | 'LOGOUT' | 'REFRESH_REUSED' | 'FORCED_LOGOUT';
  sessionId: string | null;
  client: 'APP' | 'ADMIN' | 'WEB' | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
}
```

- 변경 이력(`tb_admin_audit_log`): 세션 하나 끊기 `SESSION_REVOKE`(targetType **`SESSION`**, targetId = 세션 ID, before = `{ userId, email, client, ip, createdAt, lastSeenAt }`, after = null). 회원 전체 끊기 `USER_SESSIONS_REVOKE`(targetType **`USER`**, targetId = 회원 ID, before = `{ email, sessionCount }`, after = `{ sessionCount: 0 }`). `GET /admin/audit-logs?targetType=`의 허용 값에 `SESSION`을 더했다.
- 대시보드에 `activeSessions`(살아 있는 세션 수), `activeUsers24h`(살아 있는 세션 중 lastSeenAt이 24시간 안인 회원 수, lastSeenAt은 1분 단위로만 갱신) — 둘 다 숫자.
- 회원 정지 때 그 회원의 세션을 모두 끊는다(FORCED_LOGOUT 기록). 정지를 풀어도 예전 토큰은 살아나지 않는다 — 다시 로그인해야 한다.
- 회원 탈퇴 때 그 회원의 세션을 모두 지운다(user 컨텍스트의 `UserDeletedListener`를 auth가 구현).
- 목록 읽기: 검색어·기기 필터가 없으면 `wearly:sessions`에서 그 쪽만(`ZREVRANGE`) 읽고 `ZCARD`가 total. 필터가 있으면 살아 있는 세션 전체를 읽어 회원 정보와 합친 뒤 거른다(세션이 수만 개를 넘으면 회원별·기기별 인덱스가 필요).

## 5. 관리자 사이트

- 메뉴 "로그인 사용자": 세션 표(회원, 역할, 기기 종류 아이콘, 기기 요약(User-Agent에서 iOS·Android·Chrome 등), IP, 로그인 시각, 마지막 활동 "3분 전"), 검색·기기 필터, 30초마다 새로 고침.
- 행마다 "로그아웃 시키기", 회원 이름 옆 "모든 기기에서 로그아웃". 확인 대화상자 후 실행, 결과 토스트. 내 현재 세션은 "현재 세션" 배지와 버튼 비활성.
- 회원 화면의 회원 상세(또는 행 펼치기)에 그 회원의 세션과 최근 로그인 이력.
- 모든 요청에 `X-Wearly-Client: admin`.

## 6. 앱

- 요청 헤더 `X-Wearly-Client: app`.
- 강제 로그아웃되면 다음 API 요청이 401 `TOKEN_INVALID` → 이미 있는 "로그인이 풀렸어요" 흐름으로 로그인 화면. 문구: "다른 곳에서 로그아웃되었어요. 다시 로그인해 주세요."

## 7. Valkey 직접 보기

`docker exec -it wearly-valkey valkey-cli` (하네스는 `-n 1`). 명령 예시는 [14_DB_명령.md](14_DB_명령.md) "Valkey" 장.
