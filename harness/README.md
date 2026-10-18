# 연동 하네스 (`harness/`)

앱의 API 모듈(`mobile/src/api/`)과 백엔드(`backend/`)를 **실제로 연결해** 계약(`doc/05_백엔드_API.md`)대로 동작하는지 확인하는 도구다.

## 왜 따로 두나

- 앱과 백엔드를 서로 다른 곳에서 만들고 있어서, 각자의 단위 테스트가 통과해도 둘이 맞물리는지는 알 수 없다.
- 하네스는 앱이 쓰는 코드(`mobile/src/api/client.ts`의 `createApiClient`, `tokenStore.ts`의 `createMemoryTokenStore`)를 **그대로 import해** Node에서 서버를 부른다. 그러니 PASS는 "앱이 이 서버와 이렇게 동작한다"는 뜻이다.
- 오류 본문 형식, 401 코드처럼 앱 모듈이 감춰 버리는 부분은 원시 `fetch`로 따로 확인한다.
- 토큰 만료·자동 갱신·AI 한도는 평소 설정(30분, 하루 10회)으로는 확인하기 어렵다. 그래서 Access 수명 3초, AI 한도 3회로 백엔드를 따로 띄운다 (계약 9장).

## 실행

```bash
cd harness && npm install     # 처음 한 번
./run.sh
```

`./run.sh`가 하는 일:

1. DB 컨테이너(`wearly-postgres`)가 꺼져 있으면 `docker compose up -d`로 켜고 healthy가 될 때까지 기다린다.
2. 하네스 전용 DB `wearly_harness`를 지우고 새로 만든다. 개발용 `wearly` DB는 건드리지 않는다.
   - 로그인 세션 저장소 Valkey(`wearly-valkey`, 호스트 6380, doc/16)가 꺼져 있으면 `docker compose up -d valkey`로 켜고, 하네스용 **DB 1만** `FLUSHDB`로 비운다. 개발 서버가 쓰는 DB 0은 건드리지 않는다.
   - 옷 사진 저장소 MinIO(`wearly-minio`, 호스트 9000, doc/18)가 꺼져 있으면 `docker compose up -d minio minio-init`으로 켜고, **하네스 전용 버킷 `wearly-images-harness`**를 지우고 비공개로 다시 만든다(`docker exec wearly-minio`의 `mc`). 개발 서버가 쓰는 `wearly-images`는 건드리지 않는다.
3. `../backend`를 `./gradlew bootRun`으로 포트 **18080**에 띄운다. 로그는 `harness/.logs/backend.log`.
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/wearly_harness`, `SERVER_PORT=18080`
   - `SPRING_DATA_REDIS_HOST=localhost`, `SPRING_DATA_REDIS_PORT=6380`, `SPRING_DATA_REDIS_DATABASE=1`
   - `WEARLY_STORAGE_ENDPOINT=http://localhost:9000`, `WEARLY_STORAGE_BUCKET=wearly-images-harness`, 계정은 compose의 로컬 전용 값, `WEARLY_STORAGE_PUBLIC_ENDPOINT=`(비움: 서명 URL이 요청 Host 이름 + 9000), `WEARLY_STORAGE_AUTO_CREATE_BUCKET=true`, `WEARLY_STORAGE_CLEANUP_ENABLED=false`(정리 작업 끔)
   - `WEARLY_ACCESS_TTL=PT3S`, `WEARLY_AI_DAILY_LIMIT=3`, `WEARLY_AI_PROVIDER=mock`
   - `WEARLY_WEATHER_PROVIDER=mock`(날씨·평년값·장소 검색을 키 없이 목업으로)
   - 하네스 관리자(`WEARLY_ADMIN_EMAIL`·`WEARLY_ADMIN_PASSWORD`·`WEARLY_ADMIN_NAME`)와 `WEARLY_ADMIN_MFA_REQUIRED=false`(관리자 2단계 인증 필수를 끈다: 하네스 관리자는 비밀번호만으로 로그인하고, 시나리오 45만 스스로 등록한 관리자로 TOTP 흐름을 확인한다, doc/22), 고정 마스터 키 `WEARLY_SECRET_KEY`(TOTP 비밀 암호화에 필요, 환경변수로 덮을 수 있다)
   - `JAVA_HOME`은 JDK 21. `WEARLY_JWT_SECRET`이 없으면 하네스용 값을 넣고, `ANTHROPIC_API_KEY`는 비운다.
