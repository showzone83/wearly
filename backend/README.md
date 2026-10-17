# Wearly 백엔드

웨어리 앱·관리자 사이트의 API 서버. 계약 문서:

- [`doc/05_백엔드_API.md`](../doc/05_백엔드_API.md) — 토큰 로그인 · 옷장 · AI 분석 (1차)
- [`doc/06_코드_관리.md`](../doc/06_코드_관리.md) — 옷 속성 값(종류·세부 종류·색·패턴·계절·스타일·보유 상태)을 DB 코드 테이블로 (2차)
- [`doc/07_관리자_사이트.md`](../doc/07_관리자_사이트.md) — 관리자 역할 · 관리자 API · AI 공급자 관리 (2차)

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.1 (Java 21 toolchain, Gradle 9.8.1 wrapper, Kotlin DSL) |
| 데이터 접근 | Spring Data JPA (Hibernate 7.4) + MyBatis (mybatis-spring-boot-starter 4.1.0) |
| DB | PostgreSQL 17 + pgvector, 스키마는 Flyway (`src/main/resources/db/migration`, V1~V3) |
| 인증 | Spring Security OAuth2 Resource Server, JWT HS256 (Nimbus) |
| AI | Claude는 Anthropic Java SDK 2.73.0, OpenAI·Gemini는 각 사 REST API를 Spring `RestClient`로 (SDK 없음) |

## 실행

```bash
# 1) 저장소 루트에서 DB 띄우기 (localhost:5433, DB·계정·비밀번호 wearly)
docker compose up -d

# 2) 서버 실행 (backend/ 에서)
cd backend
cp .env.example .env   # 처음 한 번. 쓸 AI 공급자의 키와 WEARLY_ADMIN_* 를 채운다
./gradlew bootRun      # 관리자 화면의 AI 공급자 설정을 우선순위 순으로 사용
./gradlew bootRunMock  # 목업 공급자로 사진 분석 (키 불필요, WEARLY_AI_PROVIDER=mock)
```

- 서버: `http://localhost:8080`, API 기본 경로 `/api/v1`, 상태 확인 `GET /actuator/health`
- 처음 뜰 때 Flyway가 V1(초기 스키마) → V2(코드 테이블) → V3(관리자·AI 공급자)을 적용한다. JPA는 `ddl-auto: validate`라서 스키마와 엔티티가 어긋나면 기동이 실패한다.
- 운영 프로필(`SPRING_PROFILES_ACTIVE=prod`)은 `WEARLY_JWT_SECRET`이 없으면 기동하지 않는다.

실행 가능한 jar: `./gradlew bootJar` → `build/libs/wearly-backend-0.0.1-SNAPSHOT.jar`

### AI 공급자 고르기 (07 문서 3장)

1. `WEARLY_AI_PROVIDER=mock`(`./gradlew bootRunMock`)이면 **언제나 목업**이다. DB 설정은 무시한다.
2. 그 밖(기본 `db`)에는 `tb_ai_provider`에서 **사용(enabled)·모델·키**를 모두 갖춘 공급자를 `priority` 순으로 시도한다.
   키는 각 행의 `api_key_env`가 가리키는 환경변수(또는 `backend/.env`)에서 읽는다. 초기값: `claude`(사용, `claude-opus-5-5`, `ANTHROPIC_API_KEY`), `openai`·`gemini`(미사용, 모델 비움).
3. 앞 공급자가 실패하면(시간 초과, API 오류, 거절, 응답 검증 실패) 다음 공급자로 넘어간다. 실패도 각각 `tb_ai_analysis_log`에 남고, 성공한 회원 분석만 일일 한도에 센다.
4. 쓸 수 있는 공급자가 하나도 없으면 사진 분석은 **502 `AI_UNAVAILABLE`**. 목업으로 대신하지 않는다.
5. 서버는 공급자가 없어도 시작한다. 시작 로그에 쓸 수 있는 공급자 목록(INFO) 또는 `쓸 수 있는 AI 공급자가 없습니다…`(WARN)가 찍힌다.

