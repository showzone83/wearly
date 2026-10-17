# Wearly 백엔드

웨어리 앱과 관리자 사이트의 API 서버. 계약 문서:

- [`doc/05_백엔드_API.md`](../doc/05_백엔드_API.md) — 1차: 토큰 로그인 · 옷장 · AI 분석
- [`doc/06_코드_관리.md`](../doc/06_코드_관리.md) — 2차: 옷 속성 값을 DB 코드 테이블로 (`GET /codes`, 코드 검증)
- [`doc/07_관리자_사이트.md`](../doc/07_관리자_사이트.md) — 2차: 관리자 역할·관리자 API·AI 공급자(Claude·OpenAI·Gemini) 관리, 3차: 6장 AI 키 웹 입력(쓰기 전용 · 암호화 저장)
- [`doc/08_백엔드_구조.md`](../doc/08_백엔드_구조.md) — 3차: 헥사고날 패키지 구조와 의존 규칙 (ArchUnit으로 검사)

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.1 (Java 21 toolchain, Gradle 9.8.1 wrapper, Kotlin DSL) |
| 데이터 접근 | Spring Data JPA (Hibernate 7.4) + MyBatis (mybatis-spring-boot-starter 4.1.0) |
| DB | PostgreSQL 17 + pgvector, 스키마는 Flyway (`src/main/resources/db/migration`, V1~V4) |
| 인증 | Spring Security OAuth2 Resource Server, JWT HS256 (Nimbus) |
| AI | Claude: Anthropic Java SDK 2.73.0 / OpenAI·Gemini: 각 사 REST API를 Spring `RestClient`로 (SDK 없음) |
| 구조 | 헥사고날 (컨텍스트별 domain · application · adapter), 규칙은 ArchUnit 1.5.1 테스트 |
| 키 암호화 | AES-256-GCM (JDK `javax.crypto`), 마스터 키 `WEARLY_SECRET_KEY` |

## 실행

```bash
# 1) 저장소 루트에서 DB 띄우기 (localhost:5433, DB·계정·비밀번호 wearly)
docker compose up -d

# 2) 서버 실행 (backend/ 에서)
cd backend
cp .env.example .env   # 처음 한 번. 키와 관리자 계정을 채운다
./gradlew bootRun      # 관리자 화면의 AI 공급자 설정(DB)대로 사진 분석
./gradlew bootRunMock  # 목업 공급자로 사진 분석 (키 불필요, WEARLY_AI_PROVIDER=mock)
```

- 서버: `http://localhost:8080`, API 기본 경로 `/api/v1`, 상태 확인 `GET /actuator/health`
- 처음 뜰 때 Flyway가 `V1__init.sql` → `V2__codes.sql`(코드 테이블 + 초기값 102개) → `V3__admin_ai.sql`(역할·AI 공급자·변경 이력) → `V4__ai_provider_secret.sql`(웹 입력 AI 키 암호문)을 적용한다. 1차 DB에 있던 데이터는 그대로 쓴다 (기존 값은 모두 새 코드에 있다). JPA는 `ddl-auto: validate`.
- **AI 공급자 고르기** (07 문서 3장)
  - `bootRunMock`(`WEARLY_AI_PROVIDER=mock`)이면 언제나 목업. DB 설정은 무시한다.
  - 그 밖(`bootRun`, 기본 `db`)에는 `tb_ai_provider`에서 사용(`enabled`)·모델·키를 모두 갖춘 공급자를 `priority` 순으로 시도하고, 실패(시간 초과·HTTP 오류·응답 검증 실패)하면 다음 공급자로 넘어간다.
  - 키는 **웹에서 넣은 키(DB) → `api_key_env` 환경변수 → 없음** 순서로 고른다 (아래 "AI 키 관리").
  - 쓸 수 있는 공급자가 없어도 서버는 **뜬다**. 시작 로그에 WARN이 남고, 사진 분석은 502 `AI_UNAVAILABLE`이다 (목업으로 대신하지 않는다). 관리자 사이트에서 고칠 수 있게 하려는 것이다.
  - 시작 직후 로그: `AI 공급자 (시도 순서): claude(CLAUDE, model=…, priority=1, key=ENV), …` 또는 `쓸 수 있는 AI 공급자가 없습니다 …` 또는 `AI 공급자: mock (목업 …)`.
  - 처음 상태: `claude`(사용, `claude-opus-5-5`, 키 `ANTHROPIC_API_KEY`), `openai`·`gemini`(꺼짐, 모델 비움). 그래서 `.env`에 `ANTHROPIC_API_KEY`만 넣으면 1차와 같이 Claude로 돈다.