4. `/actuator/health`가 UP이 될 때까지 최대 180초 기다린다. 그 안에 뜨지 않거나 프로세스가 죽으면 로그 끝부분을 보여 주고 실패로 끝난다.
5. 시나리오를 실행하고 결과 표를 출력한다.
6. 끝나면(성공·실패·Ctrl+C 모두) 백엔드 프로세스 그룹과 18080 포트를 쓰는 프로세스를 끈다.

`backend/`가 아직 없으면 안내 문구를 보여 주고 끝난다.

### 옵션

| 옵션 | 설명 |
|---|---|
| `--keep` | 끝나도 백엔드를 남긴다. 끄는 명령을 출력해 준다. |
| `--external <url>` | DB 리셋·백엔드 기동 없이 이미 떠 있는 서버로 시나리오만 돌린다. Access 수명 3초에 기대는 시나리오 3개는 건너뛴다(SKIP). AI 한도 시나리오는 응답의 `remainingToday`를 따라가므로 그대로 돈다. |
| `--only <키워드,...>` | 분류나 이름에 키워드가 들어간 시나리오만 실행한다. 예: `--only 토큰,AI` |
| `--list` | 시나리오 목록만 출력한다. |
| `--db-only` | `wearly_harness` DB를 새로 만들고 Valkey DB 1과 하네스 사진 버킷을 비우고 끝낸다. |

| 환경변수 | 기본값 |
|---|---|
| `HARNESS_JAVA_HOME` | `/Users/showzone/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home` (없으면 `/usr/libexec/java_home -v 21`) |
| `HARNESS_BOOT_TIMEOUT` | `180` (초). 첫 실행에서 Gradle 내려받기가 길면 늘린다. |
| `HARNESS_BACKEND_DIR` | 저장소 루트의 `backend/` |

자주 쓰는 조합:

```bash
./run.sh --keep                               # 돌리고 서버는 남겨 두기
./run.sh --external http://localhost:18080    # 남겨 둔 서버로 다시 (빠름)
./run.sh --external http://localhost:8080     # 개발 중인 서버(평소 설정)에 대고 확인
npm run typecheck                             # 하네스 + mobile/src/api 타입 검사
```

## 시나리오

