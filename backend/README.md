# Wearly 백엔드

웨어리 앱과 관리자 사이트의 API 서버. 계약 문서:

- [`doc/05_백엔드_API.md`](../doc/05_백엔드_API.md) — 1차: 토큰 로그인 · 옷장 · AI 분석
- [`doc/06_코드_관리.md`](../doc/06_코드_관리.md) — 2차: 옷 속성 값을 DB 코드 테이블로 (`GET /codes`, 코드 검증)
- [`doc/07_관리자_사이트.md`](../doc/07_관리자_사이트.md) — 2차: 관리자 역할·관리자 API·AI 공급자(Claude·OpenAI·Gemini) 관리, 3차: 6장 AI 키 웹 입력(쓰기 전용 · 암호화 저장)
- [`doc/08_백엔드_구조.md`](../doc/08_백엔드_구조.md) — 3차: 헥사고날 패키지 구조와 의존 규칙 (ArchUnit으로 검사), 4차: 5장 코드 스타일(Google Java Style)
- [`doc/09_AI_코디_추천.md`](../doc/09_AI_코디_추천.md) — 4차: AI 코디 추천 API (`POST /outfits/recommendations`)
- [`doc/12_디자인_테마.md`](../doc/12_디자인_테마.md) 3장 — 6차: 앱 설정(전체 사용자 테마) `GET /app/config`, `GET·PUT /admin/app/theme`
- [`doc/13_옷_사용_중지.md`](../doc/13_옷_사용_중지.md) — 6차: 사용하지 않는 옷(`retire`·`restore`, `retired` 필터, 통계) · 완전 삭제

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.1 (Java 21 toolchain, Gradle 9.8.1 wrapper, Kotlin DSL) |
| 데이터 접근 | Spring Data JPA (Hibernate 7.4) + MyBatis (mybatis-spring-boot-starter 4.1.0) |
| DB | PostgreSQL 17 + pgvector, 스키마는 Flyway (`src/main/resources/db/migration`, V1~V9) |
| 인증 | Spring Security OAuth2 Resource Server, JWT HS256 (Nimbus) |
| AI | Claude: Anthropic Java SDK 2.73.0 / OpenAI·Gemini: 각 사 REST API를 Spring `RestClient`로 (SDK 없음) |
| 구조 | 헥사고날 (컨텍스트별 domain · application · adapter), 규칙은 ArchUnit 1.5.1 테스트 |
| 키 암호화 | AES-256-GCM (JDK `javax.crypto`), 마스터 키 `wearly.secret-key` (환경변수 `WEARLY_SECRET_KEY`) |
| 설정 | 공통 `application.yml` + 로컬 비밀 `config/application-local.yml` (git 제외, `.env` 쓰지 않음, 08 문서 6장) |
| 로컬 실행 | 루트 `docker-compose.yml`: db · backend · admin |
| 코드 스타일 | Google Java Style: Spotless 8.10.4 + google-java-format 1.36.1, Checkstyle 14.3.0 (`config/checkstyle/google_checks.xml`) |

## 실행

처음 한 번, 로컬 비밀 설정 파일을 만든다 (git 제외, 아래 "설정"):

```bash
cd backend
cp config/application-local.example.yml config/application-local.yml   # AI 키·관리자 계정·마스터 키를 채운다
chmod 600 config/application-local.yml
```

### 전체 실행: docker compose (DB · 백엔드 · 관리자 사이트)

저장소 루트에서:

```bash
docker compose up -d --build              # 전체 빌드·실행: db(5433) · backend(8080) · admin(5173)
docker compose logs -f backend            # 백엔드 로그 보기 (admin, db도 같은 방식)
docker compose up -d --build backend      # 백엔드만 다시 빌드·재시작 (코드 수정 후)
docker compose down                       # 중지 (DB 데이터는 wearly-pgdata 볼륨에 남음)
```

- **앱(Expo)은 compose에 없다.** 시뮬레이터·휴대폰이 Mac의 Metro에 붙어야 하므로 Mac에서 `cd mobile && npx expo start`를 따로 띄운다. 앱은 `http://<Mac>:8080`의 compose 백엔드를 그대로 쓴다.
- 백엔드 이미지(`backend/Dockerfile`): 빌드는 `eclipse-temurin:21-jdk`에서 `./gradlew bootJar -x test`(Gradle 캐시는 BuildKit 캐시 마운트), 실행은 `eclipse-temurin:21-jre`의 비루트 사용자 `wearly`, 작업 디렉터리 `/app`. 이미지 빌드에서는 테스트를 돌리지 않으니 테스트는 Mac에서 `./gradlew test`.
- compose는 `--spring.profiles.active=docker`로 띄운다 (`application-docker.yml`: DB 주소 `db:5432`, 비밀 값 없음). 비밀 값은 `backend/config/` 폴더를 `/app/config`에 **읽기 전용으로 마운트**해서 `application-local.yml`을 읽는다. `backend/.dockerignore`가 이 파일을 빼므로 이미지에는 들어가지 않는다.
- 헬스체크는 `/actuator/health`. 시작 순서는 db(healthy) → backend → admin.
- 관리자 사이트(`admin/Dockerfile`, 빌드 컨텍스트는 저장소 루트)는 nginx가 빌드 결과를 서빙한다. API 주소는 빌드 인자 `VITE_API_URL`(기본 `http://localhost:8080`). 관리자 사이트를 vite(`npm run dev`, 5173)로 띄울 때는 포트가 겹치므로 `docker compose up -d --build db backend`만 띄운다.

### Mac에서 직접 실행

```bash
docker compose up -d db   # 저장소 루트에서 DB만 (localhost:5433, DB·계정·비밀번호 wearly)
cd backend
./gradlew bootRun         # 관리자 화면의 AI 공급자 설정(DB)대로 사진 분석
./gradlew bootRunMock     # 목업 공급자로 사진 분석 (키 불필요, WEARLY_AI_PROVIDER=mock)
```