- **관리자 계정**: `WEARLY_ADMIN_EMAIL`·`WEARLY_ADMIN_PASSWORD`가 있으면 시작할 때 그 계정을 만들거나(이름 `WEARLY_ADMIN_NAME`, 기본 "관리자") 이미 있는 계정을 `ADMIN`으로 올린다. 이미 있는 계정의 비밀번호·이름은 바꾸지 않는다. 가입 API로는 관리자를 만들 수 없다.
- 운영 프로필(`SPRING_PROFILES_ACTIVE=prod`)은 `WEARLY_JWT_SECRET`이 없으면 기동하지 않는다.

실행 가능한 jar: `./gradlew bootJar` → `build/libs/wearly-backend-0.0.1-SNAPSHOT.jar`

## 환경변수

모든 값은 환경변수나 `backend/.env`로 덮어쓴다. `.env`는 git에 올리지 않는다 (`.env.example`만 커밋).
`.env`는 `bootRun`/`java -jar`를 **`backend/` 디렉터리에서** 실행할 때 읽힌다 (`spring.config.import=optional:file:.env[.properties]`).

| 이름 | 기본값 | 설명 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/wearly` | DB 주소 |
| `SPRING_DATASOURCE_USERNAME` | `wearly` | |
| `SPRING_DATASOURCE_PASSWORD` | `wearly` | |
| `SERVER_PORT` | `8080` | |
| `WEARLY_JWT_SECRET` | 개발용 고정값 | HS256 키, 32바이트 이상. prod 프로필에서는 필수 |
| `WEARLY_ACCESS_TTL` | `PT30M` | Access 토큰 수명 (ISO-8601 Duration) |
| `WEARLY_REFRESH_TTL` | `P30D` | Refresh 토큰 수명 |
| `WEARLY_AI_PROVIDER` | `db` | `db`(관리자 화면 설정) · `mock`. 1차 값 `claude`는 `db`와 같다. 그 밖의 값(예전 `auto` 포함)은 시작 실패 |
| `WEARLY_AI_DAILY_LIMIT` | `10` | 회원별 하루 사진 분석 횟수 (Asia/Seoul 날짜, 성공만, 관리자 시험 분석 제외) |
| `WEARLY_AI_TIMEOUT` | `PT60S` | 공급자 호출 제한 시간 기본값. 공급자 `options.timeoutSeconds`가 있으면 그 값 |
| `WEARLY_AI_ALLOW_BASE_URL` | `false` | `true`일 때만 공급자 `options.baseUrl`(API 주소 바꾸기)을 허용. 키가 다른 주소로 갈 수 있어 운영에서는 끈다 |
| `ANTHROPIC_API_KEY` | (없음) | Claude 키. 처음 상태의 `claude` 행이 가리키는 이름 |
| `OPENAI_API_KEY` | (없음) | OpenAI 키. `openai` 행이 가리키는 이름 |
| `GEMINI_API_KEY` | (없음) | Gemini 키. `gemini` 행이 가리키는 이름 |
| `WEARLY_ADMIN_EMAIL` / `WEARLY_ADMIN_PASSWORD` | (없음) | 둘 다 있으면 시작 시 관리자 계정 생성·승격 |
| `WEARLY_ADMIN_NAME` | `관리자` | 새로 만들 때의 이름 |
| `WEARLY_SECRET_KEY` | (없음) | 웹에서 넣은 AI 키를 암호화하는 마스터 키. **base64로 인코딩한 32바이트** (`openssl rand -base64 32`). 없으면 웹 키 입력이 꺼진다 |

- 1차의 `WEARLY_AI_MODEL`·`WEARLY_AI_EFFORT`는 없어졌다. 모델·effort는 관리자 화면(`tb_ai_provider.model`·`options.effort`)에서 바꾼다.
- 공급자 행의 `api_key_env`는 **키가 들어 있는 환경변수 이름**이다. 다른 서버 비밀(DB 비밀번호·JWT 키)이 공급자에게 가지 않도록 이름은 `*_API_KEY` 형식만 허용한다.
- 키 값은 어떤 API 응답·로그·변경 이력·예외 메시지에도 나오지 않는다. 관리자 화면에는 출처(`keySource`)와 웹 입력 키의 끝 4자(`keyLast4`)만 준다. 공급자의 인증 오류(401·403) 메시지는 키 조각이 섞일 수 있어 버린다.

## AI 키 관리 (07 문서 6장)

키는 두 곳에서 올 수 있고, 공급자마다 아래 순서로 하나를 고른다.

1. **웹 입력(DB)**: 관리자 사이트에서 넣은 키. `tb_ai_provider_secret`에 AES-256-GCM 암호문으로만 저장한다(저장할 때마다 새 무작위 12바이트 nonce, AAD = `provider_id`라 다른 공급자 행에 붙여 넣어도 복호화되지 않음). 마스터 키는 `WEARLY_SECRET_KEY`(DB에 없음), `key_version`은 지금 1.
2. **환경변수**: 공급자 행의 `api_key_env`(예: `ANTHROPIC_API_KEY`)가 가리키는 값. `.env`도 된다.
3. 둘 다 없으면 그 공급자는 쓸 수 없다 (`usable: false`).

- `WEARLY_SECRET_KEY`가 **없으면** 웹 키 기능이 꺼진다: 시작 로그 WARN, 저장 API는 409 `SECRET_KEY_MISSING`, DB에 남은 키는 보지 않고 환경변수 키만 쓴다. **형식이 틀리면**(base64가 아니거나 32바이트가 아님) 같은 상태로 ERROR를 남긴다. 서버는 어느 경우에도 뜬다.
- 마스터 키가 바뀌어 DB 키를 **복호화하지 못하면** 그 공급자는 쓸 수 없는 것으로 보고 ERROR 로그를 남긴다(환경변수 키로 몰래 바꾸지 않음). 관리자 화면에서 키를 다시 넣으면 된다.
- 복호화한 키는 메모리에 캐시하고, 키를 넣거나 지우거나 공급자를 지우면 비운다 (서버 인스턴스 하나 전제).

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| PUT | `/api/v1/admin/ai/providers/{id}/api-key` | `{ apiKey, currentPassword, verify? }` (`verify` 기본 true) | 200 공급자 행 |
| DELETE | `/api/v1/admin/ai/providers/{id}/api-key` | `{ currentPassword }` | 200 공급자 행 (환경변수 키로 되돌아감) |

- 확인 순서: 공급자 없음 404 → 마스터 키 없음 409 `SECRET_KEY_MISSING` → 비밀번호 재확인 실패 403 `REAUTH_FAILED` → 키 형식(앞뒤 공백 제거 후 20~300자, 공백·줄바꿈 없음) 400 `VALIDATION_FAILED`(`apiKey`) → `verify`면 각 사 **모델 목록 조회**(Claude SDK `models().list()` = `GET /v1/models`, OpenAI `GET /v1/models`, Gemini `GET /v1beta/models?pageSize=1`) 실패 시 400 `API_KEY_INVALID`.
- 변경 이력 `AI_PROVIDER_KEY_SET`·`AI_PROVIDER_KEY_DELETE`: before·after에 `keySource`·`keyLast4`만.
- 공급자 행(목록·추가·수정·키 API 응답)에 `keySource`(`DB`·`ENV`·`NONE`)·`keyLast4`·`keyUpdatedAt`·`keyUpdatedBy`(넣은 관리자 이메일)·`secretStoreEnabled`를 더했다. 뒤의 셋은 DB 키를 쓸 때만 값이 있다. `keyConfigured`는 `keySource !== 'NONE'`과 같다.
- 마스터 키 교체(키 회전)는 아직 없다. 바꾸려면 새 `WEARLY_SECRET_KEY`로 띄운 뒤 웹 키를 다시 넣는다.

하네스 예: `SERVER_PORT=18080 WEARLY_ACCESS_TTL=PT3S WEARLY_AI_DAILY_LIMIT=3 WEARLY_AI_PROVIDER=mock SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/wearly_harness`

## API 요약 (2차에서 더한 것)

### 코드 (06 문서)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/codes` | 공개. `{ version, groups: [{ group, name, codes: [{ code, label, parent, sortOrder, active, attrs }] }] }`. `ETag: "<version>"`, `If-None-Match`가 같으면 304. `Cache-Control: no-cache` |

