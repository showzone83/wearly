# Wearly 백엔드

웨어리 앱과 관리자 사이트의 API 서버. 계약 문서:

- [`doc/05_백엔드_API.md`](../doc/05_백엔드_API.md) — 1차: 토큰 로그인 · 옷장 · AI 분석
- [`doc/06_코드_관리.md`](../doc/06_코드_관리.md) — 2차: 옷 속성 값을 DB 코드 테이블로 (`GET /codes`, 코드 검증)
- [`doc/07_관리자_사이트.md`](../doc/07_관리자_사이트.md) — 2차: 관리자 역할·관리자 API·AI 공급자(Claude·OpenAI·Gemini) 관리, 3차: 6장 AI 키 웹 입력(쓰기 전용 · 암호화 저장)
- [`doc/08_백엔드_구조.md`](../doc/08_백엔드_구조.md) — 3차: 헥사고날 패키지 구조와 의존 규칙 (ArchUnit으로 검사), 4차: 5장 코드 스타일(Google Java Style)
- [`doc/09_AI_코디_추천.md`](../doc/09_AI_코디_추천.md) — 4차: AI 코디 추천 API (`POST /outfits/recommendations`)
- [`doc/12_디자인_테마.md`](../doc/12_디자인_테마.md) 3장 — 6차: 앱 설정(전체 사용자 테마) `GET /app/config`, `GET·PUT /admin/app/theme`
- [`doc/13_옷_사용_중지.md`](../doc/13_옷_사용_중지.md) — 6차: 사용하지 않는 옷(`retire`·`restore`, `retired` 필터, 통계) · 완전 삭제
- [`doc/18_사진_저장.md`](../doc/18_사진_저장.md) — 옷 사진 서버 저장: S3 호환 저장소(로컬 MinIO), 서명 URL 올리기·받기, `/images/**`, 아이템 `image`·`imageId`, `analyze {imageId}`, 클라우드 전환
- [`doc/20_오류_응답.md`](../doc/20_오류_응답.md) — 오류 응답: 문장 없이 `{ status, code, params?, fieldErrors, traceId }`, 오류 분류(기본·업무·시스템), `traceId`(`X-Request-Id`·MDC·로그 패턴), `error-codes.json` 카탈로그와 앱·관리자 문구 표 일치 검사, 새 코드 추가 순서
- [`doc/21_예정_코디_저장.md`](../doc/21_예정_코디_저장.md) — 예정 코디 서버 저장: `GET·POST·PATCH·DELETE /plans`, `POST /plans/bulk`, 검증·`PLAN_LIMIT`(500개)·`missingItemIds`, V14, 앱 동기화 규칙
- [`doc/22_관리자_계정_관리.md`](../doc/22_관리자_계정_관리.md) — 관리자 계정 보완(1단계, 구현함) + 회사 계정 로그인 SSO(2단계, 구현함: 범용 OIDC Authorization Code + PKCE, `/auth/sso/*`, V22, 로컬 Keycloak): 자동 승격 막기, 비밀번호 변경, 관리자 지정·해제(`LAST_ADMIN`), 2단계 인증 TOTP(`/auth/mfa/*`, `/admin/me/mfa/*`, V15·V16), 관리자 로그인 기록
- [`doc/23_D-1_알림.md`](../doc/23_D-1_알림.md) — 예정 코디 D-1 알림: `PUT·DELETE /me/push-token`, `notify` 컨텍스트, 매일 19:30 D-1 작업, Expo Push 어댑터, V17
- [`doc/12_디자인_테마.md`](../doc/12_디자인_테마.md) 7장 — 계절 테마 `seasonMode` (이슈 #6), [`doc/06_코드_관리.md`](../doc/06_코드_관리.md) — 나의 정보 코드 그룹(이슈 #5)·계절 코드 7개(이슈 #4)
- [`doc/16_세션_Valkey.md`](../doc/16_세션_Valkey.md) — 7차: Refresh 토큰·로그인 세션을 Valkey로, Access 토큰 `sid` 확인, 관리자 로그인 사용자 보기·강제 로그아웃

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.1 (Java 21 toolchain, Gradle 9.8.1 wrapper, Kotlin DSL) |
| 데이터 접근 | Spring Data JPA (Hibernate 7.4) + MyBatis (mybatis-spring-boot-starter 4.1.0) |
| DB | PostgreSQL 17 + pgvector, 스키마는 Flyway (`src/main/resources/db/migration`, V1~V17·V20~V22) |
| 사진 저장소 | S3 호환 오브젝트 스토리지: AWS SDK v2 `S3Client`·`S3Presigner`. 로컬은 MinIO 포크 `pgsty/minio`, 운영은 AWS S3·Cloudflare R2·네이버 클라우드. 사진 처리는 JDK ImageIO + metadata-extractor (18 문서) |
| 세션 저장소 | Valkey 9.1 (Redis 호환), `spring-boot-starter-data-redis`(Lettuce). Refresh 토큰·로그인 세션 (16 문서) |
| 인증 | Spring Security OAuth2 Resource Server, JWT HS256 (Nimbus) + 매 요청 `sid` 세션 확인 |
| AI | Claude: Anthropic Java SDK 2.73.0 / OpenAI·Gemini: 각 사 REST API를 Spring `RestClient`로 (SDK 없음) |
| 구조 | 헥사고날 (컨텍스트별 domain · application · adapter), 규칙은 ArchUnit 1.5.1 테스트 |
| 키 암호화 | AES-256-GCM (JDK `javax.crypto`), 마스터 키 `wearly.secret-key` (환경변수 `WEARLY_SECRET_KEY`) |
| 설정 | 공통 `application.yml` + 로컬 비밀 `config/application-local.yml` (git 제외, `.env` 쓰지 않음, 08 문서 6장) |
| 로컬 실행 | 루트 `docker-compose.yml`: db · valkey · minio(+ minio-init) · backend · admin |
| 코드 스타일 | Google Java Style: Spotless 8.10.4 + google-java-format 1.36.1, Checkstyle 14.3.0 (`config/checkstyle/google_checks.xml`) |

## 실행

처음 한 번, 로컬 비밀 설정 파일을 만든다 (git 제외, 아래 "설정"):

```bash
cd backend
cp config/application-local.example.yml config/application-local.yml   # AI 키·관리자 계정·마스터 키를 채운다
chmod 600 config/application-local.yml
```

### 전체 실행: docker compose (DB · Valkey · MinIO · 백엔드 · 관리자 사이트)

저장소 루트에서:

```bash
docker compose up -d --build              # 전체 빌드·실행: db(5433) · valkey(6380) · minio(9000·9001) · backend(8080) · admin(5173)
docker compose logs -f backend            # 백엔드 로그 보기 (admin, db, valkey, minio도 같은 방식)
docker compose up -d --build backend      # 백엔드만 다시 빌드·재시작 (코드 수정 후)
docker compose down                       # 중지 (DB 데이터는 wearly-pgdata, 로그인 세션은 wearly-valkeydata, 옷 사진은 wearly-miniodata 볼륨에 남음)
```

- **앱(Expo)은 compose에 없다.** 시뮬레이터·휴대폰이 Mac의 Metro에 붙어야 하므로 Mac에서 `cd mobile && npx expo start`를 따로 띄운다. 앱은 `http://<Mac>:8080`의 compose 백엔드를 그대로 쓴다.
- 백엔드 이미지(`backend/Dockerfile`): 빌드는 `eclipse-temurin:21-jdk`에서 `./gradlew bootJar -x test`(Gradle 캐시는 BuildKit 캐시 마운트), 실행은 `eclipse-temurin:21-jre`의 비루트 사용자 `wearly`, 작업 디렉터리 `/app`. 이미지 빌드에서는 테스트를 돌리지 않으니 테스트는 Mac에서 `./gradlew test`.
- compose는 `--spring.profiles.active=docker`로 띄운다 (`application-docker.yml`: DB 주소 `db:5432`, Valkey `valkey:6379`, 사진 저장소 `http://minio:9000`, 비밀 값 없음). 비밀 값은 `backend/config/` 폴더를 `/app/config`에 **읽기 전용으로 마운트**해서 `application-local.yml`을 읽는다. `backend/.dockerignore`가 이 파일을 빼므로 이미지에는 들어가지 않는다.
- 헬스체크는 `/actuator/health`(Valkey 상태 포함). 시작 순서는 db·valkey·minio(healthy)·minio-init(버킷 만들기 끝) → backend → admin.
- MinIO(옷 사진, 18 문서): 이미지 `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`(공식 `minio/minio`는 받을 수 없어 커뮤니티 포크를 쓴다), 컨테이너 `wearly-minio`, S3 API 9000·웹 콘솔 9001(http://localhost:9001), 로컬 전용 계정 `wearly` / `wearly-minio-local`, 볼륨 `wearly-miniodata`, CORS는 환경변수 `MINIO_API_CORS_ALLOW_ORIGIN`. `minio-init`(`pgsty/mc`)이 비공개 버킷 `wearly-images`를 만들고 끝난다(Exited 0). 들여다보기는 doc/14 8장.
- Valkey: 이미지 `valkey/valkey:9.1`, 컨테이너 `wearly-valkey`, 호스트 6380, AOF(`--appendonly yes`), 헬스체크 `valkey-cli ping`. 들여다보기는 `docker exec -it wearly-valkey valkey-cli` (doc/14 7장).
- 관리자 SSO 시험용 Keycloak(22 문서 4.2.5)은 **profile `sso`**라 평소 `docker compose up`에는 뜨지 않는다: `docker compose --profile sso up -d keycloak`(이미지 `quay.io/keycloak/keycloak:26.8.1`, 컨테이너 `wearly-keycloak`, 호스트 8180, 콘솔 `admin` / `wearly-keycloak-local`, realm `infra/keycloak/wearly-realm.json`, 모두 로컬 전용 시험 값). 백엔드를 켤 설정은 위 "관리자 회사 계정 로그인 (SSO)". 끄기 `docker compose --profile sso stop keycloak`, 처음 상태로 `docker compose --profile sso rm -sf keycloak`. compose 안의 백엔드는 `application-docker.yml`의 `wearly.sso.backchannel-rewrites`로 서버끼리 부르는 주소만 `keycloak:8080`으로 바꾼다.
- 관리자 사이트(`admin/Dockerfile`, 빌드 컨텍스트는 저장소 루트)는 nginx가 빌드 결과를 서빙한다. API 주소는 빌드 인자 `VITE_API_URL`(기본 `http://localhost:8080`). 관리자 사이트를 vite(`npm run dev`, 5173)로 띄울 때는 포트가 겹치므로 `docker compose up -d --build db backend`만 띄운다.

### Mac에서 직접 실행

```bash
docker compose up -d db valkey minio minio-init   # 저장소 루트에서 DB(localhost:5433, DB·계정·비밀번호 wearly)·Valkey(localhost:6380)·사진 저장소 MinIO(localhost:9000)만
cd backend
./gradlew bootRun         # 관리자 화면의 AI 공급자 설정(DB)대로 사진 분석
./gradlew bootRunMock     # 목업 공급자로 사진 분석 (키 불필요, WEARLY_AI_PROVIDER=mock)
```

compose의 backend가 떠 있으면 8080이 겹치므로 먼저 `docker compose stop backend`.

- 서버: `http://localhost:8080`, API 기본 경로 `/api/v1`, 상태 확인 `GET /actuator/health`
- 처음 뜰 때 Flyway가 `V1__init.sql` → `V2__codes.sql`(코드 테이블 + 초기값 102개) → `V3__admin_ai.sql`(역할·AI 공급자·변경 이력) → `V4__ai_provider_secret.sql`(웹 입력 AI 키 암호문) → `V5__outfit_recommend.sql`(AI 기록 feature에 `OUTFIT_RECOMMEND`, 한도 인덱스를 기능별로) → `V6__ai_usage_reservation.sql`(5차: 진행 중 AI 호출 예약, 동시 요청 한도) → `V7__ai_log_summary.sql`(5차: feature를 형식 CHECK로, 기록 요약 `result_summary` 추가·기존 행 채움) → `V8__app_setting.sql`(6차: 앱 설정 `tb_app_setting`, 초기 테마 `nyang`) → `V9__wardrobe_item_retire.sql`(6차: 옷 사용 중지 컬럼 `retired_at`·`retire_reason`·`retire_memo`, 부분 인덱스, 코드 그룹 `RETIRE_REASON` 5개) → `V10__auth_session_valkey.sql`(7차: `tb_refresh_token` 삭제 — 기존 로그인은 끊김, 로그인 이력 `tb_auth_event`) → `V11__weather.sql`(날씨, 17 문서) → `V12__item_image_storage.sql`(옷 사진: V1의 빈 `tb_item_image`를 지우고 다시 만듦, 18 문서) → `V13__place_weather.sql`(장소별 날씨: `tb_weather_place_snapshot`·`tb_weather_climate`, `tb_weather_call_log`의 `run_id`·`region_code` NULL 허용과 `call_kind` 4종, 19 문서)을 적용한다. 1차 DB에 있던 데이터는 그대로 쓴다 (기존 값은 모두 새 코드에 있다). → `V14__planned_outfit.sql`(예정 코디, 21 문서) → `V15__admin_mfa.sql`(관리자 2단계 인증 TOTP 비밀·복구 코드, 22 문서) → `V16__auth_event_mfa.sql`(로그인 이력 종류 4개 추가·최신순 인덱스) → `V17__push_token.sql`(푸시 토큰·D-1 발송 기록, 23 문서) → `V20__item_colors_purchase.sql`(이슈 #4: 보조 색·구입일, 계절 코드 3개) → `V21__user_personal_info.sql`(이슈 #5: 나의 정보 칸, 코드 3그룹) → `V22__admin_sso.sql`(관리자 회사 계정 로그인 SSO: `tb_user_sso`, 로그인 이력 `SSO_LOGIN`·`SSO_DENIED`와 `sso_provider`·`sso_amr`, 22 문서 4.2)을 적용한다. V18·V19 번호는 비어 있다(캐시 토큰 칸이 V19 예정, 18 문서 13.4). JPA는 `ddl-auto: validate`.
- **AI 공급자 고르기** (07 문서 3장)
  - `bootRunMock`(`WEARLY_AI_PROVIDER=mock`)이면 언제나 목업. DB 설정은 무시한다.
  - 그 밖(`bootRun`, 기본 `db`)에는 `tb_ai_provider`에서 사용(`enabled`)·모델·키를 모두 갖춘 공급자를 `priority` 순으로 시도하고, 실패(시간 초과·HTTP 오류·응답 검증 실패)하면 다음 공급자로 넘어간다.
  - 키는 **웹에서 넣은 키(DB) → 서버 설정 키(`wearly.ai.keys.<api_key_env>` → 같은 이름 환경변수) → 없음** 순서로 고른다 (아래 "AI 키 관리").
  - 쓸 수 있는 공급자가 없어도 서버는 **뜬다**. 시작 로그에 WARN이 남고, 사진 분석·AI 코디 추천은 502 `AI_UNAVAILABLE`이다 (목업으로 대신하지 않는다). 관리자 사이트에서 고칠 수 있게 하려는 것이다.
  - 시작 직후 로그: `AI 공급자 (시도 순서): claude(CLAUDE, model=…, priority=1, key=ENV), …` 또는 `쓸 수 있는 AI 공급자가 없습니다 …` 또는 `AI 공급자: mock (목업 …)`.
  - 처음 상태: `claude`(사용, `claude-opus-5-5`, 키 `ANTHROPIC_API_KEY`), `openai`·`gemini`(꺼짐, 모델 비움). 그래서 `config/application-local.yml`의 `wearly.ai.keys.ANTHROPIC_API_KEY`만 넣으면 1차와 같이 Claude로 돈다.
- **관리자 계정**: `wearly.admin.email`·`wearly.admin.password`(환경변수 `WEARLY_ADMIN_EMAIL`·`WEARLY_ADMIN_PASSWORD`)가 있고 **관리자가 한 명도 없을 때만** 시작할 때 그 계정을 만든다(이름 `wearly.admin.name`, 기본 "관리자"). 그 이메일로 이미 가입한 일반 회원은 관리자로 올리지 않는다(경고 로그만). 비밀번호는 첫 생성에만 쓰고 이후에는 관리자 사이트 "내 계정"에서 바꾼다. 가입 API로는 관리자를 만들 수 없다 (22 문서 4.1.1).
- **관리자 2단계 인증**: 관리자 사이트 로그인은 기본(`wearly.admin.mfa.required: true`)으로 TOTP(인증 앱) 등록·코드 확인을 거친다. 비밀을 마스터 키(`wearly.secret-key`)로 암호화하므로 **켜려면 마스터 키가 꼭 필요**하다. 로컬에서 번거로우면 `application-local.yml`에 `wearly.admin.mfa.required: false`(등록한 관리자는 그래도 코드를 확인한다). 흐름·휴대폰 분실 대처는 22 문서 4.1.4~4.1.7.
- **관리자 회사 계정 로그인 (SSO)**: `wearly.sso.enabled`(기본 `false`)를 켜고 `wearly.sso.providers.<id>`에 OIDC 공급자(`issuer-uri`·`client-id`·`client-secret`·`admin-groups`·`redirect-allowlist`)를 적으면 관리자 로그인 화면에 "<display-name> 로그인" 버튼이 생긴다. 견본(Keycloak·Entra ID·Google Workspace)은 `config/application-local.example.yml`의 `sso:` 주석. 설정이 모자란 공급자는 서버를 멈추지 않고 빼며 ERROR 로그를 남기고, 비밀번호 + TOTP 비상 계정은 그대로 들어온다. 로컬 시험은 `docker compose --profile sso up -d keycloak`(8180, 22 문서 4.2.5). 흐름·계정 연결·한계는 22 문서 4.2.
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
    # mfa:
    #   required: false           # 관리자 2단계 인증을 끈다 (로컬 개발용, 기본 true)
  # sso:                          # 관리자 회사 계정 로그인 (22 문서 4.2). 견본 전체는 application-local.example.yml
  #   enabled: true
  #   providers:
  #     keycloak:
  #       display-name: "회사 계정 (Keycloak)"
  #       issuer-uri: http://localhost:8180/realms/wearly
  #       client-id: wearly-admin
  #       client-secret: "wearly-admin-local-secret"   # 로컬 Keycloak 시험 값. 실제 IdP 비밀은 환경변수로
  #       admin-groups: [wearly-admins]
  #       redirect-allowlist: [http://localhost:5173/sso/callback, http://localhost:5173/login]
```

아래 값은 OS 환경변수(왼쪽 이름)로도 덮어쓸 수 있다 (운영 서버·CI). 같은 항목을 yml과 환경변수에 함께 두지 않는다.

| 환경변수 | yml 속성 | 기본값 | 설명 |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5433/wearly` | DB 주소 (compose는 `db:5432`) |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `wearly` | |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `wearly` | |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | `localhost` | Valkey 주소 (compose는 `valkey`) |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | `6380` | (compose는 `6379`) |
| `SPRING_DATA_REDIS_DATABASE` | `spring.data.redis.database` | `0` | 하네스는 `1` |
| `SERVER_PORT` | `server.port` | `8080` | |
| `WEARLY_JWT_SECRET` | `wearly.jwt.secret` | 개발용 고정값 | HS256 키, 32바이트 이상. prod 프로필에서는 필수 |
| `WEARLY_ACCESS_TTL` | `wearly.jwt.access-ttl` | `PT30M` | Access 토큰 수명 (ISO-8601 Duration) |
| `WEARLY_REFRESH_TTL` | `wearly.jwt.refresh-ttl` | `P30D` | Refresh 토큰 수명 |
| `WEARLY_AI_PROVIDER` | `wearly.ai.provider` | `db` | `db`(관리자 화면 설정) · `mock`. 1차 값 `claude`는 `db`와 같다. 그 밖의 값(예전 `auto` 포함)은 시작 실패 |
| `WEARLY_AI_DAILY_LIMIT` | `wearly.ai.daily-limit` | `10` | 회원별 하루 사진 분석 횟수 (Asia/Seoul 날짜, 성공만, 관리자 시험 분석 제외) |
| `WEARLY_AI_OUTFIT_DAILY_LIMIT` | `wearly.ai.outfit-daily-limit` | `20` | 회원별 하루 AI 코디 추천 횟수 (사진 분석과 따로 센다, Asia/Seoul 날짜, 성공만) |
| `WEARLY_AI_TIMEOUT` | `wearly.ai.timeout` | `PT60S` | 공급자 호출 제한 시간 기본값. 공급자 `options.timeoutSeconds`가 있으면 그 값 |
| `WEARLY_AI_IMAGE_MAX_SIDE` | `wearly.ai.image-max-side` | `1024` | 사진 분석에서 AI 공급자에 보내기 전에 줄이는 긴 변 픽셀 (64~8192, multipart·`{imageId}` 모두). 이미 작거나 JPEG·PNG가 아니면 그대로 보낸다. 18 문서 3.1·7.4 |
| `WEARLY_AI_IMAGE_QUALITY` | `wearly.ai.image-quality` | `0.8` | 줄인 사진의 JPEG 품질 (0.1~1.0) |
| `WEARLY_AI_PROMPT_CACHE_ENABLED` | `wearly.ai.prompt-cache.enabled` | `true` | Claude 요청의 고정 지시문(system) 끝에 `cache_control`(ephemeral, 5분)을 붙인다. OpenAI·Gemini는 같은 앞부분을 공급자가 자동으로 캐시해 이 값과 상관없다. 호출 성공 로그에 `cacheRead`·`cacheWrite`가 찍힌다. 18 문서 13장 |
| `WEARLY_AI_ALLOW_BASE_URL` | `wearly.ai.allow-base-url` | `false` | `true`일 때만 공급자 `options.baseUrl`(API 주소 바꾸기)을 허용. 키가 다른 주소로 갈 수 있어 운영에서는 끈다 |
| (환경변수 없음) | `wearly.ai.model-catalog.<CLAUDE\|OPENAI\|GEMINI>` | `application.yml`의 Claude 3개 | 공급자 종류별 내장 모델 목록 `[{ id, display-name, recommended }]`. 관리자 모델 드롭다운이 키가 없거나 제공사 목록을 받지 못했을 때 보여 주고, 제공사 목록에서도 같은 ID에 추천 표시를 붙인다. 종류 이름은 대소문자 무시, 모르는 종류·형식이 틀린 ID는 시작 실패 (doc/07 4장) |
| `ANTHROPIC_API_KEY` | `wearly.ai.keys.ANTHROPIC_API_KEY` | (없음) | Claude 키. 처음 상태의 `claude` 행이 가리키는 이름. yml 값이 먼저 |
| `OPENAI_API_KEY` | `wearly.ai.keys.OPENAI_API_KEY` | (없음) | OpenAI 키. `openai` 행이 가리키는 이름 |
| `GEMINI_API_KEY` | `wearly.ai.keys.GEMINI_API_KEY` | (없음) | Gemini 키. `gemini` 행이 가리키는 이름 |
| `WEARLY_ADMIN_EMAIL` / `WEARLY_ADMIN_PASSWORD` | `wearly.admin.email` / `wearly.admin.password` | (없음) | 둘 다 있고 관리자가 한 명도 없을 때만 시작 시 그 계정을 만든다 (기존 일반 회원은 올리지 않음) |
| `WEARLY_ADMIN_NAME` | `wearly.admin.name` | `관리자` | 새로 만들 때의 이름 |
| `WEARLY_ADMIN_MFA_REQUIRED` | `wearly.admin.mfa.required` | `true` | 관리자 사이트 로그인에 2단계 인증(TOTP) 필수. `false`면 등록하지 않은 관리자는 비밀번호만으로 들어온다 (등록한 관리자는 코드 확인). `true`는 마스터 키 필요. 22 문서 4.1.4 |
| `WEARLY_ADMIN_MFA_ISSUER` | `wearly.admin.mfa.issuer` | `Wearly` | 인증 앱에 보이는 발급자 이름 |
| `WEARLY_SSO_ENABLED` | `wearly.sso.enabled` | `false` | 관리자 사이트 회사 계정 로그인(SSO) 켜기. 22 문서 4.2 |
| `WEARLY_SSO_TRUST_IDP_MFA` | `wearly.sso.trust-idp-mfa` | `true` | IdP가 MFA를 맡는다고 보고 SSO 세션에 앱 TOTP를 묻지 않는다. `false`면 앱 TOTP도 거친다 |
| `WEARLY_SSO_HTTP_TIMEOUT` | `wearly.sso.http-timeout` | `PT10S` | IdP(discovery·token·JWKS) 호출 제한 시간 |
| `WEARLY_SSO_PROVIDERS_<ID>_CLIENTSECRET` 등 | `wearly.sso.providers.<id>.*` | (없음) | 공급자 설정(`display-name`·`issuer-uri`·`discovery-uri`·`client-id`·`client-secret`·`scopes`·`roles-claim`·`admin-groups`·`admin-emails`·`redirect-allowlist`·`callback-uri`·`assume-email-verified`·`enabled`). 환경변수 이름은 점을 `_`로, 하이픈을 빼서 만든다(`client-secret` → `…_CLIENTSECRET`, `_CLIENT_SECRET`은 묶이지 않는다). `wearly.sso.backchannel-rewrites`(서버끼리 부르는 IdP 주소 바꾸기)는 `application-docker.yml`에 있다 |
| `WEARLY_NOTIFY_PUSH_ENABLED` | `wearly.notify.push.enabled` | `false` | D-1 알림 서버 푸시 사용. false면 토큰은 받지만 D-1 작업을 예약하지 않는다. 23 문서 6장 |
| `WEARLY_NOTIFY_PUSH_PROVIDER` | `wearly.notify.push.provider` | `log` | `expo`(Expo Push API로 보냄) · `log`(보내지 않고 건수만 로그) |
| `WEARLY_NOTIFY_EXPO_BASE_URL` | `wearly.notify.push.expo.base-url` | `https://exp.host` | Expo Push API 주소 |
| `WEARLY_NOTIFY_EXPO_ACCESS_TOKEN` | `wearly.notify.push.expo.access-token` | (없음) | Expo "enhanced push security"를 켰을 때만. 실제 값은 `application-local.yml`에 |
| `WEARLY_NOTIFY_EXPO_TIMEOUT` | `wearly.notify.push.expo.timeout` | `PT10S` | Expo 호출 제한 시간 |
| `WEARLY_NOTIFY_DAY_BEFORE_TIME` | `wearly.notify.day-before.time` | `19:30` | D-1 작업 시각 `HH:mm` (한국 시간) |
| `WEARLY_NOTIFY_DAY_BEFORE_SCHEDULER_ENABLED` | `wearly.notify.day-before.scheduler-enabled` | `true` | false면 D-1 작업을 예약하지 않는다 (테스트용) |
| `WEARLY_SECRET_KEY` | `wearly.secret-key` | (없음) | 웹에서 넣은 AI 키를 암호화하는 마스터 키. **base64로 인코딩한 32바이트** (`openssl rand -base64 32`). 없으면 웹 키 입력이 꺼진다 |
| `WEARLY_LOCAL_CONFIG` | — | `./config/application-local.yml` | 로컬 비밀 설정 파일 경로 |
| `WEARLY_STORAGE_ENDPOINT` | `wearly.storage.endpoint` | `http://localhost:9000` | 사진 저장소 주소 (백엔드가 붙는 주소. compose는 `http://minio:9000`, AWS S3는 비움). 18 문서 3장 |
| `WEARLY_STORAGE_REGION` | `wearly.storage.region` | `us-east-1` | 서명 리전 (R2 `auto`, AWS `ap-northeast-2` …) |
| `WEARLY_STORAGE_BUCKET` | `wearly.storage.bucket` | `wearly-images` | 버킷 이름 (비공개) |
| `WEARLY_STORAGE_ACCESS_KEY` / `WEARLY_STORAGE_SECRET_KEY` | `wearly.storage.access-key` / `secret-key` | `wearly` / `wearly-minio-local` | 로컬 MinIO 계정 (로컬 전용). 클라우드 키는 `application-local.yml`·배포 비밀 관리에. AWS는 비우면 기본 자격 증명 체인 |
| `WEARLY_STORAGE_PATH_STYLE` | `wearly.storage.path-style` | `true` | MinIO·R2·NCP true, AWS S3 false |
| `WEARLY_STORAGE_PUBLIC_ENDPOINT` | `wearly.storage.public-endpoint` | (빔) | 서명 URL 주소. 비우면 요청 Host 이름 + `endpoint` 포트(로컬 시뮬레이터 localhost, 휴대폰 LAN IP). **R2·NCP 같은 클라우드는 `endpoint`와 같은 값을 꼭 넣는다** |
| `WEARLY_STORAGE_AUTO_CREATE_BUCKET` | `wearly.storage.auto-create-bucket` | `false` | true면 시작 때 버킷이 없으면 만든다 (테스트·하네스용) |
| `WEARLY_STORAGE_UPLOAD_URL_TTL` | `wearly.storage.upload-url-ttl` | `PT10M` | 업로드(PUT) 서명 URL 수명 |
| `WEARLY_STORAGE_DOWNLOAD_URL_TTL` | `wearly.storage.download-url-ttl` | `PT1H` | 받기(GET) 서명 URL 수명 |
| `WEARLY_STORAGE_MAX_UPLOAD_BYTES` | `wearly.storage.max-upload-bytes` | `10MB` | 올릴 수 있는 최대 크기 (complete에서 검사) |
| `WEARLY_STORAGE_DAILY_UPLOAD_LIMIT` | `wearly.storage.daily-upload-limit` | `200` | 회원별 하루 upload-url 발급 수 (Asia/Seoul 날짜, 상태 무관) |
| `WEARLY_STORAGE_MAX_PIXELS` | `wearly.storage.max-pixels` | `40000000` | 디코딩 전에 막는 가로×세로 상한 |
| `WEARLY_STORAGE_PROCESSING_CONCURRENCY` | `wearly.storage.processing-concurrency` | `2` | 동시에 처리하는 사진 수 |
| `WEARLY_STORAGE_STALE_AFTER` | `wearly.storage.stale-after` | `P1D` | 아이템에 안 붙은 사진을 정리 대상으로 보는 시간 |
| `WEARLY_STORAGE_CLEANUP_ENABLED` | `wearly.storage.cleanup-enabled` | `true` | false면 정리 주기 작업을 끈다 (테스트·하네스) |
| `WEARLY_STORAGE_CLEANUP_INTERVAL` | `wearly.storage.cleanup-interval` | `PT1H` | 정리 작업 간격 (시작 5분 뒤 첫 실행) |
| `WEARLY_STORAGE_ORIGINAL_MAX_SIDE` | `wearly.storage.original-max-side` | `1600` | 저장하는 `original.jpg`의 긴 변 최대 픽셀 (64~8192). 바꿔도 이미 저장된 사진은 그대로 |
| `WEARLY_STORAGE_ORIGINAL_QUALITY` | `wearly.storage.original-quality` | `0.8` | `original.jpg` JPEG 품질 (0.1~1.0) |
| `WEARLY_STORAGE_THUMB_MAX_SIDE` | `wearly.storage.thumb-max-side` | `400` | 목록용 `thumb.jpg`의 긴 변 (16~4096) |
| `WEARLY_STORAGE_THUMB_QUALITY` | `wearly.storage.thumb-quality` | `0.8` | `thumb.jpg` JPEG 품질 (0.1~1.0) |
| `WEATHERAPI_KEY` | `wearly.weather.keys.WEATHERAPI_KEY` | (없음) | WeatherAPI.com 키 (17 문서). 없으면 날씨 공급자는 "키 없음"으로 건너뛴다 |
| `WEARLY_WEATHER_PROVIDER` | `wearly.weather.provider` | `db` | `db`(관리자 날씨 공급자 설정) · `mock`(키 없이 목업 날씨). 그 밖은 시작 실패 |
| `WEARLY_WEATHER_TIMEOUT` | `wearly.weather.timeout` | `PT10S` | 날씨 공급자 호출 제한 시간 |
| `WEARLY_WEATHER_LOG_RETENTION_DAYS` | `wearly.weather.log-retention-days` | `90` | 날씨 실행·호출 이력 보관 일수 (매일 04:00 정리) |
| `WEARLY_WEATHER_SCHEDULER_ENABLED` | `wearly.weather.scheduler-enabled` | `true` | `false`면 날씨 주기 실행·시작 실행·이력 정리를 끈다 (테스트용). 주기·예보 일수 등 운영값은 DB와 관리자 화면에 있다 |
| `WEARLY_WEATHER_PLACE_CACHE_TTL` | `wearly.weather.place-cache-ttl` | `PT3H` | REGION 밖 좌표 예보 캐시를 그대로 쓰는 시간 (19 문서) |
| `WEARLY_WEATHER_ON_DEMAND_DAILY_LIMIT` | `wearly.weather.on-demand-daily-limit` | `30` | 회원마다 하루(한국 날짜)에 공급자를 실제로 부르게 할 수 있는 좌표 요청 수. 넘으면 예전 캐시 → 평년값 → 429 `WEATHER_LIMIT` |
| `WEARLY_WEATHER_SEARCH_CACHE_TTL` | `wearly.weather.search-cache-ttl` | `P1D` | 같은 검색어의 장소 검색 결과 캐시 (Valkey) |
| `WEARLY_WEATHER_SEARCH_DAILY_LIMIT` | `wearly.weather.search-daily-limit` | `100` | 회원마다 하루에 공급자를 실제로 부르게 할 수 있는 장소 검색 수 (캐시에서 준 검색은 세지 않음) |
| `WEARLY_WEATHER_CLIMATE_ENABLED` | `wearly.weather.climate.enabled` | `true` | 평년값(예보 밖 날짜) 사용. false면 `CLIMATE`를 주지 않는다 |
| `WEARLY_WEATHER_CLIMATE_BASE_URL` | `wearly.weather.climate.base-url` | `https://archive-api.open-meteo.com` | Open-Meteo 과거 기상 API. 무료는 비상업용 → 상업 전환 때 유료 주소(`customer-archive-api.open-meteo.com`)·키 또는 끄기 |
| `WEARLY_WEATHER_CLIMATE_API_KEY` | `wearly.weather.climate.api-key` | (없음) | Open-Meteo 유료 플랜 키 |
| `WEARLY_WEATHER_CLIMATE_YEARS` | `wearly.weather.climate.years` | `10` | 평년값에 쓰는 지난 완결 연도 수 (2026년이면 2016~2025) |
| `WEARLY_WEATHER_GEOCODING_ENABLED` | `wearly.weather.geocoding.enabled` | `true` | 장소 검색에 Open-Meteo 지오코딩(한글 검색어·한글 이름) 사용. false나 실패면 WeatherAPI 검색(영어 이름) |
| `WEARLY_WEATHER_GEOCODING_BASE_URL` | `wearly.weather.geocoding.base-url` | `https://geocoding-api.open-meteo.com` | 지오코딩 API 주소. 상업 전환 때 `customer-geocoding-api.open-meteo.com` |
| `WEARLY_WEATHER_GEOCODING_API_KEY` | `wearly.weather.geocoding.api-key` | (없음) | Open-Meteo 유료 플랜 키 |

- 로그: `logging.pattern.level`(`application.yml`)이 모든 로그 줄에 `[traceId=… userId=…]`를 붙인다. 오류 응답의 `traceId`·응답 헤더 `X-Request-Id`로 로그를 찾는다: `docker compose logs backend | grep <traceId>` (20 문서 4장). `server.error.*`는 응답에 예외 메시지·스택·바인딩 오류를 넣지 않게 모두 끈다.
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
| GET | `/admin/dashboard` | 회원 수·7일 신규·아이템 수·오늘 AI 호출·공급자별 7일 통계, 7차 `activeSessions`·`activeUsers24h` |
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
| GET·PATCH | `/admin/users`, `/admin/users/{id}` | 회원 검색(`q`·`status`·`role`) / `{ status?, role? }` (자기 자신의 역할·상태는 못 바꾼다, 정지한 회원은 관리자로 지정 못 함, 마지막 관리자 해제는 409 `LAST_ADMIN`). `itemCount`는 사용 중인 옷, `retiredItemCount`는 사용 중지한 옷(6차), `ageRange`·`bodyType`·`heightCm`·`fitPreference`(나의 정보, 조회만), `mfaEnrolled` |
| GET | `/admin/audit-logs` | 변경 이력 (`targetType` = `CODE`·`AI_PROVIDER`·`USER`·`APP_SETTING`·`SESSION`) |
| GET | `/admin/sessions` | 7차: 로그인 세션 목록 `?q=&client=&page=&size=` (lastSeenAt 최신순, `current` = 내 세션) |
| GET | `/admin/users/{id}/sessions` | 7차: `{ sessions, events }` — 그 회원의 세션 + 최근 로그인 이력 20개 |
| DELETE | `/admin/sessions/{sessionId}` | 7차: 세션 하나 강제 로그아웃 204 (내 현재 세션 400, 없으면 404). 이력 `SESSION_REVOKE` |
| DELETE | `/admin/users/{id}/sessions` | 7차: 회원의 모든 세션 강제 로그아웃 204 (자기 자신 400). 이력 `USER_SESSIONS_REVOKE` |
| GET·PUT | `/admin/app/theme` | 6차: 전체 사용자 테마·계절 모드 `{ themeId, seasonMode, updatedAt, updatedBy }` / `{ themeId?, seasonMode? }`(보낸 값만 바꿈. `nyang`·`closeta`, `seasonMode`는 `OFF`·`AUTO`·`SPRING`·`SUMMER`·`FALL`·`WINTER`, 그 밖 400). 바뀌면 이력 `APP_THEME_UPDATE` (12 문서 7장) |
| POST | `/admin/me/password` | 22 문서: `{ currentPassword, newPassword }` → 204. 틀리면 403 `REAUTH_FAILED`. 지금 세션 말고 내 다른 세션을 끊는다. 이력 `ADMIN_PASSWORD_CHANGE` |
| GET | `/admin/me/mfa` | 내 2단계 인증 상태 `{ enrolled, enrolledAt, recoveryCodesLeft, required }` |
| POST | `/admin/me/mfa/setup`·`/enable`·`/reset` | 등록 시작 `{ secret, otpauthUrl }` / `{ code }` → `{ recoveryCodes }` / `{ currentPassword, code \| recoveryCode }` → 204 (내 TOTP 지우고 다른 세션 끊기). 이력 `ADMIN_MFA_ENROLL`·`ADMIN_MFA_RESET` |
| DELETE | `/admin/users/{id}/mfa` | 22 문서: 다른 관리자의 2단계 인증 초기화 204 (그 회원 세션 모두 끊김, 자기 자신 400). 이력 `USER_MFA_RESET` |
| GET | `/admin/auth-events` | 22 문서: 관리자 로그인 기록 `?userId=&type=&from=&to=&page=&size=` (최신순, `type`은 `LOGIN`·`LOGOUT`·`REFRESH_REUSED`·`FORCED_LOGOUT`·`LOGIN_FAILED`·`MFA_SUCCESS`·`MFA_FAILED`·`MFA_LOCKED`) |

- 목록 응답은 `{ items, page, size, total }`, `page` 0부터, `size` 기본 20·최대 100.
- 정지(`SUSPENDED`) 회원: 로그인 403 `ACCOUNT_SUSPENDED`(비밀번호가 맞을 때만), 남은 Access 토큰 401 `TOKEN_INVALID`, refresh 401 `REFRESH_INVALID`(정지할 때 로그인 세션을 모두 끊는다. 풀어도 예전 토큰은 살아나지 않는다).
- 7차: Access 토큰에 `sid`(세션 ID)가 있고 매 요청 Valkey의 세션을 확인한다. 로그아웃·강제 로그아웃·정지·탈퇴·재사용 감지로 세션이 끊기면 남은 Access 수명과 상관없이 401 `TOKEN_INVALID`. `sid` 없는 옛 토큰도 401. 요청 헤더 `X-Wearly-Client: app|admin|web`(CORS 허용)로 기기 종류를 남긴다.
- 권한은 매 요청 DB의 역할로 판단한다 (JWT `role` 클레임은 표시용). 역할을 내리면 같은 토큰으로도 바로 403.
- 모든 관리자 변경은 `tb_admin_audit_log`에 before/after로 남는다.

### 인증 — 관리자 2단계 인증 (22 문서)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/auth/login` | 일반 회원·앱은 `AuthResponse`. **관리자 사이트 로그인(`X-Wearly-Client: admin`)에서 2단계 인증이 필요하면** 토큰 대신 `{ mfa: { required: true, enrolled }, mfaToken, mfaTokenExpiresIn: 300 }` |
| POST | `/api/v1/auth/mfa/setup` | `{ mfaToken }` → `{ secret, otpauthUrl }` (아직 등록하지 않았을 때, 마스터 키 없으면 409 `SECRET_KEY_MISSING`) |
| POST | `/api/v1/auth/mfa/verify` | `{ mfaToken, code \| recoveryCode }` → `AuthResponse` + `recoveryCodes`(처음 등록에만 10개)·`recoveryCodesLeft` |

- 대기 토큰은 5분이고 관리자 API에는 쓸 수 없다. 코드를 확인하지 않은 세션으로 `/admin/**`를 부르면 403 `MFA_REQUIRED`. 틀리면 400 `MFA_CODE_INVALID`(`remainingAttempts`), 5번 틀리면 15분 잠금 429 `MFA_LOCKED`(`retryAfterSeconds`). 로그인 이력에 `LOGIN_FAILED`·`MFA_SUCCESS`·`MFA_FAILED`·`MFA_LOCKED`가 남는다.

### 인증 — 관리자 회사 계정 로그인 SSO (22 문서 4.2, 로그인 없이 부르는 공개 경로)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/auth/sso/providers` | `{ enabled, providers: [{ id, displayName }] }`. 꺼져 있으면 `{ enabled: false, providers: [] }` |
| GET | `/api/v1/auth/sso/{provider}/start?redirect=` | IdP 로그인으로 302 (state·nonce·PKCE는 Valkey 5분 + 쿠키 `wearly_sso_state`). `redirect`가 허용 목록에 없으면 400 |
| GET | `/api/v1/auth/sso/{provider}/callback` | IdP가 돌려보내는 주소. 302로 관리자 사이트 `?code=<교환 코드>` 또는 `?error=<오류 코드>&status=…&traceId=…` |
| POST | `/api/v1/auth/sso/exchange` | `{ code }` → `/auth/login`과 같은 모양(교환 코드는 1분·1회, 아니면 401 `SSO_EXCHANGE_INVALID`) |
| GET | `/api/v1/auth/sso/{provider}/logout?redirect=` | IdP 로그아웃으로 302 |

- 꺼져 있으면(기본) `providers`만 200 빈 목록이고 나머지는 404 `NOT_FOUND`. SSO 오류 코드 7개(`SSO_*`)는 22 문서 4.2.8.

## API 요약 (6차에서 더한 것)

### 앱 설정 (12 문서 3장)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/app/config` | **공개**. `{ themeId, seasonMode, version }`(계절 모드 기본 `OFF`, 12 문서 7장). `version`은 마지막으로 바꾼 시각(ISO, 밀리초), 바꾼 적이 없으면 `"initial"`. `ETag: "<version>"`, `If-None-Match`가 같으면 304, `Cache-Control: no-cache`. 만료된 토큰이 같이 와도 무시 |

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

### 옷 사진 (18 문서)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/images/upload-url` | `{ purpose: 'ITEM', contentType: 'image/jpeg'\|'image/png', size }` → 200 `{ imageId, uploadUrl, method: 'PUT', headers, expiresAt }`. 입력 오류 400 `VALIDATION_FAILED`, 하루 한도 429 `IMAGE_UPLOAD_LIMIT` |
| POST | `/api/v1/images/{imageId}/complete` | 서명 URL로 올린 뒤 호출 → 200 `{ imageId, url, thumbUrl, width, height, expiresAt }`. 검증·EXIF 제거·축소(1600/400 JPEG, 설정 `wearly.storage.original-max-side`·`thumb-max-side`). 400 `IMAGE_INVALID`(그 imageId는 버려짐)·`IMAGE_NOT_UPLOADED`, 남의 이미지 404 |
| POST | `/api/v1/wardrobe/items/analyze` | multipart 그대로, 또는 JSON `{ imageId }`(내 READY 이미지) |
| POST·PATCH | `/api/v1/wardrobe/items`(·bulk), `/{id}` | `imageId?: string \| null`을 더했다 (PATCH `null` = 사진 빼기). 409 `IMAGE_IN_USE`, PENDING은 400 `IMAGE_NOT_UPLOADED` |

- 아이템 응답에 `image: { imageId, url, thumbUrl, expiresAt } | null`이 있다 (`url`·`thumbUrl`은 `download-url-ttl` 동안 유효한 GET 서명 URL).
- 버킷은 비공개이고 앱은 서명 URL로 저장소에 직접 올리고 받는다. 아이템 완전 삭제·사진 교체·회원 탈퇴는 객체도 지운다. 안 붙은 사진은 정리 작업이 지운다.
- 운영 저장소(AWS S3·Cloudflare R2·네이버 클라우드) 설정 예시는 `config/application-cloud.example.yml`, 전환 방법은 doc/18 8장.

### 오류 응답 (20 문서)

모든 오류는 `{ status, code, params?, fieldErrors: [{ field, code, params? }], traceId }`이고 **문장(`message`)은 없다.** 응답 헤더 `X-Request-Id`(보내면 형식이 맞을 때 그 값, 아니면 서버가 만든 값)가 `traceId`와 같다. 코드 51개(관리자 2단계 인증 8개·회사 계정 로그인 7개 포함, 20 문서 12·13장)·필드 오류 코드 21개의 기준은 `src/main/resources/error-codes.json`, 앱·관리자는 이 파일로 화면 문구 표를 맞춘다.

### 예정 코디 (21 문서, `V14__planned_outfit.sql`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/plans?from=&to=` | 내 예정 목록 (날짜 → 시간대 순, `YYYY-MM-DD` 양 끝 포함). 응답에 `missingItemIds` |
| POST | `/api/v1/plans` | 만들기 201. 같은 `clientId`의 예정이 있으면 고치고 200 |
| POST | `/api/v1/plans/bulk` | 기기에 있던 예정을 한 번에 (최대 100개, `clientId` 필수). 요청 순서대로 `{ items, created, updated }`, 틀린 항목이 있으면 하나도 저장하지 않음 |
| PATCH | `/api/v1/plans/{id}` | 보낸 필드만 바꾼다 (`null`은 비움, `clientId`는 무시) |
| DELETE | `/api/v1/plans/{id}` | 204 |

- 로그인 필요, 남의 예정 404. 회원당 500개, 넘으면 409 `PLAN_LIMIT`(`params.max`). 날짜는 오늘(한국) 앞뒤 1년. 코디(`outfit`)는 앱 `Outfit` 모양 그대로 JSONB에 저장하고, 서버는 `items`(칸 → 아이템 id)만 본다. 옷장에 없는 아이템은 `missingItemIds`로 알려 주고 코디는 그대로 둔다.
- 예정이 쓰는 STYLE·REGION 코드는 삭제할 수 없다 (409 `CODE_IN_USE`, `usages`의 `kind` = `PLAN`).

### 옷 색·구입일, 나의 정보, 푸시 토큰 (2026-10-18)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST·PATCH | `/api/v1/wardrobe/items`(·bulk), `/{id}` | 이슈 #4: `colors`(1~3개, 첫 번째가 주 색상, 중복 없음) 또는 예전 `color`(하나). 응답 `color` = `colors[0]`. `purchasedAt`(1990-01-01~오늘, 비우면 등록일[서울]을 넣고 `purchasedAtEstimated: true`). PATCH `color`만 오면 주 색상만 바꾸고 `purchasedAt: null`은 등록일로 되돌림 (05 문서 6장) |
| GET | `/api/v1/wardrobe/items?color=` | 이슈 #4: 주 색상이나 보조 색 중 하나라도 같으면 맞는다. `/wardrobe/stats`의 `byColor`는 주 색상 기준, 코드 삭제 가드(`CODE_IN_USE`)의 사용 수는 보조 색도 센다 |
| POST | `/api/v1/wardrobe/items/analyze` | 이슈 #4: 결과 `suggestion.colors`(눈에 띄는 색 1~3개, 첫 번째 = `color`) |
| GET·PUT | `/api/v1/users/me/profile` | 이슈 #5: `ageRange`·`bodyType`·`heightCm`(130~210)·`fitPreference`(모두 선택, PUT은 전체 교체라 빠지면 비움, 코드는 AGE_RANGE·BODY_TYPE·FIT 활성 코드로 검증). 코디 추천 AI의 사용자 메시지에 "회원 정보" 한 줄로 들어간다 |
| PUT | `/api/v1/me/push-token` | 23 문서: `{ token, platform, deviceId }` → 200 `{ deviceId, platform, serverPush, dayBeforeTime }`. 같은 기기면 바꾼다 |
| DELETE | `/api/v1/me/push-token?deviceId=` | 204 (없어도 204, `deviceId` 없으면 400) |

- 푸시 토큰: 로그인 필요, Expo 푸시 토큰 `ExponentPushToken[…]`만. `serverPush`는 `wearly.notify.push.enabled=true`이고 `provider=expo`일 때만 `true`(기본 `false`)다. 서버는 매일 19:30 KST(`wearly.notify.day-before.time`)에 내일 예정을 가진 회원(알림 `dMinus1`을 켠)에게 "내일 예정 코디"를 보내고, 장소 날씨를 다시 확인해 기온 3°C 이상·비 여부가 바뀌었으면 "예보가 바뀌었어요"로 보낸다. 같은 예정은 하루 한 번(`tb_push_send_log`). 기본 `log` 공급자는 보내지 않고 건수만 로그에 남긴다.

### 날씨 (17 문서, 8차)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/weather?region=` | 인증 불필요. 서버에 저장된 지역 날씨(현재 + 오늘부터 예보). ETag/304. 없는 지역 400, 받은 적 없으면 503 `WEATHER_UNAVAILABLE` |
| GET·PUT | `/api/v1/admin/weather/settings` | 스케줄 설정 (켜기·주기·예보 일수·차단 기준·차단 시간) + 다음 실행·마지막 실행 |
| GET·PATCH | `/api/v1/admin/weather/providers[/{code}]` | 공급자 설정·상태·이번 달 사용량. `POST …/reset` 차단 해제, `POST …/test` 시험 호출 |
| POST | `/api/v1/admin/weather/refresh` | 지금 실행 (202 `{runId}`). 409 `WEATHER_REFRESH_RUNNING`·`WEATHER_NO_PROVIDER` |
| GET | `/api/v1/admin/weather/runs[/{runId}]`, `/calls`, `/calls/summary`, `/snapshots[/{region}]` | 실행·호출 이력(`/calls`는 `callKind` = `FORECAST`·`ON_DEMAND`·`SEARCH`·`CLIMATE` 필터, 틀린 값 400), 요약(`/calls/summary`의 `auxiliaryMonths` = Open-Meteo 평년값·장소 검색의 이번 달 호출 수), 저장된 날씨 |

- 서버가 활성 REGION 지역의 날씨를 주기적으로 받아 `tb_weather_snapshot`에 둔다. 앱 요청은 외부 API를 부르지 않는다. 실행은 Valkey 잠금(`wearly:weather:refresh:lock`)으로 한 번에 하나만 돈다.
- 키 없이 화면을 보려면 `WEARLY_WEATHER_PROVIDER=mock ./gradlew bootRun`.

### 장소별 날씨 (19 문서, 앱 예정일 코디)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/weather/day?region=&date=` | 인증 불필요(틀린 토큰은 무시). 한 지역·한 날짜의 날씨. 저장된 스냅샷만 읽는다. 예보에 그 날이 있으면 `FORECAST`, 없으면 그 달 평년값 `CLIMATE`. 400(`date` 오늘~1년·`region`), 예보도 평년값도 없으면 503 `WEATHER_UNAVAILABLE` |
| GET | `/api/v1/weather/day?lat=&lon=&date=` | 로그인 필요(없으면 401). REGION 밖 좌표. 격자(소수 첫째 자리) 캐시 3시간, 오래됐으면 공급자를 한 번 부른다. 회원 하루 한도 초과 → 예전 캐시 → 평년값 → 429 `WEATHER_LIMIT`(`params.kind: ON_DEMAND`) |
| GET | `/api/v1/weather/places?q=` | 로그인 필요. 장소 검색(시·도 이름 맞추기 → Open-Meteo 지오코딩 → 실패 시 WeatherAPI 검색), 최대 10개, 1일 캐시 |

- 좌표 응답의 `place.regionCode`는 `null`, `lat`·`lon`은 격자 좌표다. 평년값은 Open-Meteo 과거 기상 API에서 지난 10개 완결 연도로 처음 필요할 때 계산해 `tb_weather_climate`에 12개월을 저장하고 400일 뒤 다시 계산한다.
- Valkey 키: `wearly:weather:lock:place:<격자>`·`…:lock:climate:<격자>`(60초 잠금), `wearly:weather:usage:<ON_DEMAND|SEARCH>:<날짜>:<userId>`(회원 하루 사용량), `wearly:weather:search:v3:<검색어>`(검색 캐시).
- 호출은 `tb_weather_call_log`에 `ON_DEMAND`(WeatherAPI)·`SEARCH`(`OPEN_METEO_GEO`)·`CLIMATE`(`OPEN_METEO`)로 남는다 (`run_id` 없음).
- **시·군·구 검색** (`4d65280`): 시·도 이름 다음, 지오코딩 전에 `src/main/resources/data/korea-districts.json`(시·군·구 268곳, 근사 좌표 25곳)에 맞춘다. 해운대·분당·부산 중구처럼 이름이 맞으면 지오코딩을 부르지 않고(회원 하루 검색 한도를 쓰지 않는다) `regionCode`는 `null`, 상위 시·도는 `parentRegionCode`로 준다. 자료는 `scripts/build-districts.py`로 만든다(행정구역이 바뀔 때만 사람이 실행, 테스트에서는 실행하지 않음). 19 문서 4장.
- Open-Meteo의 무료 API는 비상업용이다. 상업 서비스에서는 유료 주소·키를 넣거나 `climate.enabled`·`geocoding.enabled`를 끈다 (`backend/config/application-local.example.yml` 주석).

## 테스트

```bash
LC_ALL=C.UTF-8 ./gradlew test    # 테스트만
LC_ALL=C.UTF-8 ./gradlew check   # 포맷·Checkstyle까지 (커밋 전)
```

- 통합 테스트는 **Testcontainers**로 `pgvector/pgvector:pg17`, `valkey/valkey:9.1`(compose와 같은 이미지, `support/TestValkey`), `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`(compose와 같은 사진 저장소 이미지, `support/TestMinio`, 테스트 버킷은 서버가 시작할 때 만든다) 컨테이너를 한 번 띄워 쓴다 (Docker 필요). 서버를 무작위 포트로 띄워 실제 HTTP로 부른다. 응답 스냅샷 테스트는 Valkey DB 2를 비우고 쓴다. 떠 있는 Valkey를 쓰려면 `WEARLY_TEST_VALKEY_HOST`(·`WEARLY_TEST_VALKEY_PORT`).
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
| `AuthIntegrationTest` | 가입·로그인·토큰·refresh 회전·재사용 감지·탈퇴, 가입 회원의 `role=USER`와 JWT `role`·`sid` 클레임, Valkey 키(`wearly:rt:*` 상태·`wearly:session:*`), `sid` 없는 토큰 401, 로그아웃 즉시 401, CORS(5173 포함), health |
| `ShortAccessTtlIntegrationTest` | Access 수명 1초로 띄워 실제 만료 후 refresh (7차: exp 올림으로 가끔 실패하던 원인 수정, 2.1초 대기) |
| `AdminSessionIntegrationTest` | 7차: 로그인 세션 기기 정보(`X-Wearly-Client`·UA 추정·`X-Forwarded-For`), 관리자 세션 목록 정렬·검색·기기 필터·`current`·대시보드 숫자, 세션 하나·회원 전체 강제 로그아웃 즉시 401·refresh 401·이력, 400·404·403, 정지 시 세션 삭제·FORCED_LOGOUT, 동시 refresh 한쪽만 성공, lastSeenAt 1분 간격, CORS `X-Wearly-Client` |
| `ProfileIntegrationTest` | 기본값·저장·검증 (색·취향은 코드 검증) |
| `WardrobeIntegrationTest` | CRUD·PATCH 병합·짝·필터·정렬·검색·통계(DRESS 키 포함)·bulk |
| `CodeIntegrationTest` | `/codes` 형식·ETag 304·공개, 새 코드(DRESS·JEANS·denim·FLORAL…)로 등록, 모르는 코드·비활성 코드·parent 불일치 400, PATCH에서 안 바꾼 비활성 필드 통과, 비활성 종류의 하위 세부 종류, 통계 키 |
| `AdminCodeIntegrationTest` | 관리자 권한 403·401, 코드 CRUD·검증·순서·사용 중 삭제 409·pairs 양방향·캐시 갱신(version 변경)·변경 이력 |
| `AdminAiProviderIntegrationTest` | 공급자 초기값·CRUD·`keyConfigured`·`usable`·검증(서버 설정 키 이름·options)·키 값 비노출·변경 이력 |
| `ProviderApiKeyIntegrationTest` | (마스터 키 있음) 웹 키 저장·교체·삭제, 키 출처 ENV→DB→ENV·NONE→DB, 암호문·nonce, 시험 분석이 DB 키로 호출, 재확인 403, 형식 400, verify(가짜 OpenAI·Gemini·Claude 서버의 모델 목록 경로·헤더) 실패 400, 공급자 삭제 시 비밀 행 삭제, 웹 키를 넣거나 지우면 모델 목록 캐시를 비우고 새 키로 다시 받음(8차), 변경 이력 내용, 서버 출력·응답·이력에 키 0건 |
| `AdminProviderModelsIntegrationTest` | (8차) 모델 목록 API: 키 없음 `NO_API_KEY` + 내장 목록(제공사를 부르지 않음), 가짜 OpenAI 서버로 LIVE(거르기·`createdAt`·Bearer 헤더)·10분 캐시·`refresh=true`·변경 이력 없음, 가짜 Claude·Gemini 서버, 일반 회원 403·토큰 없음 401·없는 공급자 404, `/admin/ai/types`의 `models` |
| `AiModelListServiceTest` | (8차, Spring 없음·가짜 포트) 키 없음·없는 공급자 404, 추천 붙이기와 10분 캐시, 키가 바뀌면(지문) 다시 받기·`invalidate`, 실패하면 내장 목록 + `MODEL_LIST_FAILED`(키·응답 원문이 로그에 없음)·사유별 문구 |
| `ModelListRulesTest` · `ModelListModulesTest` | (8차) OpenAI·Gemini 거르기, `models/` 접두사 제거, 추천·정렬, 키 지문 / 가짜 HTTP로 Claude(SDK, 폐기 모델 제외)·OpenAI·Gemini(`nextPageToken` 따라가기, 같은 토큰이면 멈춤)의 요청과 응답 파싱·오류 사유 |
| `ProviderApiKeyNoMasterKeyIntegrationTest` | 마스터 키 없음 409 `SECRET_KEY_MISSING`(비밀번호 확인보다 먼저), `secretStoreEnabled: false` |
| `EnvApiKeyAdapterTest` | 서버 설정 키 찾기: `wearly.ai.keys.<이름>` → 같은 이름 속성·환경변수, 빈 값은 건너뜀, `*_API_KEY`가 아닌 이름 거부 (Spring 없음) |
| `AesGcmSecretCipherTest` · `ApiKeyRulesTest` · `ApiKeyServiceTest` | 암호화 왕복·nonce 매번 다름·AAD/마스터 키/암호문이 다르면 실패·마스터 키 없음 WARN·형식 오류 ERROR, 키 형식·출처 우선순위, 복호화 캐시·무효화·복호화 실패 시 쓸 수 없음 (Spring 없음) |
| `WardrobeRetireIntegrationTest` · `OutfitRetiredItemIntegrationTest` | (6차) 사용 중지 → 목록 `retired` 필터·다시 사용·시각 유지·PATCH, 통계(`retired`·`retiredByReason`), 완전 삭제와 차이, 남의 옷 404·입력 400, 비활성 이유 400·사용 중 코드 삭제 409, 회원 목록 `retiredItemCount`, DB CHECK, 코디 후보 제외·고정/중심 400·옷 부족 |
| `AppConfigIntegrationTest` | (6차) `/app/config` 공개·형식·ETag 304·`no-cache`·틀린 토큰 무시·CORS `ETag` 노출, 관리자 GET·PUT, version 변경, 같은 값 PUT, 400·403·401, 변경 이력 `APP_THEME_UPDATE` |
| `RetirementTest` · `ThemeSettingTest` · `AppSettingServiceTest` · `AdminAppThemeServiceTest` | (6차, Spring 없음) 사용 중지·다시 사용 규칙과 이유·메모 검증, 허용 테마·version, 테마 변경 저장·같은 값·400, 관리자 이메일·변경 이력 |
| `ItemImageIntegrationTest` | (사진 저장, 18 문서, 실제 MinIO) upload-url → 서명 URL로 PUT → complete(JPEG로 다시 인코딩) → 아이템 등록 → 서명 URL로 받기, PNG도 JPEG로 저장, 남의·없는 이미지 404, 잘못된 파일 `IMAGE_INVALID`(업로드 원본 삭제), upload-url 입력 검증 400, PENDING 붙이기 `IMAGE_NOT_UPLOADED`, 다른 아이템에 붙은 이미지 409 `IMAGE_IN_USE`, 사진 교체·빼기·아이템 완전 삭제·회원 탈퇴 시 객체 삭제, 하루 한도 429, 정리 작업(오래된 PENDING·안 붙은 READY·DELETED), `{ imageId }`로 AI 분석 |
| `ImageIoProcessorAdapterTest` · `ImageRulesTest` · `StoragePresignersTest` | (Spring 없음) 큰 PNG를 1600/400 JPEG(품질 0.8)로 줄임, 설정한 크기·품질을 씀, 작은 사진은 크기 유지, 투명 PNG는 흰 배경, EXIF 위치·방향 태그 제거와 방향 8가지, 가짜 JPEG·다른 형식 거부, 화소 상한을 디코딩 전에 거부 / 실제 바이트로 JPEG·PNG 판별, 업로드 요청 검증, `imageId` UUID 형식, 오브젝트 키 규칙 / 서명 호스트를 요청 Host 이름 + 저장소 포트로 만듦 |
| `WeatherIntegrationTest` | (17 문서, 목업 날씨) 관리자 권한, 지금 실행 → 앱 조회·ETag·304·400·503, 한 지역 실행, 설정 검증·변경 이력, 공급자 수정·차단 해제·시험 호출, 호출 이력 필터·요약 |
| `PlaceWeatherIntegrationTest` | (19 문서, 실제 HTTP, 목업 날씨·평년값) region 방식은 토큰 없이 `FORECAST`·`CLIMATE`, 좌표 방식·장소 검색은 로그인 필요(401), 날짜·좌표 검증 400, 좌표는 격자로 받아 캐시하고 동시에 와도 공급자는 한 번, 회원 하루 한도를 넘으면 공급자를 부르지 않고 평년값으로 답함, 장소 검색이 국내 결과에 REGION 코드를 붙이고 호출 이력에 `SEARCH`로 남음 |
| `PlaceWeatherServiceTest` · `ClimateServiceTest` | (Spring 없음·가짜 포트) 순서 FORECAST → CLIMATE → 503/429, 한도는 실제 호출 직전에만 차감, 한도 초과 시 예전 캐시·평년값, 격자 잠금·대기, 시·도 이름 맞추기·지오코딩 정렬·국내 "시"·"군" 보정·중복 제거·`regionCode` 붙이기·지오코딩 실패 시 WeatherAPI 검색(캐시 안 함), 평년값 계산·400일 재계산·실패 10분 대기·끔 |
| `OpenMeteoClientTest` · `OpenMeteoGeocodingClientTest` · `WeatherApiClientTest` | (가짜 HTTP 서버·응답 고정본) 과거 기상 요청 모양·일별 관측값 변환·자료 없는 날 null·오류 사유 분류·시간 초과·`apikey` 가림, 지오코딩 `language=ko` 응답 파싱·오류 사유, WeatherAPI 예보·검색 매핑 |
| `GridKeyTest` · `MonthlyClimateTest` · `ProviderHealthTest` · `WeatherSettingTest` · `WeatherRefreshRunnerTest` · `WeatherScheduleServiceTest` | (Spring 없음) 격자 키 반올림·`-0.0`·거리, 월별 평년값 계산(평균·1mm 이상 비 온 날 비율·400일), 공급자 상태 전이, 설정 범위, 실행기(두 공급자 폴백)·스케줄 |
| `HexagonalArchitectureTest` | 08 문서 3장·7장 규칙 22개 (ArchUnit, 오류 응답 규칙 3개 포함: `BusinessException`은 문장을 받지 않음, 예외 메시지를 읽는 메서드는 오류 응답 객체를 만들지 않음, `ErrorCode.description()`을 응답에 쓰지 않음, 20 문서 9장): 의존 방향, 컨텍스트 사이는 `port.in`만, common·공유 커널은 컨텍스트를 모름, 출력 어댑터는 API 오류를 모름, `@Entity`·`@Mapper` 위치, 검증·Jackson 어노테이션은 웹 어댑터만, 컨트롤러는 `*Response`만 반환, 포트 DTO는 어댑터를 모름 |
| `ErrorHandlingIntegrationTest` | (20 문서, 실제 HTTP) 예상치 못한 예외 → 500 `INTERNAL_ERROR`와 `traceId`만, 로그에는 원문과 같은 `traceId`·`userId`, DB 연결 장애 → 503(DB 주소는 응답에 없음), 고유 제약 → 409 `CONFLICT`·그 밖의 무결성 위반 500, 필터 예외도 `/error`에서 같은 형식·받은 `X-Request-Id` 유지, 잘못된 JSON → `body INVALID_FORMAT`(Jackson·클래스 이름 없음)·빈 본문 `body REQUIRED`·타입 불일치, 검증 애너테이션 → 코드+`params`, `X-Request-Id` 전달·형식이 틀리면 새로 만듦·성공 응답에도 헤더, 시큐리티 401·403에도 `traceId`, 업무 오류는 카탈로그 코드·`params`만. 모든 오류 본문에 `message`·예외·SQL 문자열이 없는지 확인 |
| `ErrorCodeCatalogTest` · `BusinessExceptionTest` | (Spring 없음) `error-codes.json` ↔ `ErrorCode`·`FieldErrorCode` enum이 코드 이름 집합·HTTP 상태·`params` 이름·권장 문구까지 같다 / 정하지 않은 `params` 이름 거부, 로그용 메시지는 코드와 필드 코드뿐, 응답 본문은 문장 없이 코드·`params`·`traceId` |
| `PlanIntegrationTest` | (21 문서, 실제 HTTP) 등록 201·목록(날짜 → 시간대 순, `from`/`to`)·PATCH(보낸 필드만, `null` 비움)·삭제 204, 같은 `clientId`로 다시 올리면 200·같은 id, bulk(요청 순서·`created`/`updated`·틀린 항목이 있으면 하나도 저장 안 함·101개 400), 남의 예정 404, 입력 검증(필드 오류 코드·날짜 범위), 오래된 예정은 날짜를 바꾸지 않으면 고칠 수 있음, 회원당 500개 409 `PLAN_LIMIT`, 지운 아이템은 `missingItemIds`로 표시하고 코디는 그대로, 예정이 쓰는 STYLE 코드는 삭제 불가(`CODE_IN_USE`, `PLAN`), 탈퇴 시 예정도 삭제 |
| `PlanRulesTest` | (Spring 없음) 날짜 범위(앞뒤 1년, 바꾸지 않은 날짜는 통과)·시간대·상황·제목·메모·스타일 코드(비활성은 저장된 값만)·장소·날씨·출처·확인 시각·코디(칸·id·크기)·`clientId` 형식, bulk의 `items[i].` 경로 |
| `ImageIoAiImageResizerTest` | (Spring 없음, 18 문서 7.4) AI 전송용 줄이기: 큰 JPEG를 긴 변 1024 JPEG로(메타데이터 없음), 큰 PNG도 JPEG로, EXIF 방향 적용, 이미 작으면 다시 인코딩하지 않음, JPEG·PNG가 아니거나 읽을 수 없으면 그대로, 픽셀 수 상한 초과는 디코딩하지 않고 그대로, 설정한 긴 변·품질 사용. `AiProviderFallbackIntegrationTest`는 큰 사진을 줄여 보내고 `image_bytes`를 보낸 바이트로 기록하는지도 본다 |
| `ApiResponseSnapshotTest` | (사진 저장: 아이템 응답의 `image` 필드를 반영해 스냅샷 갱신, 5차, 6차·7차에 새 필드로 갱신. 7차: 대시보드 `activeSessions`·`activeUsers24h`, `targetType` 오류 문구에 `SESSION`, 정지 해제 뒤 예전 토큰 401 단계 추가. 오류 응답 개편(20 문서): 오류 응답은 `message` 없이 `code`·`params`·`traceId`이고 `traceId`는 `<traceId>`로 정규화해 비교한다) 160개 요청의 응답(상태·헤더 일부·본문)을 `src/test/resources/snapshots/api-responses.json`과 비교. 토큰·시각·버전 등 매번 다른 값만 정규화한다. 응답 모양을 바꾸면 실패한다. 파일을 지우고 돌리면 다시 만든다 |
| `*ServiceTest` (5차) | 서비스 단위 테스트(가짜 포트, Spring 없음): `AuthService`, `CodeManagementService`(색 짝·삭제 가드), `AdminUserService`(자기 정지 금지), `AdminAiService`(키 저장 순서), `AiCallRunner`(폴백·기록), `AiQuotaService`·`AiJsonGenerationService`(한도 예약), `WardrobeService` |
| `AiQuotaConcurrencyIntegrationTest` | (5차) 12개 동시 예약·동시 사진 분석에서 한도 3개만 성공, 오래된 예약 무시 |
| `CodeUsageSourceIntegrationTest` · `WardrobePatchIntegrationTest` · `AiLogSummaryMigrationTest` | (5차) 코드 사용 수를 옷장·프로필이 세는지, 옷장 PATCH 오류 모양, V7이 기존 기록 요약을 4차 문구대로 채우는지 |
| `CandidateValidatorTest` · `OutfitPromptTest` | (Spring 없음) 후보 검증: 정상·남의 아이템·칸 불일치·TOP+DRESS 혼합·신발 없음·고정/중심 누락·제외/피하고 싶은 색/세탁 중·중복·형식. 스키마: 칸별 enum이 그 칸의 내 아이템만·세탁 중 제외·고정·원피스 고정·null 허용 규칙, 프롬프트 내용, 모자란 칸, 고정 확인, 목업 결정성 |
| `OutfitRecommendIntegrationTest` | (목업) 응답 형식·규칙·결정성, 고정·중심·제외·색·후보 수, 옷 부족 400(기록 없음), 한도 429(사진 분석과 따로), 요청 검증 400·401, 기록 `feature`·관리자 AI 기록·대시보드 |
| `OutfitProviderFallbackIntegrationTest` | (DB 공급자, 가짜 OpenAI·Gemini·Claude 서버) 오류·남은 후보 0개면 다음 공급자, 각 사 요청 형식(시스템 지시·텍스트·스키마 enum), 실패 기록, 전부 실패 502·한도 미차감, 공급자 없으면 502, 공급자 `today`·대시보드 |
| `*domain*` 단위 테스트 | 코드 검증·attrs, refresh 토큰 상태 판단·기기 종류 추정, 공급자 선택 순서, 옷장 검증·통계 키, 프로필 정리 (Spring 없음) |
| `AiProviderFallbackIntegrationTest` | (DB 공급자 실행) 공급자 없으면 502, priority 순 시도·실패 시 다음 공급자·실패 기록, 전부 실패 502·한도 미차감, 시험 분석(사용 여부 무관·한도 미차감·ADMIN_TEST), AI 기록 검색·상세·대시보드 |
| `AdminUserIntegrationTest` | 시작 시 관리자 생성·기존 회원 승격, 회원 검색, 정지 시 로그인 403·토큰 401·refresh 401, 자기 자신 강등·정지 400, 대시보드 |
| `AdminMfaIntegrationTest` | (22 문서) 관리자 사이트 로그인이 대기 토큰을 주고 등록(setup → verify) 뒤에야 관리자 API가 열림·복구 코드 10개, 5번 틀리면 잠기고 대기 토큰이 무효, 일반 회원/앱 로그인은 그대로 토큰, 비밀번호 변경 시 다른 세션만 끊김, 내 2단계 인증 초기화 → 다시 등록, 다른 관리자의 초기화 시 그 세션 끊김, 관리자 해제 시 권한이 바로 빠짐 |
| `SsoIntegrationTest` · `SsoNoTrustIdpMfaIntegrationTest` · `SsoDisabledIntegrationTest` | (22 문서 4.2, 가짜 OIDC 서버 `FakeOidcServer`) 새 관리자 생성·교환 코드로 토큰 발급·1회용, 관리자 그룹이 아니면 거부(회원 안 만듦), 그룹에서 빠지면 관리자 해제·세션 끊기, 기존 관리자와 연결·일반 회원 비승격, 이메일 미검증 거부, state 1회·쿠키 확인, ID 토큰 불일치, IdP 오류·연결 실패, 인가 응답 `iss` 불일치, 허용 목록 밖 주소, IdP 로그아웃 / `trust-idp-mfa: false`면 앱 2단계 인증을 거침 / 꺼진 기본 동작(빈 목록, 404) |
| `IdTokenVerifierTest` · `SsoConfigTest` · `SsoProviderTest` | (Spring 없음) ID 토큰 확인(서명 알고리즘·iss·aud/azp·exp·iat·nonce·sub) / 설정이 모자란 공급자를 빼고 로그 / 공급자 값 정리·관리자 그룹·이메일 판단 |
| `TotpTest` · `AdminAccountServiceTest` · `AuthServiceTest` | (Spring 없음) RFC 6238 시험 벡터(SHA-1·256·512)·앞뒤 1단계만 받음·마지막으로 쓴 단계 이하 거부·Base32(RFC 4648)·인증 앱 등록 주소 / 관리자가 없으면 설정 계정을 만들고 같은 이메일의 일반 회원은 올리지 않으며 관리자가 있으면 설정을 쓰지 않음·로그의 이메일은 앞 2글자만 / 로그인 흐름(`LOGIN_FAILED` 기록, 관리자 사이트만 대기 토큰) |
| `DayBeforeNotifyIntegrationTest` · `PushTokenIntegrationTest` | (23 문서, 실제 DB·HTTP) 알림을 켠 회원에게만 하루 한 번 보냄, `DeviceNotRegistered` 토큰 삭제 / 등록(기기마다 하나·같은 기기는 바꿈)·같은 토큰은 다른 회원으로 옮김·삭제 204·검증 오류 모음·401·탈퇴 시 삭제 |
| `DayBeforeNotifyServiceTest` · `ExpoPushSenderAdapterTest` · `DayBeforeMessageTest` · `PushTokenRulesTest` | (Spring 없음) D-1 작업 순서·건너뜀·하루 한 번·예보 바뀜·날씨 재확인 생략/실패·토큰 삭제·예외 때 기록 풀기 / 가짜 Expo 서버로 100개 묶음·티켓 해석·헤더 / 알림 문구 / 토큰·플랫폼·기기 ID 검증 |
| `WardrobeColorsPurchaseIntegrationTest` · `ItemRulesTest` · `WardrobeDomainTest` | (이슈 #4) 색 1~3개·`color` 호환·중복/개수 오류, PATCH 색, 구입일 범위·비우면 등록일·추정 표시, 목록 `color` 필터가 보조 색까지, 통계 `byColor` 주 색상 기준, 코드 사용 수에 보조 색 / 도메인 규칙 |
| `ProfileIntegrationTest` · `CodeAttrsTest` · `UserProfileTest` | (이슈 #5) 나의 정보 저장·비우기·코드 검증·키 범위 130~210, BODY_TYPE `description`·`hint` attrs 검증 / 프로필 도메인 |
| `KoreanDistrictsTest` · `DistrictFileAdapterTest` · `PlaceDistrictSearchTest` | (19 문서) 시·군·구 맞추기(EXACT·STEM·PREFIX, 앞 낱말, 겹치는 이름 label) / `korea-districts.json` 형식(268곳·근사 칸·좌표 범위·최근 행정구역) / 장소 검색 흐름: 해운대는 지오코딩을 부르지 않음, 중구는 시·도별 모두, 한도 초과·공급자 없음에도 시·군·구 결과는 줌 |
| `ProviderOptionsTest` · `AnalysisPromptTest` | (프롬프트 캐싱, 18 문서 13장) 설정이 없으면 켜지고 false면 꺼짐 / 고정 지시문·스키마가 사진·스냅샷 재읽기와 상관없이 같음, 허용 값이 코드 순서대로, 스키마의 모든 객체가 순서 고정 맵, 코드가 바뀌면 고정 지시문도 바뀜. `ClaudeAiProviderTest`(고정 지시문 끝에 `cache_control` ephemeral·사진만 다른 두 요청의 앞부분이 같음·사진 없는 코디 추천도 동일·캐시 토큰 파싱)·`OpenAiProviderTest`·`GeminiAiProviderTest`(캐시 토큰 파싱)·`OutfitPromptTest`(코디 추천 고정/바뀌는 부분, 회원 정보 한 줄, 보조 색)도 같은 줄에서 본다 |
| `AiAnalyzeIntegrationTest` | 목업 결정성, 일일 한도 429, 10MB 초과 413, 형식·파트 검증 |
| `AiProviderModeTest` | `WEARLY_AI_PROVIDER` 해석(`mock`·`db`·`claude`), `auto`·모르는 값 시작 실패와 안내 |
| `ClaudeAiProviderTest` · `OpenAiProviderTest` · `GeminiAiProviderTest` | 각 공급자의 요청 JSON(이미지·구조화 출력 스키마·헤더), 응답 파싱·토큰 수, 거절·잘림·HTTP 오류·시간 초과, 오류 메시지에 키가 남지 않음 |
| `MockAiProviderTest` · `ImageTypesTest` | 목업이 활성 코드 안에서 짝 맞는 값만 고름, 이미지 형식 판별 |

## JPA와 MyBatis 분담

같은 DataSource와 트랜잭션 매니저(`JpaTransactionManager`)를 쓴다. MyBatis는 Spring이 관리하는 커넥션에 참여하므로 한 `@Transactional` 안에서 섞어 써도 된다.

| | JPA (쓰기·단건) | MyBatis (읽기 전용) |
|---|---|---|
| 회원·자격 증명·로그인 이력 | `User`(역할·정지), `LocalCredential`, 로그인 이력(`AuthEventJpaAdapter`, 네이티브 INSERT) | 관리자 회원 목록, 세션 목록의 회원 요약·로그인 이력 (`AdminQueryMapper.xml`) |
| 프로필 | `UserProfile` | |
| 옷장 | 등록·bulk·PATCH·삭제·단건 조회 (`WardrobeItem`) | 동적 필터 목록, `/wardrobe/stats` 집계, 코드 사용 수 (`WardrobeItemMapper.xml`) |
| 프로필 | | 코드 사용 수 (`user/ProfileMapper.xml`) |
| 코드 | 관리자 추가·수정·순서·삭제 (`Code`) | 캐시 적재·version (`CodeMapper.xml`). 사용 수는 옷장·프로필이 `CodeUsageSource`로 센다 |
| AI | 호출 기록 저장 (`AiAnalysisLogEntity`), 공급자 설정 (`AiProviderEntity`), 웹 키 암호문 (`ProviderSecretEntity`), 한도 예약 (`AiUsageReservationEntity`) | 일일 사용량·한도 잠금 (`ai/AiQuotaMapper.xml`), 관리자 AI 기록 검색·공급자별 통계·대시보드 (`admin/AdminQueryMapper.xml`) |
| 옷 사진 | `tb_item_image` 쓰기·잠금 조회·정리 표시 (`ItemImageJpaAdapter`) | 하루 업로드 한도 집계(회원별 advisory lock)·정리 대상 (`image/ItemImageMapper.xml`) |
| 예정 코디 | 만들기·수정·삭제·`clientId` 조회 (`PlanJpaAdapter`, `PlanEntity`) | 날짜 범위 목록·회원별 개수·쓰기 잠금(`pg_advisory_xact_lock`)·코드 사용 수 (`plan/PlanMapper.xml`) |
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
├── error/                         오류 코드(ErrorCode)·BusinessException·전역 예외 처리·traceId(TraceIds)·/error(ErrorPageController) — 20 문서
├── imaging/                       ImageCodec: 사진 읽기·줄이기·JPEG 쓰기 도구 (image의 저장용·ai의 전송용 어댑터가 같이 씀)
├── web/                           웹 어댑터 공용 (CurrentUser, CurrentSession, PathIds, Uploads, Ids, Times, JsonPatches, TraceIdFilter)
├── config/                        WearlyProperties, ClockConfig, CorsConfig
├── mybatis/                       TypeHandler, LikePatterns(LIKE 이스케이프, XML에서 <bind>)
├── persistence/                   PersistenceClock (엔티티 시각도 주입한 Clock)
└── transaction/                   AfterTransaction (커밋·완료 뒤 실행)
```

| 컨텍스트 | domain | 입력 포트 (주요) | 출력 어댑터 |
|---|---|---|---|
| `auth` | `RefreshToken`(회전·재사용 판단), `RefreshTokens`, `LoginSession`, `ClientKind`·`ClientInfo`, `AuthEvent`, `AccessTokenClaims`, `LocalCredential`, `EmailTakenException`, SSO: `SsoProvider`·`SsoSettings`·`SsoState`·`SsoGrant`·`SsoLink`·`SsoRedirects`·`SsoSecrets`·`IdTokenClaims`·`OidcMetadata` | `AuthUseCase`, `AuthenticationUseCase`, `AccountUseCase`, `SessionUseCase`(7차), `SsoUseCase`(SSO, 22 문서 4.2: `SsoService`·`SsoAccountService`) | valkey(세션·Refresh 토큰, Lua 스크립트 `ValkeyScripts`), JPA(자격 증명·로그인 이력), security(BCrypt, JWT 발급), oidc(`HttpOidcAdapter`: discovery·JWKS·토큰 교환, `IdTokenVerifier`: Nimbus ID 토큰 확인, `OidcPort`), valkey(`ValkeySsoStoreAdapter`: state·교환 코드), JPA(`SsoLinkJpaAdapter`: `tb_user_sso`). SSO 계정 알림은 `SsoAccountListener`(auth가 정의, admin의 `SsoAccountAuditListener`가 변경 이력으로 남김). 설정 `auth.config.SsoConfig`·`SsoProperties`. 입력 어댑터 `adapter.in.user`(탈퇴 시 세션 삭제, user의 `UserDeletedListener` 구현). 보안 필터 체인은 `auth.config.SecurityConfig` |
| `user` | `User`(이름·상태·역할 불변식), `UserStatus`, `UserRole`, `UserProfile`, `WorkType` | `UserUseCase`, `ProfileUseCase`, `ProfileCodeUsageUseCase`, `UserDeletedListener`(SPI: auth가 구현) | JPA(회원·프로필), MyBatis(프로필 코드 사용 수) |
| `wardrobe` | `WardrobeItem`, `ItemInput`, `ItemRules`(불변식), `ItemCodeValidator`, `WardrobeStatsRules`, `Retirement`·`RetireRules`(6차 사용 중지) | `WardrobeUseCase`, `WardrobeStatsUseCase`, `ItemCodeUsageUseCase` | JPA(아이템 쓰기·단건), MyBatis(목록·통계·코드 사용 수) |
| `code` | `Code`, `CodeAttrs`, `CodeRules` (스냅샷·검증 정책은 공유 커널) | `CodeCatalog`, `CodeManagementUseCase`, `CodeUsageSource`(SPI: 옷장·프로필이 구현) | JPA(코드 쓰기), MyBatis(스냅샷 캐시) |
| `ai` | `AiProviderSetting`, `AiProviderSelection`, `AiRunMode`, `AiProviderType`, `ApiKeyRules`, `AnalysisPrompt`, `AiJsonPrompt`, `AiSuggestionValidator` … | `AnalyzeImageUseCase`, `ProviderTestUseCase`, `AiProviderAdminUseCase`, `AiProviderKeyUseCase`, `AiStatusUseCase`, `JsonGenerationUseCase` | JPA(공급자·호출 기록·웹 키·한도 예약), MyBatis(사용량·한도 잠금), ai/*(공급자 모듈: Claude SDK·OpenAI·Gemini REST·목업·키 확인), env, crypto(AES-GCM), transaction |
| `outfit` | `OutfitPool`, `OutfitPrompt`, `CandidateValidator`, `MockOutfitPlanner`, `OutfitRequest` … | `RecommendOutfitUseCase` (기능 이름 `OutfitFeatures`) | 없음 (옷장·프로필·코드는 각 입력 포트, 공급자는 ai의 `JsonGenerationUseCase`) |
| `appsetting` | `AppThemes`(허용 테마), `ThemeSetting`(변경·version) | `AppSettingUseCase` | JPA(`tb_app_setting`). 관리자 변경은 admin의 `AdminAppThemeService`가 이 포트로 부르고 이력을 남긴다 (6차) |
| `image` | `ItemImage`(PENDING·READY·DELETED 전이), `ImageRules`(업로드 요청 검증·imageId 형식), `ImageFormats`(실제 바이트 판별), `ImageKeys`, `ImageSettings` | `ImageUploadUseCase`(upload-url·complete), `ItemImageUseCase`(아이템에 붙이기·빼기·조회), `ImageContentUseCase`(AI 분석용 original 읽기), `ImageCleanupUseCase`(정리·탈퇴 삭제) | JPA(`tb_item_image`), MyBatis(하루 한도 집계·정리 대상), storage(S3 호환: `S3ImageStorageAdapter`·`StoragePresigners`), imaging(JDK ImageIO 사진 처리), transaction(커밋 뒤 실행). 입력 어댑터 `adapter.in.scheduler`(정리 주기 작업), `adapter.in.user`(탈퇴 알림 받기, user의 `UserDeletedListener` 구현) |
| `plan` | `Plan`, `PlanInput`·`PlanDraft`, `PlanRules`(입력 검증·500개·날짜 범위), `PlanOutfits`(코디 검증·`missingItemIds` 계산), `PlanPlace`, `PlanWeather` | `PlanUseCase`(목록·만들기·bulk·PATCH·삭제), `PlanCodeUsageUseCase`, wardrobe의 `ItemExistenceUseCase`를 부른다 (21 문서) | JPA(`tb_planned_outfit` 쓰기), MyBatis(목록·개수·회원별 쓰기 잠금·코드 사용 수), `in/code/`에 `PlanCodeUsageSource` |
| `admin` | `AuditTargets`, `SearchText` | `AdminDashboardUseCase`, `AdminUserUseCase`, `AdminCodeUseCase`, `AdminAiProviderUseCase`, `AdminAppThemeUseCase`(6차), `AdminSessionUseCase`(7차), `EnsureAdminUseCase` | JPA(변경 이력 = `RecordAuditPort`), MyBatis(대시보드·목록 읽기 모델 → `port.out.dto`) |

- 컨텍스트 사이는 상대 `port.in`(과 `port.in.dto`)으로만 부른다. 관리자 API는 admin 컨텍스트가 code·ai·user·auth 유스케이스를 부르고, 같은 트랜잭션에서 변경 이력을 남긴다. 외부 HTTP가 끼는 웹 키 저장은 트랜잭션을 나눈다(`AdminAiKeyWriter`).
- 사진 분석 `POST /wardrobe/items/analyze`는 경로는 옷장이지만 ai 컨텍스트의 `AiAnalyzeController`가 받는다.
- `MultipartFile`은 웹 어댑터에서 `ImageUpload`(byte[]·MIME)로, PATCH JSON은 웹 어댑터가 필드마다 `Patch`로 바꿔 `PatchCodeDto`·`PatchProviderDto`·`PatchUserDto`·`PatchItemDto`로 넘긴다.
- 컨트롤러는 항상 `*Response`를 돌려준다. 응답 JSON은 5차 전과 바이트 단위로 같다(`ApiResponseSnapshotTest`). 6차에서 계약상 새 필드(아이템 `retiredAt`·`retireReason`·`retireMemo`, 통계 `retired`·`retiredByReason`, 회원 목록 `retiredItemCount`, 코드 그룹 `RETIRE_REASON`)와 변경 이력 `targetType` 오류 문구에 `APP_SETTING`이 더해져 스냅샷을 갱신했다.