compose의 backend가 떠 있으면 8080이 겹치므로 먼저 `docker compose stop backend`.

- 서버: `http://localhost:8080`, API 기본 경로 `/api/v1`, 상태 확인 `GET /actuator/health`
- 처음 뜰 때 Flyway가 `V1__init.sql` → `V2__codes.sql`(코드 테이블 + 초기값 102개) → `V3__admin_ai.sql`(역할·AI 공급자·변경 이력) → `V4__ai_provider_secret.sql`(웹 입력 AI 키 암호문) → `V5__outfit_recommend.sql`(AI 기록 feature에 `OUTFIT_RECOMMEND`, 한도 인덱스를 기능별로) → `V6__ai_usage_reservation.sql`(5차: 진행 중 AI 호출 예약, 동시 요청 한도) → `V7__ai_log_summary.sql`(5차: feature를 형식 CHECK로, 기록 요약 `result_summary` 추가·기존 행 채움) → `V8__app_setting.sql`(6차: 앱 설정 `tb_app_setting`, 초기 테마 `nyang`) → `V9__wardrobe_item_retire.sql`(6차: 옷 사용 중지 컬럼 `retired_at`·`retire_reason`·`retire_memo`, 부분 인덱스, 코드 그룹 `RETIRE_REASON` 5개)을 적용한다. 1차 DB에 있던 데이터는 그대로 쓴다 (기존 값은 모두 새 코드에 있다). JPA는 `ddl-auto: validate`.
- **AI 공급자 고르기** (07 문서 3장)
  - `bootRunMock`(`WEARLY_AI_PROVIDER=mock`)이면 언제나 목업. DB 설정은 무시한다.
  - 그 밖(`bootRun`, 기본 `db`)에는 `tb_ai_provider`에서 사용(`enabled`)·모델·키를 모두 갖춘 공급자를 `priority` 순으로 시도하고, 실패(시간 초과·HTTP 오류·응답 검증 실패)하면 다음 공급자로 넘어간다.
  - 키는 **웹에서 넣은 키(DB) → 서버 설정 키(`wearly.ai.keys.<api_key_env>` → 같은 이름 환경변수) → 없음** 순서로 고른다 (아래 "AI 키 관리").
  - 쓸 수 있는 공급자가 없어도 서버는 **뜬다**. 시작 로그에 WARN이 남고, 사진 분석·AI 코디 추천은 502 `AI_UNAVAILABLE`이다 (목업으로 대신하지 않는다). 관리자 사이트에서 고칠 수 있게 하려는 것이다.
  - 시작 직후 로그: `AI 공급자 (시도 순서): claude(CLAUDE, model=…, priority=1, key=ENV), …` 또는 `쓸 수 있는 AI 공급자가 없습니다 …` 또는 `AI 공급자: mock (목업 …)`.
  - 처음 상태: `claude`(사용, `claude-opus-5-5`, 키 `ANTHROPIC_API_KEY`), `openai`·`gemini`(꺼짐, 모델 비움). 그래서 `config/application-local.yml`의 `wearly.ai.keys.ANTHROPIC_API_KEY`만 넣으면 1차와 같이 Claude로 돈다.
- **관리자 계정**: `wearly.admin.email`·`wearly.admin.password`(환경변수 `WEARLY_ADMIN_EMAIL`·`WEARLY_ADMIN_PASSWORD`)가 있으면 시작할 때 그 계정을 만들거나(이름 `wearly.admin.name`, 기본 "관리자") 이미 있는 계정을 `ADMIN`으로 올린다. 이미 있는 계정의 비밀번호·이름은 바꾸지 않는다. 가입 API로는 관리자를 만들 수 없다.
- 운영 프로필(`SPRING_PROFILES_ACTIVE=prod`)은 `WEARLY_JWT_SECRET`이 없으면 기동하지 않는다.

실행 가능한 jar: `./gradlew bootJar` → `build/libs/wearly-backend-0.0.1-SNAPSHOT.jar` (`backend/`에서 `java -jar`로 실행해야 `./config/application-local.yml`을 읽는다)

## 설정 (yml, 08 문서 6장)

- **공통 설정**: `src/main/resources/application.yml` (git 포함, 비밀 값 없음). compose용 `application-docker.yml`, 운영용 `application-prod.yml`.
- **로컬 비밀 설정**: `config/application-local.yml` (git·docker 이미지 제외, 권한 600 권장). 견본은 `config/application-local.example.yml`(커밋). 같은 속성 이름을 계층으로 적고, 값은 따옴표로 감싼다.
- 서버는 `spring.config.import: optional:file:${WEARLY_LOCAL_CONFIG:./config/application-local.yml}`로 읽는다. 그래서 `bootRun`/`java -jar`는 **`backend/` 디렉터리에서** 실행한다 (다른 파일을 쓰려면 `WEARLY_LOCAL_CONFIG=<경로>`). 파일이 없으면 건너뛴다.
- **테스트와 하네스는 이 파일을 읽지 않는다**: `build.gradle.kts`의 test 태스크와 `harness/run.sh`가 `WEARLY_LOCAL_CONFIG`를 없는 경로로 준다. IDE에서 Gradle을 거치지 않고 테스트를 돌릴 때는 같은 환경변수를 직접 준다.
- `.env`는 더 이상 읽지 않는다 (예전 `backend/.env`의 값은 같은 이름의 yml 속성으로 옮긴다: `ANTHROPIC_API_KEY` → `wearly.ai.keys.ANTHROPIC_API_KEY`, `WEARLY_ADMIN_EMAIL` → `wearly.admin.email`, `WEARLY_SECRET_KEY` → `wearly.secret-key` …).