- 공급자·모델·우선순위·옵션은 관리자 API(`/api/v1/admin/ai/providers`)로 바꾼다. 키 값은 DB에 저장하지 않고 응답·로그에도 내보내지 않는다(`keyConfigured`만).
- 키 환경변수 이름은 `*_API_KEY` 모양만 받는다(예: `OPENAI_API_KEY`). DB 비밀번호·JWT 키 같은 다른 비밀을 공급자로 보내지 못하게 하려는 것.
- 공급자 옵션 `baseUrl`은 `WEARLY_AI_ALLOW_BASE_URL=true`일 때만 쓴다(기본 false). 키를 다른 주소로 보내지 못하게 하려는 것.
- 예전 값 `WEARLY_AI_PROVIDER=claude`는 `db`로 받는다(초기값에서 claude가 우선순위 1이라 같은 동작). `auto`나 모르는 값은 시작 실패와 안내.
- 예전 `WEARLY_AI_MODEL`·`WEARLY_AI_EFFORT`는 더 이상 쓰지 않는다. 모델·effort는 공급자 행에서 바꾼다.

### 관리자 계정

`WEARLY_ADMIN_EMAIL`·`WEARLY_ADMIN_PASSWORD`가 있으면 시작할 때 그 계정을 만들거나(이름 `WEARLY_ADMIN_NAME`, 기본 "관리자") 이미 있는 계정을 `ADMIN`으로 올린다. 관리자 로그인은 일반 `POST /api/v1/auth/login`이고 응답의 `user.role`이 `ADMIN`이다. 가입 API로는 관리자를 만들 수 없다.

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
| `WEARLY_AI_PROVIDER` | `db` | `db` · `mock`. 목업은 `./gradlew bootRunMock`이 이 값을 `mock`으로 준다 |
| `WEARLY_AI_DAILY_LIMIT` | `10` | 회원별 하루 AI 분석 횟수 (Asia/Seoul 날짜, 성공한 회원 분석만 센다) |
| `WEARLY_AI_TIMEOUT` | `PT60S` | 공급자 옵션 `timeoutSeconds`가 없을 때의 호출 제한 시간 |
| `WEARLY_AI_ALLOW_BASE_URL` | `false` | 공급자 옵션 `baseUrl`(테스트 서버·프록시 주소)을 받을지. 끄면 관리자 API가 baseUrl을 400으로 거부하고, DB에 있어도 무시해 키가 공식 API 주소로만 나간다 |
| `ANTHROPIC_API_KEY` | (없음) | Claude 키 (초기값 `claude` 행의 `api_key_env`) |
| `OPENAI_API_KEY` | (없음) | OpenAI 키 (초기값 `openai` 행) |
| `GEMINI_API_KEY` | (없음) | Gemini 키 (초기값 `gemini` 행) |
| `WEARLY_ADMIN_EMAIL` | (없음) | 시작 시 만들거나 ADMIN으로 올릴 계정 이메일 |
| `WEARLY_ADMIN_PASSWORD` | (없음) | 위 계정을 새로 만들 때의 비밀번호 (8~64자, 영문+숫자) |
| `WEARLY_ADMIN_NAME` | `관리자` | 새로 만들 때의 이름 |

관리자가 공급자를 추가하면서 다른 키 환경변수 이름(예: `OPENAI_BACKUP_API_KEY`)을 정하면 그 이름으로도 키를 넣을 수 있다.

하네스 예: `SERVER_PORT=18080 WEARLY_ACCESS_TTL=PT3S WEARLY_AI_DAILY_LIMIT=3 WEARLY_AI_PROVIDER=mock SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/wearly_harness`

## API 요약

공통 규칙·오류 형식·인증·옷장 API는 05 문서. 아래는 2차에서 더하거나 바뀐 것.

### 코드 (06 문서)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/codes` | 공개. `{ version, groups: [{ group, name, codes: [{ code, label, parent, sortOrder, active, attrs }] }] }`. `ETag: "<version>"`, `If-None-Match`가 같으면 304 |