- 아이템 등록·수정·일괄 등록과 프로필 `colors`·`moods`는 코드 테이블로 검증한다. 새로 고르는 값은 활성 코드만, PATCH에서 안 바꾼 비활성 값은 그대로 둔다. 종류(CATEGORY)가 비활성이면 그 하위 세부 종류도 새로 고를 수 없다.
- `/wardrobe/stats`의 `byCategory`·`byStatus` 키는 활성 코드 전부(0 포함) + 아이템이 있는 비활성 코드, 코드 순서대로.
- 서버는 코드를 메모리에 캐시하고, 관리자 API로 바뀌면 커밋 직후 다시 읽는다 (`version`이 바뀐다).

### 관리자 (07 문서, `/api/v1/admin/**`, `ADMIN`만 — 아니면 403 `FORBIDDEN`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/admin/dashboard` | 회원 수·7일 신규·아이템 수·오늘 AI 호출·공급자별 7일 통계 |
| GET | `/admin/codes` | `/codes` 모양 + 그룹 `description`, 코드별 `usageCount` |
| POST | `/admin/codes/{group}` | 코드 추가 → 201. 중복 409 `CODE_EXISTS` |
| PATCH | `/admin/codes/{group}/{code}` | label·parent·sortOrder·active·attrs 수정 (attrs는 통째로) |
| PUT | `/admin/codes/{group}/order` | `{ codes: [...] }` 순서대로 sortOrder 10, 20, … |
| DELETE | `/admin/codes/{group}/{code}` | 쓰는 아이템·프로필(·하위 세부 종류)이 있으면 409 `CODE_IN_USE` |
| GET·POST | `/admin/ai/providers` | 공급자 목록(`keyConfigured`·`keySource`·`usable`·`today` …) / 추가 (중복 409 `PROVIDER_EXISTS`) |
| PUT·DELETE | `/admin/ai/providers/{id}/api-key` | 3차: 웹 키 넣기·바꾸기 / 지우기 (위 "AI 키 관리") |
| PATCH·DELETE | `/admin/ai/providers/{id}` | 수정 / 삭제 (분석 기록은 남는다) |
| POST | `/admin/ai/providers/{id}/test` | multipart `image`. 사용 여부와 관계없이 한 번 호출, 한도에 안 셈, `feature=ADMIN_TEST`로 기록 |
| GET | `/admin/ai/types` | 지원 `type`과 `options` 설명 |
| GET | `/admin/ai/logs`, `/admin/ai/logs/{id}` | AI 기록 검색(`providerId`·`status`·`userEmail`·`feature`·`from`·`to`) / 결과 JSON 전체 |
| GET·PATCH | `/admin/users`, `/admin/users/{id}` | 회원 검색(`q`·`status`·`role`) / `{ status?, role? }` (자기 자신은 못 내리고 못 정지) |
| GET | `/admin/audit-logs` | 변경 이력 (`targetType` = `CODE`·`AI_PROVIDER`·`USER`) |