```yaml
# config/application-local.yml (예시 값)
wearly:
  ai:
    keys:                         # 공급자 행의 서버 설정 키 이름(api_key_env) → 키 값
      ANTHROPIC_API_KEY: "sk-ant-..."
  secret-key: "..."               # openssl rand -base64 32
  admin:
    email: "admin@example.com"
    password: "admin1234pw"
    name: "관리자"
```

아래 값은 OS 환경변수(왼쪽 이름)로도 덮어쓸 수 있다 (운영 서버·CI). 같은 항목을 yml과 환경변수에 함께 두지 않는다.

| 환경변수 | yml 속성 | 기본값 | 설명 |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5433/wearly` | DB 주소 (compose는 `db:5432`) |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `wearly` | |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `wearly` | |
| `SERVER_PORT` | `server.port` | `8080` | |
| `WEARLY_JWT_SECRET` | `wearly.jwt.secret` | 개발용 고정값 | HS256 키, 32바이트 이상. prod 프로필에서는 필수 |
| `WEARLY_ACCESS_TTL` | `wearly.jwt.access-ttl` | `PT30M` | Access 토큰 수명 (ISO-8601 Duration) |
| `WEARLY_REFRESH_TTL` | `wearly.jwt.refresh-ttl` | `P30D` | Refresh 토큰 수명 |
| `WEARLY_AI_PROVIDER` | `wearly.ai.provider` | `db` | `db`(관리자 화면 설정) · `mock`. 1차 값 `claude`는 `db`와 같다. 그 밖의 값(예전 `auto` 포함)은 시작 실패 |
| `WEARLY_AI_DAILY_LIMIT` | `wearly.ai.daily-limit` | `10` | 회원별 하루 사진 분석 횟수 (Asia/Seoul 날짜, 성공만, 관리자 시험 분석 제외) |
| `WEARLY_AI_OUTFIT_DAILY_LIMIT` | `wearly.ai.outfit-daily-limit` | `20` | 회원별 하루 AI 코디 추천 횟수 (사진 분석과 따로 센다, Asia/Seoul 날짜, 성공만) |
| `WEARLY_AI_TIMEOUT` | `wearly.ai.timeout` | `PT60S` | 공급자 호출 제한 시간 기본값. 공급자 `options.timeoutSeconds`가 있으면 그 값 |
| `WEARLY_AI_ALLOW_BASE_URL` | `wearly.ai.allow-base-url` | `false` | `true`일 때만 공급자 `options.baseUrl`(API 주소 바꾸기)을 허용. 키가 다른 주소로 갈 수 있어 운영에서는 끈다 |
| `ANTHROPIC_API_KEY` | `wearly.ai.keys.ANTHROPIC_API_KEY` | (없음) | Claude 키. 처음 상태의 `claude` 행이 가리키는 이름. yml 값이 먼저 |
| `OPENAI_API_KEY` | `wearly.ai.keys.OPENAI_API_KEY` | (없음) | OpenAI 키. `openai` 행이 가리키는 이름 |
| `GEMINI_API_KEY` | `wearly.ai.keys.GEMINI_API_KEY` | (없음) | Gemini 키. `gemini` 행이 가리키는 이름 |
| `WEARLY_ADMIN_EMAIL` / `WEARLY_ADMIN_PASSWORD` | `wearly.admin.email` / `wearly.admin.password` | (없음) | 둘 다 있으면 시작 시 관리자 계정 생성·승격 |
| `WEARLY_ADMIN_NAME` | `wearly.admin.name` | `관리자` | 새로 만들 때의 이름 |
| `WEARLY_SECRET_KEY` | `wearly.secret-key` | (없음) | 웹에서 넣은 AI 키를 암호화하는 마스터 키. **base64로 인코딩한 32바이트** (`openssl rand -base64 32`). 없으면 웹 키 입력이 꺼진다 |
| `WEARLY_LOCAL_CONFIG` | — | `./config/application-local.yml` | 로컬 비밀 설정 파일 경로 |

- 1차의 `WEARLY_AI_MODEL`·`WEARLY_AI_EFFORT`는 없어졌다. 모델·effort는 관리자 화면(`tb_ai_provider.model`·`options.effort`)에서 바꾼다.
- 공급자 행의 `api_key_env`는 **서버 설정 키 이름**이다 (관리자 화면의 '서버 설정 키'). 서버는 `wearly.ai.keys.<이름>` → 같은 이름의 환경변수 순으로 키를 찾는다. 다른 서버 비밀(DB 비밀번호·JWT 키)이 공급자에게 가지 않도록 이름은 `*_API_KEY` 형식만 허용한다.
- 키 값은 어떤 API 응답·로그·변경 이력·예외 메시지에도 나오지 않는다. 관리자 화면에는 출처(`keySource`)와 웹 입력 키의 끝 4자(`keyLast4`)만 준다. 공급자의 인증 오류(401·403) 메시지는 키 조각이 섞일 수 있어 버린다.

## AI 키 관리 (07 문서 6장)

키는 두 곳에서 올 수 있고, 공급자마다 아래 순서로 하나를 고른다.

1. **웹 입력(DB)**: 관리자 사이트에서 넣은 키. `tb_ai_provider_secret`에 AES-256-GCM 암호문으로만 저장한다(저장할 때마다 새 무작위 12바이트 nonce, AAD = `provider_id`라 다른 공급자 행에 붙여 넣어도 복호화되지 않음). 마스터 키는 `wearly.secret-key`(환경변수 `WEARLY_SECRET_KEY`, DB에 없음), `key_version`은 지금 1.
2. **서버 설정 키**: 공급자 행의 `api_key_env`(예: `ANTHROPIC_API_KEY`)를 이름으로 `wearly.ai.keys.<이름>`(`config/application-local.yml`) → 같은 이름의 OS 환경변수 순으로 찾은 값.
3. 둘 다 없으면 그 공급자는 쓸 수 없다 (`usable: false`).

- 마스터 키(`wearly.secret-key`)가 **없으면** 웹 키 기능이 꺼진다: 시작 로그 WARN, 저장 API는 409 `SECRET_KEY_MISSING`, DB에 남은 키는 보지 않고 서버 설정 키만 쓴다. **형식이 틀리면**(base64가 아니거나 32바이트가 아님) 같은 상태로 ERROR를 남긴다. 서버는 어느 경우에도 뜬다.
- 마스터 키가 바뀌어 DB 키를 **복호화하지 못하면** 그 공급자는 쓸 수 없는 것으로 보고 ERROR 로그를 남긴다(서버 설정 키로 몰래 바꾸지 않음). 관리자 화면에서 키를 다시 넣으면 된다.
- 복호화한 키는 메모리에 캐시하고, 키를 넣거나 지우거나 공급자를 지우면 비운다 (서버 인스턴스 하나 전제).

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| PUT | `/api/v1/admin/ai/providers/{id}/api-key` | `{ apiKey, currentPassword, verify? }` (`verify` 기본 true) | 200 공급자 행 |
| DELETE | `/api/v1/admin/ai/providers/{id}/api-key` | `{ currentPassword }` | 200 공급자 행 (서버 설정 키로 되돌아감) |

- 확인 순서: 공급자 없음 404 → 마스터 키 없음 409 `SECRET_KEY_MISSING` → 비밀번호 재확인 실패 403 `REAUTH_FAILED` → 키 형식(앞뒤 공백 제거 후 20~300자, 공백·줄바꿈 없음) 400 `VALIDATION_FAILED`(`apiKey`) → `verify`면 각 사 **모델 목록 조회**(Claude SDK `models().list()` = `GET /v1/models`, OpenAI `GET /v1/models`, Gemini `GET /v1beta/models?pageSize=1`) 실패 시 400 `API_KEY_INVALID`.
- 변경 이력 `AI_PROVIDER_KEY_SET`·`AI_PROVIDER_KEY_DELETE`: before·after에 `keySource`·`keyLast4`만.
- 공급자 행(목록·추가·수정·키 API 응답)에 `keySource`(`DB`·`ENV`·`NONE`)·`keyLast4`·`keyUpdatedAt`·`keyUpdatedBy`(넣은 관리자 이메일)·`secretStoreEnabled`를 더했다. 뒤의 셋은 DB 키를 쓸 때만 값이 있다. `keyConfigured`는 `keySource !== 'NONE'`과 같다.
- 마스터 키 교체(키 회전)는 아직 없다. 바꾸려면 새 `wearly.secret-key`로 띄운 뒤 웹 키를 다시 넣는다.

하네스 예 (`harness/run.sh`가 이렇게 띄운다): `WEARLY_LOCAL_CONFIG=<없는 경로> SERVER_PORT=18080 WEARLY_ACCESS_TTL=PT3S WEARLY_AI_DAILY_LIMIT=3 WEARLY_AI_PROVIDER=mock SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/wearly_harness`

## AI 코디 추천 (09 문서, 4차)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/v1/outfits/recommendations` | `OutfitRecommendRequest` (09 문서 3장) | 200 `{ candidates: [{ items, reason, aiScores }], provider, model, remainingToday }` |