- 아이템 등록·수정·일괄 등록과 프로필(`colors`·`moods`)은 코드 테이블로 검증한다. 새로 고르는 값은 활성 코드만, PATCH에서 바꾸지 않은 값은 비활성이어도 통과. subType의 상위는 category여야 한다.
- `/wardrobe/stats`의 `byCategory`·`byStatus` 키는 활성 코드 전부(0 포함) + 아이템이 있는 비활성 코드.
- AI 프롬프트·JSON 스키마·응답 검증·목업은 활성 코드로 만든다. `AnalyzeResponse.provider`는 공급자 ID 문자열(`"claude"`, `"openai"`, `"mock"` …).

### 관리자 (07 문서, `/api/v1/admin/**`는 ADMIN만, 아니면 403 `FORBIDDEN`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/admin/dashboard` | 회원·7일 신규·아이템 수, 오늘 AI 호출, 공급자별 7일 통계 |
| GET | `/admin/codes` | `/codes` + 그룹 `description`, 코드별 `usageCount` |
| POST | `/admin/codes/{group}` | 코드 추가 (201). 중복 409 `CODE_EXISTS` |
| PATCH | `/admin/codes/{group}/{code}` | 라벨·상위·순서·사용 여부·attrs 수정 (attrs는 통째로) |
| PUT | `/admin/codes/{group}/order` | `{ codes: [...] }` 순서대로 sortOrder 10, 20, … |
| DELETE | `/admin/codes/{group}/{code}` | 쓰는 아이템·프로필·하위 세부 종류가 있으면 409 `CODE_IN_USE` |
| GET·POST | `/admin/ai/providers` | 공급자 목록(`keyConfigured`·`usable`·오늘 호출) / 추가 (201) |
| PATCH·DELETE | `/admin/ai/providers/{id}` | 수정 / 삭제 (분석 기록은 남음) |
| POST | `/admin/ai/providers/{id}/test` | multipart `image`로 그 공급자를 한 번 호출. 한도에 안 세고 `ADMIN_TEST`로 기록 |
| GET | `/admin/ai/types` | 지원하는 type과 options 설명 |
| GET | `/admin/ai/logs`, `/admin/ai/logs/{id}` | AI 분석 기록 검색 / 결과 JSON 전체 |
| GET·PATCH | `/admin/users`, `/admin/users/{id}` | 회원 검색 / 정지·해제·역할 |
| GET | `/admin/audit-logs` | 변경 이력 (코드·AI 공급자·회원) |

- 목록은 `{ items, page, size, total }`, 쿼리 `page`(0부터)·`size`(기본 20, 최대 100).
- 회원이 정지되면 로그인 403 `ACCOUNT_SUSPENDED`, 남은 Access 토큰 401 `TOKEN_INVALID`, refresh 401 `REFRESH_INVALID`.
- CORS: Expo 웹 주소 + 관리자 사이트 개발 주소 `http://localhost:5173`(·`http://127.0.0.1:5173`).
- 자세한 요청·응답 형식과 계약에서 정한 세부는 [`WORK_REPORT.md`](WORK_REPORT.md).

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

- 대부분의 테스트는 AI 공급자를 `mock`으로 띄운다 (`IntegrationTestBase`). `AiProviderRoutingIntegrationTest`만 `db` 모드로 띄우고 세 공급자를 로컬 가짜 서버에 붙인다. 실제 키도 `.env`도 쓰지 않는다.
- 단위 테스트의 코드 초기값은 계약 원본 `doc/06_코드_초기값.json`을 그대로 읽는다 (`TestCodes`).
- 리눅스에서 로케일이 UTF-8이 아니면 Gradle이 한글 테스트 이름으로 보고서 파일을 못 만든다. `LC_ALL=C.UTF-8`을 준다 (macOS는 해당 없음).