- 목록 응답은 `{ items, page, size, total }`, `page` 0부터, `size` 기본 20·최대 100.
- 정지(`SUSPENDED`) 회원: 로그인 403 `ACCOUNT_SUSPENDED`(비밀번호가 맞을 때만), 남은 Access 토큰 401 `TOKEN_INVALID`, refresh 401 `REFRESH_INVALID`(정지할 때 Refresh 토큰을 모두 폐기).
- 권한은 매 요청 DB의 역할로 판단한다 (JWT `role` 클레임은 표시용). 역할을 내리면 같은 토큰으로도 바로 403.
- 모든 관리자 변경은 `tb_admin_audit_log`에 before/after로 남는다.

## 테스트

```bash
LC_ALL=C.UTF-8 ./gradlew test
```

- 통합 테스트는 **Testcontainers**로 `pgvector/pgvector:pg17` 컨테이너를 한 번 띄워 쓴다 (Docker 필요). 서버를 무작위 포트로 띄워 실제 HTTP로 부른다.
- Docker 없이 이미 떠 있는 DB로 돌리려면 `WEARLY_TEST_DB_URL`을 준다. 테스트는 매번 새 이메일로 데이터를 만들고 지우지 않으므로 **개발 DB가 아닌 빈 DB**를 쓴다.

  ```bash
  docker exec wearly-postgres psql -U wearly -c "create database wearly_test"
  WEARLY_TEST_DB_URL=jdbc:postgresql://localhost:5433/wearly_test ./gradlew test
  # 계정이 다르면 WEARLY_TEST_DB_USERNAME / WEARLY_TEST_DB_PASSWORD
  ```