- 흐름: 요청 검증 400 → 고정·중심 아이템 확인 400 → 옷 부족 400 `WARDROBE_INSUFFICIENT` → 일일 한도 429 `AI_LIMIT_EXCEEDED` → 공급자를 priority 순으로 시도(사진 분석과 같은 공급자·순서) → 모두 실패 502 `AI_UNAVAILABLE`. AI를 부르기 전에 끝나면 기록을 남기지 않는다.
- **AI에게 주는 것**: 이번 추천에 쓸 수 있는 내 옷(CATEGORY `slot`이 있고 ITEM_STATUS `recommendable`, 제외 목록·피하고 싶은 색 제외. 고정·중심 아이템은 상태와 관계없이 넣음)을 칸별로 id·이름·세부 종류·색(뉴트럴·밝기)·패턴·계절·스타일·격식·보온·볼륨·착용 횟수·마지막 착용일과 함께, 프로필 취향, 조건(상황·목표 격식·분위기·날씨와 아우터·계절·보온 힌트·컨셉·메모), 최근 7일 코디, 좋아한/덜 좋아한 아이템, 2장 점수 기준, 내 옷 색끼리의 어울리는 짝.
- **구조화 출력 스키마**: `{ candidates: [{ items: { TOP, BOTTOM, DRESS, OUTER, SHOES, BAG }, reason, aiScores: { color, occasion, weather, taste, diversity } }] }`. 칸 값은 그 칸에 쓸 수 있는 내 옷 id의 enum이고, 비울 수 있는 칸은 `null`도 허용(못 쓰는 칸은 `null`만). 모든 객체 `additionalProperties: false`·모든 필드 required. 숫자 범위 제약은 넣지 않고 서버가 맞춘다(Claude 구조화 출력이 지원하지 않음).
- **후보 검증**(공급자 무관, `CandidateValidator`): 내 옷인지, 칸이 종류의 칸과 같은지, 추천 가능한 상태인지, 제외·피하고 싶은 색이 아닌지(고정·중심 아이템은 예외), TOP+BOTTOM xor DRESS, SHOES 있음, 고정·중심 포함, 중복 아님, `reason`(80자로 자름), `aiScores`(0~100 정수로 맞춤). 틀린 후보만 버리고, **남은 후보가 0개면 그 공급자를 실패(`REJECTED_OUTPUT`)로 기록하고 다음 공급자**로 넘어간다.
- **공급자 요청**: Claude `system` + user 텍스트 + `output_config.format`(json_schema, effort·서버 측 폴백은 사진 분석과 같음), OpenAI Responses `instructions` + `input_text` + `text.format`(json_schema, strict), Gemini `systemInstruction` + text part + `generationConfig.responseFormat.text.schema`(또는 `options.schemaField=responseJsonSchema`).
- **목업**(`bootRunMock`): 칸별 목록에서 규칙에 맞는 후보를 결정적으로 만든다(상의×하의 조합 id 순 → 원피스, 신발·가방은 돌려 쓰기, 아우터는 고정했거나 날씨가 원할 때). 응답 `provider: "mock"`, `model: null`. 서버 검증을 그대로 거친다.
- **기록**: `tb_ai_analysis_log.feature = 'OUTFIT_RECOMMEND'`, `image_sha256`·`image_bytes`는 NULL, `result`는 `{ candidates: [...], dropped: [버린 까닭] }`. 관리자 대시보드·AI 기록(`feature=OUTFIT_RECOMMEND` 검색, 요약 "코디 후보 N개")·공급자 `today`에 들어간다.
- 400 `WARDROBE_INSUFFICIENT`의 `message`는 칸 코드로 모자란 칸을 알린다: `추천할 수 있는 옷이 모자라요: BOTTOM 또는 DRESS, SHOES` (앱이 칸 이름으로 바꾼다).