시나리오마다 무작위 이메일(`h-<태그>-<uuid>@harness.wearly.test`)로 새 사용자를 만들어 서로 영향을 주지 않는다.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 1 | 기본 | `/actuator/health` → `UP` |
| 2 | 기본 | 토큰 없이 보호 API → 401 `UNAUTHORIZED`, 오류 JSON 형식(`status`·`code`·`params?`·`fieldErrors[]`·`traceId`, `message` 없음, 본문 `traceId` = 응답 헤더 `X-Request-Id`), 형식이 틀린 토큰 → `TOKEN_INVALID`, 앱 모듈의 `ApiError.traceId`와 `message`(= `code`) |
| 3 | 기본 | CORS: Expo 웹 출처 3곳은 허용, 다른 출처는 거부 |
| 4 | 인증 | 회원가입 → 저장소에 토큰 저장, `users.me()` 일치, 이메일 소문자 저장, `AuthResponse` 모양(만료 초, Refresh는 base64url 43자) |
| 5 | 인증 | 같은 이메일을 대소문자만 바꿔 재가입 → 409 `EMAIL_TAKEN`, 저장소 비어 있음 |
| 6 | 인증 | 비밀번호(숫자 없음·영문 없음·8자 미만·64자 초과), 이름(빈 값·20자 초과), 이메일 형식 → 400 `VALIDATION_FAILED` + 해당 `fieldErrors`(`field`·`code`), 앱 모듈 `ApiError.fieldErrors`에도 코드가 담김 |
| 7 | 인증 | 로그인 성공(이메일 대소문자 무관), 틀린 비밀번호·없는 이메일 → 401 `INVALID_CREDENTIALS` |
| 8 | 프로필 | 처음 GET → 앱 `DEFAULT_PROFILE`과 같은 기본값 + `onboarded: false`, PUT 응답·GET 일치, `name`이 `users.me()`에 반영, 빈 이름 → 400 |
| 9 | 프로필 | 나의 정보(연령대·체형·키·핏 선호, 이슈 #5): 저장 응답·GET 일치, `null`로 보내면 비우기, 코드 검증(AGE_RANGE·BODY_TYPE·FIT), 키 범위(130~210) 오류 |
| 10 | 옷장 | create → get → PATCH 일부 필드(나머지 유지, `brand: null`로 지우기) → remove → get 404 |
| 11 | 옷장 | 목록 필터 `category`·`status`·`color`·`q`(이름·브랜드 부분 일치)·조합, 정렬 `recent`(기본)·`worn`·`name`, 없는 category 값 → 400 |
| 12 | 옷장 | 잘못된 category·subType 짝 → 400 (등록, PATCH로 category만 바꾸기), formality 4·빈 이름 → 400 |
| 13 | 옷장 | `bulkCreate` 응답이 요청 순서대로, 101개 → 400, 중간에 틀린 항목이 있으면 하나도 저장 안 됨 |
| 14 | 옷장 | `stats`: total, byCategory(0 포함), byStatus, byColor·byStyle(많은 순), wornLast30Days, utilization30d(소수 둘째 자리), sleeping, 삭제·추가 뒤 다시 계산 |
| 15 | 옷장 | 다른 사용자의 아이템 get·PATCH·DELETE → 404, 목록·통계에 안 보임 |
| 16 | 코드 | `GET /codes`: 그룹 순서·초기값(`doc/06_코드_초기값.json`)이 모두 들어 있음, ETag 같은 값으로 다시 요청하면 304 |
| 17 | 코드 | 새 코드(원피스·청바지·데님·플로럴·페미닌)로 아이템 등록·목록 필터·통계 `byCategory.DRESS`, 모르는 코드·짝 불일치 400 |
| 18 | AI | 작은 PNG를 코드로 만들어 분석 → provider `mock`, 앱 허용 값 안의 제안(짝 포함), 같은 이미지 = 같은 결과, `remainingToday` 1씩 감소, 사용자별 한도 분리, 파트 이름 `image` |
| 19 | AI | 한도(3회)까지 쓴 뒤 → 429 `AI_LIMIT_EXCEEDED` |
| 20 | AI | 10MB 넘는 이미지 → 413 `IMAGE_TOO_LARGE`, 실패한 호출은 한도에서 빼지 않음 |
| 21 | 토큰 | refresh 회전(새 토큰 발급), 이미 회전된 토큰 재사용 → 401 `REFRESH_REUSED`, 그 뒤 최신 토큰도 무효(family 폐기), 없는 토큰 → `REFRESH_INVALID` |
| 22 | 토큰 | 4.5초 기다려 Access 만료 → 서버는 `TOKEN_EXPIRED`, 앱 모듈은 자동 갱신 후 성공, 저장소의 access·refresh가 모두 바뀜, refresh 1번 |
| 23 | 토큰 | 만료 상태에서 요청 5개를 동시에 → 모두 성공, `/auth/refresh`는 **1번**, `tokenStore.set`도 1번 (single-flight) |
| 24 | 토큰 | 재사용 감지로 family가 폐기된 뒤 만료 → 앱 모듈이 저장소를 비우고 `onAuthLost`를 1번 부르고 원래 오류(`TOKEN_EXPIRED`)를 던짐 |
| 25 | 계정 | logout → 저장소 비움, 그 refresh로 갱신 불가, 없는 토큰 logout도 204, 다른 세션은 영향 없음 |
| 26 | 계정 | 회원 탈퇴 → 기존 access 401, refresh 401, 로그인 `INVALID_CREDENTIALS`, 같은 이메일로 재가입하면 빈 옷장·기본 프로필 |
| 27 | 관리자 | (관리자) 일반 회원은 관리자 API 403 `FORBIDDEN`, 관리자 대시보드·회원 목록 형식 |
| 28 | 관리자 | (관리자) 코드: 추가 → `/codes`에 반영 → 비활성화(새로 고르면 400, 저장된 값은 유지) → 사용 중 삭제 409 `CODE_IN_USE` → 삭제 |
| 29 | 관리자 | (관리자) AI 공급자: 목록(`keyConfigured`), 종류 설명, 추가·중복 409·삭제 |
| 31 | 관리자 | (관리자) 회원 정지: 로그인 403 `ACCOUNT_SUSPENDED`, 기존 토큰 401 → 해제 후 로그인 |

22~24는 Access 수명 3초가 필요해서 `--external`에서는 건너뛴다. 27~31(관리자)은 관리자 계정이 필요하다 (`./run.sh`는 하네스 관리자를 만들어 준다).

### 관리자 AI 공급자 모델 목록 (8차, 원시 HTTP)

계약 07 문서 4장 "모델 목록". 번호는 `--list` 기준이다. 하네스는 실제 제공사를 부르지 않는다: 값이 없는 키 이름(`HARNESS_MISSING_API_KEY`)으로 공급자를 만들어 내장 목록만 확인하고, 끝나면 공급자를 지운다.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 30 | 관리자 | (관리자) 키 없는 공급자의 `GET /admin/ai/providers/{id}/models`(`refresh=true` 포함) → 200 `source: CATALOG`·`error.code: NO_API_KEY`·문구, `fetchedAt` ISO 시각, 내장 목록이 비어 있지 않고 추천이 맨 앞·`createdAt` null·`claude-sonnet-5-5` 추천, `GET /admin/ai/types`의 `CLAUDE.models`, 일반 회원 403 `FORBIDDEN`, 없는 공급자 404 `NOT_FOUND` |

### 앱 설정 · 옷 사용 중지 (6차, 원시 HTTP)

계약 12 문서 3장·13 문서. 앱 API 모듈을 거치지 않고 `raw()`와 `src/lib/rawUser.ts`(가입·관리자 로그인, `TOKEN_EXPIRED`면 다시 로그인해 한 번 더)로만 확인한다. 번호는 `--list` 기준.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 32 | 앱 설정 | `GET /app/config` 토큰 없이(틀린 토큰도) 200, `{ themeId, seasonMode, version }`만, `ETag: "<version>"`·`Cache-Control: no-cache`, 같은 ETag 304·다른 ETag 200, PUT은 401, CORS `ETag` 노출 |
| 33 | 앱 설정 | (관리자) 일반 회원 403·토큰 없이 401, GET 형식(`themeId`·`seasonMode`, 처음엔 `updatedAt`·`updatedBy` null), 400(`dark`·빈 본문·`NYANG`) `themeId`, PUT으로 바꾸면 `updatedBy` = 관리자 이메일·`/app/config` version = `updatedAt`·옛 ETag 200·새 ETag 304, 같은 값 PUT은 그대로·이력 없음, 이력 `APP_THEME_UPDATE`(`APP_SETTING`/`theme`, before·after `{themeId}`), 원래 테마로 되돌림 |
| 34 | 앱 설정 | (관리자) 계절 모드(이슈 #6): `PUT { seasonMode }`만 보내면 테마는 그대로, `/app/config`의 `seasonMode`·ETag가 바뀌고, 틀린 값은 400(`seasonMode`), 이력 `APP_THEME_UPDATE`에는 `seasonMode`만 남는다, 원래 모드로 되돌림 |
| 35 | 옷 사용 중지 | `/codes`에 `RETIRE_REASON` 5개(라벨·attrs 없음) |
| 36 | 옷 사용 중지 | retire 응답 필드(메모 앞뒤 공백 제거·보유 상태 유지), 목록 기본·`retired=false`에서 빠지고 `true`·`all`에 보임, 모르는 `retired` 400, 다시 retire는 시각 유지, PATCH 가능, restore(사용 중이어도 200) |
| 37 | 옷 사용 중지 | 통계: total·byCategory·byStatus·byColor·활용률·sleeping은 사용 중인 옷만, `retired`·`retiredByReason`(많은 순), restore 뒤 다시 계산 |
| 38 | 옷 사용 중지 | 완전 삭제는 `retired=all`에도 없고 404, 사용 중지 옷은 남음, 남의 옷 retire·restore 404(입력이 틀려도 404 먼저), 401, 모르는 이유+메모 201자 400(`reason`·`memo`), 이유 없음 400, 메모 200자는 됨 |
| 39 | 옷 사용 중지 | (관리자) 새 이유 코드로 retire → 사용 중 코드 삭제 409 `CODE_IN_USE` → 비활성화하면 retire 400(`reason`), 회원 목록 `itemCount`·`retiredItemCount`, 정리 |
| 40 | 옷 사용 중지 | AI 코디 추천(목업): 후보에 사용 중지 옷 없음, 고정·중심으로 주면 400("사용하지 않는 옷이에요.") |

### 로그인 세션 (doc/16, 원시 HTTP)

Refresh 토큰·세션이 Valkey로 옮겨 가면서 Access 토큰에 `sid`가 들어가고, 서버가 매 요청 세션이 살아 있는지 본다. 하네스 서버는 Access 수명이 3초라서 "끊은 뒤 다음 요청이 401"은 끊기 직전에 새로 로그인한 토큰으로 확인한다(만료가 먼저 오면 `TOKEN_EXPIRED`).

| # | 분류 | 확인하는 것 |
|---|---|---|
| 41 | 로그인 세션 | (관리자) `GET /admin/sessions?q=` 항목 키 12개, `X-Wearly-Client`·`X-Forwarded-For` 첫 값·User-Agent가 세션에 남음, 헤더 없는 Node fetch는 `APP`, lastSeenAt 최신순, `client=WEB` 필터·`TV` 400, 내 세션만 `current`, `GET /admin/users/{id}/sessions`(세션 + 이력 `LOGIN`), 대시보드 `activeSessions`·`activeUsers24h` |
| 42 | 로그인 세션 | (관리자) `DELETE /admin/sessions/{sid}` 204 → 그 기기 다음 요청 401 `TOKEN_INVALID`, refresh 401 `REFRESH_INVALID`, 다른 기기 refresh는 200, 이미 끊긴 세션 404, 내 현재 세션 400(`sessionId`), 이력 `SESSION_REVOKE`·로그인 이력 `FORCED_LOGOUT` |
| 43 | 로그인 세션 | (관리자) `DELETE /admin/users/{id}/sessions` 204 → 모든 기기 401·refresh 실패, 목록에서 사라짐, 다시 로그인은 됨, 자기 자신 400, 없는 회원 404, 이력 `USER_SESSIONS_REVOKE`(`before.sessionCount`) |
| 44 | 로그인 세션 | 로그아웃하면 그 세션의 Access 토큰도 바로 401 `TOKEN_INVALID`, 다른 세션은 그대로, 일반 회원의 세션 API 403 |

refresh 횟수는 `createApiClient`의 `fetch` 주입 옵션으로 세션마다 요청을 기록해 센다. `tokenStore`도 감싸서 `set`·`clear` 횟수를 센다.

### 관리자 2단계 인증 (doc/22, 원시 HTTP)

계약 22 문서 4.1. 하네스 백엔드는 `WEARLY_ADMIN_MFA_REQUIRED=false`와 고정 마스터 키(`WEARLY_SECRET_KEY`, 하네스 전용 값)로 뜨므로 하네스 관리자와 다른 시나리오는 비밀번호만으로 들어오고, 이 시나리오만 스스로 등록한 관리자로 확인한다. 가짜 인증 앱은 하네스 안의 TOTP 계산 함수다(`src/scenarios/mfa.ts`). 번호는 `--list` 기준.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 45 | 관리자 | (관리자) 하네스 관리자가 새 회원을 관리자로 지정 → 서버 필수가 꺼져 있고 등록 전이라 관리자 사이트 로그인이 바로 토큰 → `GET /admin/me/mfa`(`enrolled` false·`required` false) → setup(`secret`·`otpauthUrl`) → 코드 형식 오류 400 `VALIDATION_FAILED` → enable로 등록(복구 코드 10개) → 다시 setup은 409 `MFA_ALREADY_ENROLLED` → 관리자 사이트 로그인이 대기 토큰(`mfa.required`·`enrolled` true, `mfaTokenExpiresIn` 300), 대기 토큰으로 관리자 API는 401 `TOKEN_INVALID` → 같은 코드 재사용 400 `MFA_CODE_INVALID`(`remainingAttempts` 4) → 복구 코드로 확인 200(`recoveryCodes` null·`recoveryCodesLeft` 9) → 확인한 세션은 관리자 API 200 → **앱 로그인 세션**은 응답 모양 그대로지만 관리자 API는 403 `MFA_REQUIRED`(앱 API는 200) → `GET /admin/auth-events`와 모르는 `type` 400 → 하네스 관리자의 `DELETE /admin/users/{id}/mfa` 204로 초기화 → 역할 해제로 정리 |

### 옷 사진 저장 (doc/18, 원시 HTTP)

계약 18 문서. 앱 API 모듈을 거치지 않고 `raw()`와 `src/lib/rawUser.ts`로 확인하고, 서명 URL로 저장소(MinIO)에 **실제로 PUT·GET**한다. 하네스 백엔드는 버킷 `wearly-images-harness`를 쓴다. 번호는 `--list` 기준(코드: `src/scenarios/images.ts`).

| # | 분류 | 확인하는 것 |
|---|---|---|
| 46 | 사진 저장 | upload-url 응답 키 5개·UUID `imageId`·`method` PUT·`headers` = `Content-Type`·서명 URL 호스트가 요청 Host 이름 → 올리기 전 complete는 400 `IMAGE_NOT_UPLOADED` → 서명 URL로 PUT 200 → complete(키 6개, 가로·세로) → `url`은 JPEG로 다시 인코딩·`thumbUrl` GET 200·서명 없는 URL 403 → `imageId`로 아이템 등록 201(`image` 키 4개)·목록에도 `image`·사진 없는 아이템 `image: null` → PATCH `imageId: null`로 빼면 `image: null`이고 예전 객체는 404 |
| 47 | 사진 저장 | 글자 파일·가짜 JPEG는 PUT은 되지만 complete에서 400 `IMAGE_INVALID`(`params.reason` 글자 파일 `FORMAT`·가짜 JPEG `DECODE`, 실패한 이미지를 다시 complete하면 404), GIF·10MB 초과 upload-url은 400 `VALIDATION_FAILED`(`contentType` `NOT_ALLOWED{allowed}`·`size` `OUT_OF_RANGE{min, max}`) |
| 48 | 사진 저장 | 남의 이미지는 complete·아이템 등록·AI 분석 모두 404, 내 `imageId`로 AI 분석 200(`provider` mock), PENDING 이미지로 등록하면 400 `IMAGE_NOT_UPLOADED` |
| 49 | 사진 저장 | 다른 아이템에 붙은 이미지로 등록하면 409 `IMAGE_IN_USE`, PATCH로 사진을 바꾸면 새 사진이 붙고 예전 사진 객체는 404 |

### 장소별 날씨 (doc/19, 원시 HTTP)

계약 17·19 문서. 앱 API 모듈을 거치지 않고 `raw()`와 `src/lib/rawUser.ts`로 확인한다(코드: `src/scenarios/weather.ts`). 하네스 백엔드는 `WEARLY_WEATHER_PROVIDER=mock`이라 예보·평년값·장소 검색이 모두 키 없이 목업으로 돈다(실제 Open-Meteo는 부르지 않는다). 번호는 `--list` 기준.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 50 | 장소 날씨 | (관리자) `GET /weather/day` region 방식: 서울을 지금 실행으로 받은 뒤 토큰 없이 200, 응답 키 7개, 오늘은 `FORECAST`(`place`·`day` 키·`cond`·`forecastUntil`·`fetchedAt` ISO·`climate` null), 40일 뒤는 `CLIMATE`(`cond`·`condText` null, 최저 < 최고, `climate.years`, `rainProb` = `rainDayRatio` × 100), `forecastUntil` 같음, 틀린 토큰도 200 |
| 51 | 장소 날씨 | 좌표 방식은 토큰 없이 401 `UNAUTHORIZED`(좌표가 틀려도 로그인이 먼저), `/weather/places`도 401. 로그인하면 격자 좌표(소수 첫째 자리)로 오늘 `FORECAST`·60일 뒤 `CLIMATE`(`place.regionCode` null), 검색 응답 키(`items`, 항목 키 7개 — `parentRegionCode` 포함), 목업 검색 도쿄는 `regionCode` null·부산은 `BUSAN`, 해운대·중구는 시·군·구 결과(`regionCode` null, 상위 시·도 `parentRegionCode`), 빈 검색어 400(`q`). `--external`이면 401만 확인 |
| 52 | 장소 날씨 | 날짜 검증: 없음(`REQUIRED`)·없는 달·형식(`INVALID_FORMAT`)·어제·1년 넘음(`OUT_OF_RANGE`) 모두 400 `VALIDATION_FAILED`(`date`), 없는 지역 400(`region` `NOT_FOUND`), 빈 검색어는 `q` `REQUIRED` |
| 53 | 장소 날씨 | (관리자) `GET /admin/weather/calls?callKind=` 필터(대소문자 무관), 틀린 값 400 `NOT_ALLOWED`(`params.allowed` = `FORECAST`·`ON_DEMAND`·`SEARCH`·`CLIMATE`), `calls/summary`의 `auxiliaryMonths`(`OPEN_METEO`·`OPEN_METEO_GEO`) (필요: admin) |

### 예정 코디 (doc/21, 원시 HTTP)

계약 21 문서. 앱 API 모듈을 거치지 않고 `raw()`와 `src/lib/rawUser.ts`로 `/plans`를 확인한다(코드: `src/scenarios/plans.ts`). 번호는 `--list` 기준. 앱 API 모듈의 `api.plans`는 이 시나리오에서 쓰지 않는다.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 54 | 예정 코디 | 토큰 없이 목록 401 → 등록 201(응답 키 17개, 숫자 문자열 `id`, 메모 공백 제거, `weatherCheckedAt` UTC, 보낸 코디 그대로, 기기 아이템 id는 `missingItemIds`) → 목록(날짜 → 시간대 순, `from`/`to` 양 끝 포함) → PATCH(`null`은 비우고 안 보낸 필드는 그대로) → DELETE 204 → 다시 삭제·PATCH는 404 |
| 55 | 예정 코디 | bulk: 같은 `clientId`의 POST는 200·같은 id, bulk는 요청 순서대로 만들거나 고침(`created`/`updated`), 다시 올려도 늘지 않음, `clientId` 중복(`items[1].clientId` `DUPLICATE`)·없음(`REQUIRED`)·101개(`items` `TOO_MANY`)·틀린 항목(`items[1].occasion` `NOT_ALLOWED`)은 400이고 하나도 저장하지 않음 |
| 56 | 예정 코디 | 입력 검증의 필드 오류 코드 한꺼번에(`clientId` `INVALID_FORMAT`, `date` `OUT_OF_RANGE{min, max}` = 오늘 앞뒤 1년, `timeOfDay`·`concepts[1]`·`place.regionCode`·`outfit.items.HAT` `NOT_ALLOWED`, `place.lat` `OUT_OF_RANGE`), `weather` 필수, 1년 안 지난 날짜는 저장, 지운 옷은 `missingItemIds`에 나오고 코디는 그대로, 남의 예정 PATCH·DELETE는 입력이 틀려도 404 |

### 푸시 토큰 (doc/23, 원시 HTTP)

계약 23 문서 4장. 하네스 서버는 `wearly.notify.push.enabled=false`(기본)라 `serverPush`는 늘 `false`다. 실제 Expo Push 호출과 D-1 작업은 백엔드 자동 테스트가 본다. 번호는 `--list` 기준(코드: `src/scenarios/push.ts`).

| # | 분류 | 확인하는 것 |
|---|---|---|
| 57 | 푸시 토큰 | 토큰 없이 401 `UNAUTHORIZED` → 등록 200(키 4개 `deviceId`·`platform`·`serverPush`·`dayBeforeTime`, `serverPush` false, `dayBeforeTime` `HH:mm`) → 같은 기기 다시 등록 200(플랫폼이 바뀜) → 검증 400(`token`·`platform`·`deviceId`) → 삭제 204(없어도 204, `deviceId` 없으면 400) |

### 계약에 정해지지 않아 하네스가 고른 기대값

계약 문서에 명시되지 않은 부분은 다음처럼 기대한다. 백엔드와 다르면 둘 중 하나를 고치고 계약 문서에도 적는다.

- `sort=worn`은 `wornCount` 많은 순이다 (앱 옷장 화면의 '많이 입은 순'과 같음).
- PATCH에서 `brand: null`은 브랜드를 지운다는 뜻이다 (필드를 빼면 유지).
- 일괄 등록은 전부 저장되거나 하나도 저장되지 않는다.
- 저장 전 프로필의 `name`은 가입 이름이나 `'웨어리 사용자'` 둘 다 허용한다. 실제 값은 '참고'에 남긴다.
- family가 폐기된 뒤 최신 refresh 토큰의 오류 코드는 `REFRESH_INVALID`, `REFRESH_REUSED` 둘 다 허용한다. 실제 값은 '참고'에 남긴다.

## 결과 읽는 법

```
#  | 결과 | 분류 | 시나리오                                  | 시간
---+------+------+-------------------------------------------+-----
 1 | PASS | 기본 | 헬스 체크 /actuator/health → UP           |  2ms
13 | FAIL | 옷장 | stats 수치 (...)                          | 35ms
19 | SKIP | 토큰 | Access 만료(4.5초 대기) → 자동 갱신 ...   |    -

실패 상세
✗ #13 [옷장] stats 수치 (...)
  검사: utilization30d = 2/6 → 0.33
  기대: 0.33
  실제: 0.3333333333333333

통과 21 · 실패 1 · 건너뜀 1 · 총 14.2s
```

- **PASS / FAIL / SKIP**: 시나리오 하나 안의 검사가 모두 통과하면 PASS다. 하나라도 틀리면 거기서 멈추고 FAIL이 된다.
- **실패 상세**: 어떤 검사가 틀렸는지(`검사`)와 기대값·실제값. 앱 모듈이 던진 오류는 `ApiError 401 TOKEN_EXPIRED: …` 형식으로 보인다. 예상하지 못한 예외는 스택 일부를 함께 보여 준다.
- **건너뜀**: 왜 건너뛰었는지. 서버에 연결할 수 없으면 모든 시나리오를 건너뛰고 실패로 끝난다.
- **참고**: 계약이 여러 값을 허용하는 곳에서 서버가 실제로 준 값.
- **종료 코드**: 실패가 있거나 서버에 닿지 못하면 1, 아니면 0.
- 백엔드 쪽 원인은 `harness/.logs/backend.log`에서 찾는다.

## 파일

| 파일 | 내용 |
|---|---|
| `run.sh` | DB·Valkey·MinIO(하네스 버킷) 준비 → 백엔드 기동·종료 → 러너 실행 |
| `src/main.ts` | 러너. 인자 해석, 시나리오 실행, 결과 표 |
| `src/scenarios/*.ts` | 분류별 시나리오 (`auth`·`profile`·`wardrobe`·`codes`·`ai`·`tokens`·`account`·`admin`·`appSettings`·`retire`·`sessions`·`images`·`weather`·`plans`·`mfa`·`push`). 전체 57개(`./run.sh --list`) |
| `src/lib/session.ts` | 앱 모듈로 클라이언트 만들기, 요청·토큰 저장·`onAuthLost` 기록 |
| `src/lib/http.ts` | 원시 요청과 오류 본문 형식 검사(`checkErrorBody`: `message` 없음, `traceId` = `X-Request-Id`, `fieldErrors` 항목은 `{ field, code, params? }`) |
| `src/lib/rawUser.ts` | 앱 모듈 없이 가입·관리자 로그인한 세션 (`TOKEN_EXPIRED`면 다시 로그인) |
| `src/lib/assert.ts` | 검사 함수, 기대값·실제값을 담는 실패 |
| `src/lib/fixtures.ts` | 아이템 입력, 기본 프로필 사본, 서울 기준 날짜 |
| `src/lib/png.ts` | 단색 PNG 생성 |

`tsconfig.json`은 `../mobile/src/api`도 함께 타입 검사한다. 앱 API 모듈은 계약 8장에 따라 `react-native`·`expo-*`·`@/` 없이 순수 TypeScript여야 한다. 이 규칙이 깨지면 `npm run typecheck`가 먼저 실패한다.

앱 `DEFAULT_PROFILE`(`mobile/src/data/seed.ts`)은 `@/` 별칭을 써서 Node에서 바로 가져올 수 없다. 그래서 `src/lib/fixtures.ts`에 사본을 두었다. 앱 기본값을 바꾸면 이 사본도 같이 고친다.