- 테스트는 기본적으로 목업 실행(`IntegrationTestBase`)이고, `AiProviderFallbackIntegrationTest`만 DB 공급자 실행이다. 실제 키도 `.env`도 쓰지 않는다 (`ANTHROPIC_API_KEY` 등을 빈 값으로 덮는다).
- Claude·OpenAI·Gemini 공급자는 로컬 가짜 HTTP 서버(JDK `HttpServer`)로 실제로 나가는 요청 JSON·응답 파싱·오류 처리를 확인한다. 공급자 행의 `options.baseUrl`로 가짜 서버를 가리킨다 (테스트는 `wearly.ai.allow-base-url=true`).
- 단위 테스트의 코드 목록은 계약 원본 `doc/06_코드_초기값.json`을 그대로 읽는다 (`TestCodes`).
- 리눅스에서 로케일이 UTF-8이 아니면 Gradle이 한글 테스트 이름으로 보고서 파일을 못 만든다. `LC_ALL=C.UTF-8 ./gradlew test`로 돌린다 (macOS는 해당 없음).

| 테스트 | 확인하는 것 |
|---|---|
| `AuthIntegrationTest` | 가입·로그인·토큰·refresh 회전·재사용 감지·탈퇴, 가입 회원의 `role=USER`와 JWT `role` 클레임, CORS(5173 포함), health |
| `ShortAccessTtlIntegrationTest` | Access 수명 1초로 띄워 실제 만료 후 refresh |
| `ProfileIntegrationTest` | 기본값·저장·검증 (색·취향은 코드 검증) |
| `WardrobeIntegrationTest` | CRUD·PATCH 병합·짝·필터·정렬·검색·통계(DRESS 키 포함)·bulk |
| `CodeIntegrationTest` | `/codes` 형식·ETag 304·공개, 새 코드(DRESS·JEANS·denim·FLORAL…)로 등록, 모르는 코드·비활성 코드·parent 불일치 400, PATCH에서 안 바꾼 비활성 필드 통과, 비활성 종류의 하위 세부 종류, 통계 키 |
| `AdminCodeIntegrationTest` | 관리자 권한 403·401, 코드 CRUD·검증·순서·사용 중 삭제 409·pairs 양방향·캐시 갱신(version 변경)·변경 이력 |
| `AdminAiProviderIntegrationTest` | 공급자 초기값·CRUD·`keyConfigured`·`usable`·검증(키 환경변수 이름·options)·키 값 비노출·변경 이력 |
| `ProviderApiKeyIntegrationTest` | (마스터 키 있음) 웹 키 저장·교체·삭제, 키 출처 ENV→DB→ENV·NONE→DB, 암호문·nonce, 시험 분석이 DB 키로 호출, 재확인 403, 형식 400, verify(가짜 OpenAI·Gemini·Claude 서버의 모델 목록 경로·헤더) 실패 400, 공급자 삭제 시 비밀 행 삭제, 변경 이력 내용, 서버 출력·응답·이력에 키 0건 |
| `ProviderApiKeyNoMasterKeyIntegrationTest` | 마스터 키 없음 409 `SECRET_KEY_MISSING`(비밀번호 확인보다 먼저), `secretStoreEnabled: false` |
| `AesGcmSecretCipherTest` · `ApiKeyRulesTest` · `ApiKeyServiceTest` | 암호화 왕복·nonce 매번 다름·AAD/마스터 키/암호문이 다르면 실패·마스터 키 없음 WARN·형식 오류 ERROR, 키 형식·출처 우선순위, 복호화 캐시·무효화·복호화 실패 시 쓸 수 없음 (Spring 없음) |
| `HexagonalArchitectureTest` | 08 문서 3장 규칙 12개 (ArchUnit) |
| `*domain*` 단위 테스트 | 코드 검증·attrs, refresh 회전·재사용 감지, 공급자 선택 순서, 옷장 검증·통계 키, 프로필 정리 (Spring 없음) |
| `AiProviderFallbackIntegrationTest` | (DB 공급자 실행) 공급자 없으면 502, priority 순 시도·실패 시 다음 공급자·실패 기록, 전부 실패 502·한도 미차감, 시험 분석(사용 여부 무관·한도 미차감·ADMIN_TEST), AI 기록 검색·상세·대시보드 |
| `AdminUserIntegrationTest` | 시작 시 관리자 생성·기존 회원 승격, 회원 검색, 정지 시 로그인 403·토큰 401·refresh 401, 자기 자신 강등·정지 400, 대시보드 |
| `AiAnalyzeIntegrationTest` | 목업 결정성, 일일 한도 429, 10MB 초과 413, 형식·파트 검증 |
| `AiProviderModeTest` | `WEARLY_AI_PROVIDER` 해석(`mock`·`db`·`claude`), `auto`·모르는 값 시작 실패와 안내 |
| `ClaudeAiProviderTest` · `OpenAiProviderTest` · `GeminiAiProviderTest` | 각 공급자의 요청 JSON(이미지·구조화 출력 스키마·헤더), 응답 파싱·토큰 수, 거절·잘림·HTTP 오류·시간 초과, 오류 메시지에 키가 남지 않음 |
| `MockAiProviderTest` · `ImageTypesTest` | 목업이 활성 코드 안에서 짝 맞는 값만 고름, 이미지 형식 판별 |