## 코드 스타일 (08 문서 5장, 4차)

[Google Java Style](https://google.github.io/styleguide/javaguide.html). 포맷은 도구가 정하고, `check`가 어기면 실패한다.

```bash
./gradlew spotlessApply   # 포맷 고치기 (google-java-format: 2칸 들여쓰기, 100자, import 정렬·안 쓰는 import 제거)
LC_ALL=C.UTF-8 ./gradlew check   # spotlessCheck + checkstyleMain + checkstyleTest + test
./gradlew spotlessCheck checkstyleMain checkstyleTest   # 테스트 없이 스타일만
```

- Spotless 8.10.4 + google-java-format 1.36.1 (`src/**/*.java`, 와일드카드 import 금지). google-java-format 1.37.0은 Spotless 8.10.4에서 포맷 단계가 실패해(InvocationTargetException) 그 바로 앞 버전을 쓴다.
- Checkstyle 14.3.0, 설정은 Checkstyle 14.3.0 배포본의 `google_checks.xml`을 옮긴 `config/checkstyle/google_checks.xml`. 기본 심각도는 error(빌드 실패), Javadoc 관련 모듈 14개만 warning(보고서 `build/reports/checkstyle/`). 바꾼 곳은 파일 머리 주석에 적었다.
- 예외는 `config/checkstyle/checkstyle-suppressions.xml` 하나: 테스트 소스의 메서드 이름 규칙(`GoogleMethodName`·`AbbreviationAsWordInName`) — 테스트 이름을 한국어 문장으로 쓰기 때문이다. 본 코드에서 꼭 필요한 예외는 `@SuppressWarnings("checkstyle:규칙")`과 이유 주석(예: 앱 JSON 필드 이름 `dMinus1`).
- MyBatis XML·SQL 마이그레이션·`build.gradle.kts`는 대상이 아니다. IntelliJ는 google-java-format 플러그인을 켜거나 저장 전에 `spotlessApply`를 돌린다.

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
| GET | `/admin/ai/logs`, `/admin/ai/logs/{id}` | AI 기록 검색(`providerId`·`status`·`userEmail`·`feature`(`ITEM_ANALYZE`·`ADMIN_TEST`·`OUTFIT_RECOMMEND`)·`from`·`to`) / 결과 JSON 전체 |
| GET·PATCH | `/admin/users`, `/admin/users/{id}` | 회원 검색(`q`·`status`·`role`) / `{ status?, role? }` (자기 자신은 못 내리고 못 정지). `itemCount`는 사용 중인 옷, `retiredItemCount`는 사용 중지한 옷(6차) |
| GET | `/admin/audit-logs` | 변경 이력 (`targetType` = `CODE`·`AI_PROVIDER`·`USER`·`APP_SETTING`) |
| GET·PUT | `/admin/app/theme` | 6차: 전체 사용자 테마 `{ themeId, updatedAt, updatedBy }` / `{ themeId }`(`nyang`·`closeta`, 그 밖 400 `themeId`). 바뀌면 이력 `APP_THEME_UPDATE` |

- 목록 응답은 `{ items, page, size, total }`, `page` 0부터, `size` 기본 20·최대 100.
- 정지(`SUSPENDED`) 회원: 로그인 403 `ACCOUNT_SUSPENDED`(비밀번호가 맞을 때만), 남은 Access 토큰 401 `TOKEN_INVALID`, refresh 401 `REFRESH_INVALID`(정지할 때 Refresh 토큰을 모두 폐기).
- 권한은 매 요청 DB의 역할로 판단한다 (JWT `role` 클레임은 표시용). 역할을 내리면 같은 토큰으로도 바로 403.
- 모든 관리자 변경은 `tb_admin_audit_log`에 before/after로 남는다.

## API 요약 (6차에서 더한 것)

### 앱 설정 (12 문서 3장)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/app/config` | **공개**. `{ themeId, version }`. `version`은 마지막으로 바꾼 시각(ISO, 밀리초), 바꾼 적이 없으면 `"initial"`. `ETag: "<version>"`, `If-None-Match`가 같으면 304, `Cache-Control: no-cache`. 만료된 토큰이 같이 와도 무시 |

관리자 조회·변경(`/admin/app/theme`)은 위 관리자 표. 같은 테마로 PUT하면 아무것도 바꾸지 않는다(시각·이력 그대로). 처음 상태의 `updatedAt`·`updatedBy`는 `null`.

### 옷 사용 중지 (13 문서)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/wardrobe/items/{id}/retire` | `{ reason, memo? }` → 200 아이템. `reason`은 활성 `RETIRE_REASON` 코드, `memo`는 200자 이하(앞뒤 공백 제거, 빈 값은 null). 이미 사용 중지면 이유·메모만 바꾸고 시각은 그대로. 남의 옷·없는 옷은 입력 검증보다 먼저 404 |
| POST | `/api/v1/wardrobe/items/{id}/restore` | 200 아이템. 사용 중이면 그대로 200 |
| GET | `/api/v1/wardrobe/items?retired=` | `false`(기본, 사용 중만) · `true`(사용 중지만) · `all`. 그 밖은 400 `retired` |
| DELETE | `/api/v1/wardrobe/items/{id}` | 완전 삭제 (그대로) |

- 아이템 응답에 `retiredAt`(ISO 또는 null)·`retireReason`·`retireMemo`가 늘었다. 사용 중지한 옷도 단건 조회·PATCH가 된다. bulk 등록은 사용 중으로만 만든다.
- `/wardrobe/stats`의 기존 숫자는 사용 중인 옷만 센다. 새 필드 `retired`(수), `retiredByReason: [{ reason, count }]`(많은 순, 같으면 코드 순).
- AI 코디 추천 후보에서 빠지고, 고정·중심으로 주면 400 `VALIDATION_FAILED`("사용하지 않는 옷이에요."). 코드 삭제 가드(`CODE_IN_USE`)는 사용 중지한 옷도 센다(`retire_reason` 포함).

## 테스트

```bash
LC_ALL=C.UTF-8 ./gradlew test    # 테스트만
LC_ALL=C.UTF-8 ./gradlew check   # 포맷·Checkstyle까지 (커밋 전)
```

- 통합 테스트는 **Testcontainers**로 `pgvector/pgvector:pg17` 컨테이너를 한 번 띄워 쓴다 (Docker 필요). 서버를 무작위 포트로 띄워 실제 HTTP로 부른다.
- Docker 없이 이미 떠 있는 DB로 돌리려면 `WEARLY_TEST_DB_URL`을 준다. 테스트는 매번 새 이메일로 데이터를 만들고 지우지 않으므로 **개발 DB가 아닌 빈 DB**를 쓴다.

  ```bash
  docker exec wearly-postgres psql -U wearly -c "create database wearly_test"
  WEARLY_TEST_DB_URL=jdbc:postgresql://localhost:5433/wearly_test ./gradlew test
  # 계정이 다르면 WEARLY_TEST_DB_USERNAME / WEARLY_TEST_DB_PASSWORD
  ```

- 테스트는 기본적으로 목업 실행(`IntegrationTestBase`)이고, `AiProviderFallbackIntegrationTest`만 DB 공급자 실행이다. 실제 키도 `config/application-local.yml`도 쓰지 않는다 (test 태스크가 `WEARLY_LOCAL_CONFIG`를 없는 경로로 주고, `ANTHROPIC_API_KEY` 등을 빈 값으로 덮는다).
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
| `AdminAiProviderIntegrationTest` | 공급자 초기값·CRUD·`keyConfigured`·`usable`·검증(서버 설정 키 이름·options)·키 값 비노출·변경 이력 |
| `ProviderApiKeyIntegrationTest` | (마스터 키 있음) 웹 키 저장·교체·삭제, 키 출처 ENV→DB→ENV·NONE→DB, 암호문·nonce, 시험 분석이 DB 키로 호출, 재확인 403, 형식 400, verify(가짜 OpenAI·Gemini·Claude 서버의 모델 목록 경로·헤더) 실패 400, 공급자 삭제 시 비밀 행 삭제, 변경 이력 내용, 서버 출력·응답·이력에 키 0건 |
| `ProviderApiKeyNoMasterKeyIntegrationTest` | 마스터 키 없음 409 `SECRET_KEY_MISSING`(비밀번호 확인보다 먼저), `secretStoreEnabled: false` |
| `EnvApiKeyAdapterTest` | 서버 설정 키 찾기: `wearly.ai.keys.<이름>` → 같은 이름 속성·환경변수, 빈 값은 건너뜀, `*_API_KEY`가 아닌 이름 거부 (Spring 없음) |
| `AesGcmSecretCipherTest` · `ApiKeyRulesTest` · `ApiKeyServiceTest` | 암호화 왕복·nonce 매번 다름·AAD/마스터 키/암호문이 다르면 실패·마스터 키 없음 WARN·형식 오류 ERROR, 키 형식·출처 우선순위, 복호화 캐시·무효화·복호화 실패 시 쓸 수 없음 (Spring 없음) |
| `WardrobeRetireIntegrationTest` · `OutfitRetiredItemIntegrationTest` | (6차) 사용 중지 → 목록 `retired` 필터·다시 사용·시각 유지·PATCH, 통계(`retired`·`retiredByReason`), 완전 삭제와 차이, 남의 옷 404·입력 400, 비활성 이유 400·사용 중 코드 삭제 409, 회원 목록 `retiredItemCount`, DB CHECK, 코디 후보 제외·고정/중심 400·옷 부족 |
| `AppConfigIntegrationTest` | (6차) `/app/config` 공개·형식·ETag 304·`no-cache`·틀린 토큰 무시·CORS `ETag` 노출, 관리자 GET·PUT, version 변경, 같은 값 PUT, 400·403·401, 변경 이력 `APP_THEME_UPDATE` |
| `RetirementTest` · `ThemeSettingTest` · `AppSettingServiceTest` · `AdminAppThemeServiceTest` | (6차, Spring 없음) 사용 중지·다시 사용 규칙과 이유·메모 검증, 허용 테마·version, 테마 변경 저장·같은 값·400, 관리자 이메일·변경 이력 |
| `HexagonalArchitectureTest` | 08 문서 3장·7장 규칙 19개 (ArchUnit): 의존 방향, 컨텍스트 사이는 `port.in`만, common·공유 커널은 컨텍스트를 모름, 출력 어댑터는 API 오류를 모름, `@Entity`·`@Mapper` 위치, 검증·Jackson 어노테이션은 웹 어댑터만, 컨트롤러는 `*Response`만 반환, 포트 DTO는 어댑터를 모름 |
| `ApiResponseSnapshotTest` | (5차, 6차에 새 필드로 갱신) 155개 요청의 응답(상태·헤더 일부·본문)을 `src/test/resources/snapshots/api-responses.json`과 비교. 토큰·시각·버전 등 매번 다른 값만 정규화한다. 응답 모양을 바꾸면 실패한다. 파일을 지우고 돌리면 다시 만든다 |
| `*ServiceTest` (5차) | 서비스 단위 테스트(가짜 포트, Spring 없음): `AuthService`, `CodeManagementService`(색 짝·삭제 가드), `AdminUserService`(자기 정지 금지), `AdminAiService`(키 저장 순서), `AiCallRunner`(폴백·기록), `AiQuotaService`·`AiJsonGenerationService`(한도 예약), `WardrobeService` |
| `AiQuotaConcurrencyIntegrationTest` | (5차) 12개 동시 예약·동시 사진 분석에서 한도 3개만 성공, 오래된 예약 무시 |
| `CodeUsageSourceIntegrationTest` · `WardrobePatchIntegrationTest` · `AiLogSummaryMigrationTest` | (5차) 코드 사용 수를 옷장·프로필이 세는지, 옷장 PATCH 오류 모양, V7이 기존 기록 요약을 4차 문구대로 채우는지 |
| `CandidateValidatorTest` · `OutfitPromptTest` | (Spring 없음) 후보 검증: 정상·남의 아이템·칸 불일치·TOP+DRESS 혼합·신발 없음·고정/중심 누락·제외/피하고 싶은 색/세탁 중·중복·형식. 스키마: 칸별 enum이 그 칸의 내 아이템만·세탁 중 제외·고정·원피스 고정·null 허용 규칙, 프롬프트 내용, 모자란 칸, 고정 확인, 목업 결정성 |
| `OutfitRecommendIntegrationTest` | (목업) 응답 형식·규칙·결정성, 고정·중심·제외·색·후보 수, 옷 부족 400(기록 없음), 한도 429(사진 분석과 따로), 요청 검증 400·401, 기록 `feature`·관리자 AI 기록·대시보드 |
| `OutfitProviderFallbackIntegrationTest` | (DB 공급자, 가짜 OpenAI·Gemini·Claude 서버) 오류·남은 후보 0개면 다음 공급자, 각 사 요청 형식(시스템 지시·텍스트·스키마 enum), 실패 기록, 전부 실패 502·한도 미차감, 공급자 없으면 502, 공급자 `today`·대시보드 |
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
| 옷장 | 등록·bulk·PATCH·삭제·단건 조회 (`WardrobeItem`) | 동적 필터 목록, `/wardrobe/stats` 집계, 코드 사용 수 (`WardrobeItemMapper.xml`) |
| 프로필 | | 코드 사용 수 (`user/ProfileMapper.xml`) |
| 코드 | 관리자 추가·수정·순서·삭제 (`Code`) | 캐시 적재·version (`CodeMapper.xml`). 사용 수는 옷장·프로필이 `CodeUsageSource`로 센다 |
| AI | 호출 기록 저장 (`AiAnalysisLogEntity`), 공급자 설정 (`AiProviderEntity`), 웹 키 암호문 (`ProviderSecretEntity`), 한도 예약 (`AiUsageReservationEntity`) | 일일 사용량·한도 잠금 (`ai/AiQuotaMapper.xml`), 관리자 AI 기록 검색·공급자별 통계·대시보드 (`admin/AdminQueryMapper.xml`) |
| 변경 이력 | 기록 (`AuditLog`) | 목록 (`AdminQueryMapper.xml`) |

## 패키지 구조 (08 문서 2장·7장, 5차)

컨텍스트마다 같은 모양이다. 안쪽으로만 의존하고(`adapter` → `application` → `domain`), 규칙은 `HexagonalArchitectureTest`(ArchUnit)가 검사한다. 요청 하나가 지나가는 클래스 순서는 [`doc/10_백엔드_흐름.md`](../doc/10_백엔드_흐름.md).

```
app.wearly.<context>
├── domain/                        순수 자바 도메인 모델·불변식 (Spring·JPA·Jackson 없음, 어기면 DomainValidationException)
├── application/
│   ├── port/in/                   유스케이스 인터페이스 (XxxUseCase)
│   │   └── dto/                   유스케이스 입력(<동작><대상>Dto)·출력(<대상>Dto). 자바 타입 그대로
│   ├── port/out/                  출력 포트 (쓰기·단건 XxxPort, 조회 XxxQueryPort, 외부 호출)
│   │   └── dto/                   조회 포트가 돌려주는 도메인이 아닌 모양 (필요한 곳만)
│   └── service/                   유스케이스 구현 (@Service, @Transactional은 여기까지)
├── adapter/
│   ├── in/web/                    @RestController
│   │   ├── request/               XxxRequest: 요청 바인딩·Bean Validation·Jackson, toDto()
│   │   └── response/              XxxResponse: 응답 JSON 모양, static from(dto) (문자열 ID, 밀리초 ISO 시각)
│   ├── in/startup/                시작 시 실행        in/code/: code의 CodeUsageSource 구현 (wardrobe·user)
│   └── out/
│       ├── persistence/jpa/       XxxEntity · XxxJpaRepository · XxxJpaAdapter
│       ├── persistence/mybatis/   XxxMybatisAdapter, mapper/XxxMapper (XML: resources/mapper/<context>/), dto/XxxRowDto
│       └── <기술>/                 ai: ai/(claude·openai·gemini·mock, 공급자 모듈), env, crypto, transaction / auth: security
└── config/                        그 컨텍스트의 Spring 설정 빈 (auth: SecurityConfig, ai: AiConfig, outfit: OutfitConfig)
app.wearly.sharedkernel.code       공유 커널 (소유자 code): CodeSnapshot·CodeChecks·CodeGroups, Slots, DressCode, Seasons
app.wearly.common                  컨텍스트를 모른다 (ArchUnit)
├── domain/                        FieldError, DomainValidationException, Zones
├── application/                   Patch(안 보냄·값·형식 오류), ImageUpload
├── error/                         오류 코드·ApiException·전역 예외 처리
├── web/                           웹 어댑터 공용 (CurrentUser, PathIds, Uploads, Ids, Times, JsonPatches)
├── config/                        WearlyProperties, ClockConfig, CorsConfig
├── mybatis/                       TypeHandler, LikePatterns(LIKE 이스케이프, XML에서 <bind>)
├── persistence/                   PersistenceClock (엔티티 시각도 주입한 Clock)
└── transaction/                   AfterTransaction (커밋·완료 뒤 실행)
```

| 컨텍스트 | domain | 입력 포트 (주요) | 출력 어댑터 |
|---|---|---|---|
| `auth` | `RefreshToken`(회전·재사용 판단), `RefreshTokens`, `LocalCredential`, `EmailTakenException` | `AuthUseCase`, `AuthenticationUseCase`, `AccountUseCase` | JPA(자격 증명·Refresh 토큰), security(BCrypt, JWT 발급). 보안 필터 체인은 `auth.config.SecurityConfig` |
| `user` | `User`(이름·상태·역할 불변식), `UserStatus`, `UserRole`, `UserProfile`, `WorkType` | `UserUseCase`, `ProfileUseCase`, `ProfileCodeUsageUseCase` | JPA(회원·프로필), MyBatis(프로필 코드 사용 수) |
| `wardrobe` | `WardrobeItem`, `ItemInput`, `ItemRules`(불변식), `ItemCodeValidator`, `WardrobeStatsRules`, `Retirement`·`RetireRules`(6차 사용 중지) | `WardrobeUseCase`, `WardrobeStatsUseCase`, `ItemCodeUsageUseCase` | JPA(아이템 쓰기·단건), MyBatis(목록·통계·코드 사용 수) |
| `code` | `Code`, `CodeAttrs`, `CodeRules` (스냅샷·검증 정책은 공유 커널) | `CodeCatalog`, `CodeManagementUseCase`, `CodeUsageSource`(SPI: 옷장·프로필이 구현) | JPA(코드 쓰기), MyBatis(스냅샷 캐시) |
| `ai` | `AiProviderSetting`, `AiProviderSelection`, `AiRunMode`, `AiProviderType`, `ApiKeyRules`, `AnalysisPrompt`, `AiJsonPrompt`, `AiSuggestionValidator` … | `AnalyzeImageUseCase`, `ProviderTestUseCase`, `AiProviderAdminUseCase`, `AiProviderKeyUseCase`, `AiStatusUseCase`, `JsonGenerationUseCase` | JPA(공급자·호출 기록·웹 키·한도 예약), MyBatis(사용량·한도 잠금), ai/*(공급자 모듈: Claude SDK·OpenAI·Gemini REST·목업·키 확인), env, crypto(AES-GCM), transaction |
| `outfit` | `OutfitPool`, `OutfitPrompt`, `CandidateValidator`, `MockOutfitPlanner`, `OutfitRequest` … | `RecommendOutfitUseCase` (기능 이름 `OutfitFeatures`) | 없음 (옷장·프로필·코드는 각 입력 포트, 공급자는 ai의 `JsonGenerationUseCase`) |
| `appsetting` | `AppThemes`(허용 테마), `ThemeSetting`(변경·version) | `AppSettingUseCase` | JPA(`tb_app_setting`). 관리자 변경은 admin의 `AdminAppThemeService`가 이 포트로 부르고 이력을 남긴다 (6차) |
| `admin` | `AuditTargets`, `SearchText` | `AdminDashboardUseCase`, `AdminUserUseCase`, `AdminCodeUseCase`, `AdminAiProviderUseCase`, `AdminAppThemeUseCase`(6차), `EnsureAdminUseCase` | JPA(변경 이력 = `RecordAuditPort`), MyBatis(대시보드·목록 읽기 모델 → `port.out.dto`) |

- 컨텍스트 사이는 상대 `port.in`(과 `port.in.dto`)으로만 부른다. 관리자 API는 admin 컨텍스트가 code·ai·user·auth 유스케이스를 부르고, 같은 트랜잭션에서 변경 이력을 남긴다. 외부 HTTP가 끼는 웹 키 저장은 트랜잭션을 나눈다(`AdminAiKeyWriter`).
- 사진 분석 `POST /wardrobe/items/analyze`는 경로는 옷장이지만 ai 컨텍스트의 `AiAnalyzeController`가 받는다.
- `MultipartFile`은 웹 어댑터에서 `ImageUpload`(byte[]·MIME)로, PATCH JSON은 웹 어댑터가 필드마다 `Patch`로 바꿔 `PatchCodeDto`·`PatchProviderDto`·`PatchUserDto`·`PatchItemDto`로 넘긴다.
- 컨트롤러는 항상 `*Response`를 돌려준다. 응답 JSON은 5차 전과 바이트 단위로 같다(`ApiResponseSnapshotTest`). 6차에서 계약상 새 필드(아이템 `retiredAt`·`retireReason`·`retireMemo`, 통계 `retired`·`retiredByReason`, 회원 목록 `retiredItemCount`, 코드 그룹 `RETIRE_REASON`)와 변경 이력 `targetType` 오류 문구에 `APP_SETTING`이 더해져 스냅샷을 갱신했다.