| 테스트 | 확인하는 것 |
|---|---|
| `AuthIntegrationTest` | 가입·로그인·토큰 만료·refresh 회전·재사용 감지·logout·탈퇴·CORS·health |
| `ShortAccessTtlIntegrationTest` | Access 수명 1초로 띄워 실제 만료 후 refresh |
| `ProfileIntegrationTest` | 기본값·PUT 저장·검증 |
| `WardrobeIntegrationTest` | CRUD·PATCH 병합·남의 아이템 404·짝 검증·필터·정렬·통계·bulk |
| `CodesIntegrationTest` | `/codes` 형식·ETag 304, 새 코드(JEANS·DRESS·denim·FLORAL…) 등록, 모르는 코드·parent 불일치 400, 비활성 코드 400과 PATCH에서 안 바꾼 비활성 값 통과(아이템·프로필), 통계 키 |
| `AdminCodeIntegrationTest` | 관리자 권한 403·401, 코드 CRUD·순서·사용 중 삭제 409·pairs 양방향·version 변경, 종류 비활성 시 하위 세부 종류 선택 불가, attrs 검증, 변경 이력 |
| `AdminIntegrationTest` | 시작 시 관리자 생성·기존 계정 승격, 회원 정지 시 로그인 403·토큰 401·refresh 401, 회원 목록, 대시보드, AI 기록 검색, AI 공급자 CRUD·`keyConfigured`, CORS 5173 |
| `AiProviderRoutingIntegrationTest` | 우선순위 순 시도와 실패 시 다음 공급자(검증 실패·5xx·시간 초과), 사용 안 함·모델 없음·키 없음 건너뛰기, 전부 실패·없음 502(목업 대체 없음), 시작 WARN, 시험 분석은 한도에 안 셈·키 미노출 |
| `AiAnalyzeIntegrationTest` · `AiFailureIntegrationTest` | 목업 결정성·일일 한도 429·413·형식 검증 / 공급자 오류 502·실패는 한도에 안 셈 |
| `ClaudeAiProviderTest` · `OpenAiProviderTest` · `GeminiAiProviderTest` | 각 사 요청 JSON(이미지·구조화 출력 스키마)·응답 파싱·토큰 수·거절·잘림·API 오류·키 지우기·시간 초과 (가짜 HTTP 서버) |
| `AiModeTest` · `MockAiProviderTest` · `ImageTypesTest` | `WEARLY_AI_PROVIDER` 규칙, 목업이 활성 코드만 고름, 이미지 형식 판별 |

## JPA와 MyBatis 분담

같은 DataSource와 트랜잭션 매니저(`JpaTransactionManager`)를 쓴다. MyBatis는 Spring이 관리하는 커넥션에 참여하므로 한 `@Transactional` 안에서 섞어 써도 된다.

| | JPA (쓰기·단건) | MyBatis (읽기 전용) |
|---|---|---|
| 회원·자격 증명·Refresh 토큰 | `User`, `LocalCredential`, `RefreshToken` | |
| 프로필 | `UserProfile` | |
| 옷장 | 등록·bulk·PATCH·삭제·단건 조회 (`WardrobeItem`) | 동적 필터 목록, `/wardrobe/stats` 집계 (`WardrobeQueryMapper.xml`) |
| 코드 | 관리자 추가·수정·순서·삭제 (`Code`) | 전체 읽기 → 메모리 캐시 (`CodeQueryMapper.xml`) |
| AI | 호출 기록 (`AiAnalysisLog`), 공급자 설정 (`AiProviderSetting`) | 일일 사용량 집계 (`AiUsageMapper.xml`) |
| 관리자 | 변경 이력 저장 (`AdminAuditLog`) | 대시보드 집계, 코드 사용 수, 회원·AI 기록·변경 이력 목록 (`AdminQueryMapper.xml`) |

## 패키지 구조

```
app.wearly
├── auth        가입·로그인·refresh·logout, JWT 발급(role 클레임), 인증 오류 응답
├── user        회원(/users/me), 프로필
├── wardrobe    옷장 아이템, 코드 검증(ItemCodeValidator), 목록·통계(MyBatis)
├── code        코드 테이블, 메모리 캐시(CodeCatalog), GET /codes
├── ai          공급자(Claude·OpenAI·Gemini·Mock), 공급자 설정·선택(AiProviderRegistry), 분석 서비스, 호출 기록
├── admin       관리자 API, 변경 이력, 시작 시 관리자 계정(AdminBootstrap)
├── domain      코드 테이블로 옮기지 않은 열거형 (DressCode, WorkType)
├── common      오류 코드·응답 형식, ID·시각 변환, MyBatis TypeHandler
└── config      Security·CORS·JWT, 설정(WearlyProperties), Clock
```