## JPA와 MyBatis 분담

같은 DataSource와 트랜잭션 매니저(`JpaTransactionManager`)를 쓴다. MyBatis는 Spring이 관리하는 커넥션에 참여하므로 한 `@Transactional` 안에서 섞어 써도 된다.

| | JPA (쓰기·단건) | MyBatis (읽기 전용) |
|---|---|---|
| 회원·자격 증명·Refresh 토큰 | `User`(역할·정지), `LocalCredential`, `RefreshToken` | 관리자 회원 목록 (`AdminQueryMapper.xml`) |
| 프로필 | `UserProfile` | |
| 옷장 | 등록·bulk·PATCH·삭제·단건 조회 (`WardrobeItem`) | 동적 필터 목록, `/wardrobe/stats` 집계 (`WardrobeQueryMapper.xml`) |
| 코드 | 관리자 추가·수정·순서·삭제 (`Code`) | 캐시 적재·version·사용 수 집계 (`CodeQueryMapper.xml`) |
| AI | 호출 기록 저장 (`AiAnalysisLogEntity`), 공급자 설정 (`AiProviderEntity`), 웹 키 암호문 (`ProviderSecretEntity`) | 일일 사용량 (`ai/AiUsageMapper.xml`), 관리자 AI 기록 검색·공급자별 통계·대시보드 (`admin/AdminQueryMapper.xml`) |
| 변경 이력 | 기록 (`AuditLog`) | 목록 (`AdminQueryMapper.xml`) |

## 패키지 구조 (08 문서, 3차)

컨텍스트마다 같은 모양이다. 안쪽으로만 의존하고(`adapter` → `application` → `domain`), 규칙은 `HexagonalArchitectureTest`(ArchUnit)가 검사한다.

```
app.wearly.<context>
├── domain/                  순수 자바 도메인 모델·규칙 (Spring·JPA·Jackson 없음)
├── application/
│   ├── port/in/             유스케이스 인터페이스 + 명령·조회 조건·결과
│   ├── port/out/            출력 포트 (저장·조회·외부 호출)
│   └── service/             유스케이스 구현 (@Service, @Transactional은 여기까지)
└── adapter/
    ├── in/web/              @RestController, 요청·응답 DTO   (in/startup: 시작 시 실행)
    └── out/persistence/     JPA 엔티티(*Entity)·리포지토리·쓰기 포트 구현
        out/query/           MyBatis 매퍼·조회 포트 구현 (XML: resources/mapper/<context>/)
        out/<기술>/           ai: ai/claude·ai/openai·ai/gemini·ai/mock·env·crypto, auth: security
app.wearly.common
├── domain/                  여러 컨텍스트가 쓰는 값 (FieldError, PatchBody, ImageUpload, Ids·Times·Zones, code.CodeSnapshot)
├── error/                   오류 코드·ApiException·전역 예외 처리
├── web/                     웹 어댑터 공용 (CurrentUser, PathIds, Uploads, JsonPatchBody)
├── config/                  Spring·Security 설정, 환경변수(WearlyProperties) → 유스케이스 설정 값
└── mybatis/                 TypeHandler
```

| 컨텍스트 | domain | 입력 포트 (주요) | 출력 어댑터 |
|---|---|---|---|
| `auth` | `RefreshToken`(회전·재사용 판단), `RefreshTokens`, `LocalCredential` | `AuthUseCase`, `AuthenticationUseCase`, `AccountUseCase` | JPA(자격 증명·Refresh 토큰), security(BCrypt, JWT 발급) |
| `user` | `User`, `UserProfile`, `DressCode`, `WorkType` | `UserUseCase`, `ProfileUseCase` | JPA(회원·프로필) |
| `wardrobe` | `WardrobeItem`, `ItemInput`, `ItemCodeValidator`, `WardrobeStatsRules` | `WardrobeUseCase`, `WardrobeStatsUseCase` | JPA(아이템 쓰기·단건), MyBatis(목록·통계) |
| `code` | `Code`, `CodeAttrs`, `CodeRules` (스냅샷은 `common.domain.code`) | `CodeCatalog`, `CodeManagementUseCase` | JPA(코드 쓰기), MyBatis(스냅샷 캐시·사용 수) |
| `ai` | `AiProviderSetting`, `AiProviderSelection`, `AiRunMode`, `AiProviderType`, `ApiKeyRules`, `AnalysisPrompt` … | `AnalyzeImageUseCase`, `ProviderTestUseCase`, `AiProviderAdminUseCase`, `AiStatusUseCase` | JPA(공급자·호출 기록·웹 키), MyBatis(사용량), ai/*(Claude SDK·OpenAI·Gemini REST·목업·키 확인), env, crypto(AES-GCM) |
| `admin` | `AuditTargets`, `SearchText` | `AdminDashboardUseCase`, `AdminUserUseCase`, `AdminCodeUseCase`, `AdminAiProviderUseCase`, `EnsureAdminUseCase` | JPA(변경 이력 = `RecordAuditPort`), MyBatis(대시보드·목록 읽기 모델) |

- 컨텍스트 사이는 상대 `port.in`으로만 부른다. 관리자 API는 admin 컨텍스트가 code·ai·user·auth 유스케이스를 부르고, 같은 트랜잭션에서 변경 이력을 남긴다.
- 사진 분석 `POST /wardrobe/items/analyze`는 경로는 옷장이지만 ai 컨텍스트의 `AiAnalyzeController`가 받는다.
- `MultipartFile`은 웹 어댑터에서 `ImageUpload`(byte[]·MIME)로, PATCH JSON은 `PatchBody`(관리자)·`ItemPatch`(옷장)로 바꿔 넘긴다.
