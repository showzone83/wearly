# 백엔드 1차 작업 보고 (2026-10-17)

## 브랜치와 진행 경위

- 푸시한 브랜치: **`feat/backend`** (거부되지 않아 다른 이름은 쓰지 않았다). PR은 만들지 않았고 `main`·`dev`에는 푸시하지 않았다.
- 마지막 코드 커밋: **`2e9dccc`**. 이 보고서와 README 갱신은 그 바로 위 커밋에 있다 (`git log -3 origin/feat/backend`로 확인).
- 경위: 이 세션을 시작할 때(03:51 UTC)는 원격에 `feat/backend`가 없어서 `origin/dev`에서 새로 만들었다. 그런데 멈췄다고 한 앞선 실행이 03:29~03:57 UTC에 `feat/backend`로 4개 커밋(구현·테스트 41개·README·작업 보고)을 이미 올려 두었고, 내 첫 푸시가 거부되면서 이를 알았다. 지시대로 **그 브랜치를 기준으로 삼아 이어서 작업했다** (force push 없음).
  - 이 세션에서 따로 만든 구현은 앞선 실행과 설계가 거의 같아서 버렸고(푸시하지 않음), 검토하면서 찾은 문제와 더 나은 부분만 아래 커밋으로 옮겼다.
- `mobile/`, `harness/`, `doc/`는 건드리지 않았다. 바뀐 것은 `backend/` 아래뿐이다.

| 커밋 | 내용 |
|---|---|
| `138a1b3` ~ `bff393b` | (앞선 실행) 프로젝트 골격, 인증·프로필·옷장·AI API, 테스트 41개, README |
| `c7bdf63` | AI 일일 한도를 동시 요청에도 지키도록 수정 (회원 행 잠금 + PENDING 자리 잡기) |
| `66ee53b` | `/error`도 계약 오류 형식으로, 긴 비밀번호 500 → 400, `.env.example` 함정 제거 |
| `2e9dccc` | 테스트: JPA와 MyBatis가 같은 트랜잭션을 쓰는지 확인 |

## 확정한 버전

| 항목 | 버전 | 비고 |
|---|---|---|
| Spring Boot | **4.1.1** | Maven Central 최신 GA (4.2.0은 아직 마일스톤) |
| mybatis-spring-boot-starter | **4.1.0** | Maven Central 최신. mybatis 3.5.19, mybatis-spring 4.1.0, POM의 `spring-boot.version`은 4.1.0 |
| Anthropic Java SDK | **2.73.0** (`com.anthropic:anthropic-java`) | Maven Central 최신 |
| Gradle wrapper | 9.8.1 | Kotlin DSL, Java 21 toolchain. 이 환경에서는 9.8.1 배포판을 받을 수 없어 **9.8.0으로 실행**했다 (아래) |
| BOM으로 들어온 주요 버전 | Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate 7.4.5, Flyway 12.4.0, Jackson 3.1.5(앱)·2.21.5(SDK), PostgreSQL JDBC 42.7.13, Testcontainers 2.0.5 | |

**MyBatis 호환성**: initializr는 MyBatis를 Boot [4.0.0, 4.1.0-M1)로만 표시하지만, 4.1.0 스타터로 Boot 4.1.1에서 컨텍스트 기동, 매퍼 XML 로딩, TypeHandler(TEXT[]·JSONB), 매퍼 테스트, JPA와의 트랜잭션 공유까지 모두 통과했다. **Boot 4.0.x로 내리지 않았다.**

## 이 세션에서 고친 점

1. **AI 일일 한도 동시성** (`c7bdf63`): 한도 확인과 기록 사이에 틈이 있어, 같은 회원이 동시에 8장을 보내면 한도 3인데 **7장이 성공**했다 (새 테스트로 재현). 이제 회원 행을 잠근 트랜잭션에서 개수를 세고 `PENDING` 기록으로 자리를 먼저 잡은 뒤, 공급자는 트랜잭션 밖에서 부르고 끝나면 `SUCCESS`/`FAILED`로 바꾼다. 한도는 `SUCCESS` + 5분 안의 `PENDING`을 센다. 고친 뒤 8건 → 성공 3건.
   - 스키마: `tb_ai_analysis_log`에 `PENDING` 상태와 `completed_at`을 더했고 `latency_ms`는 nullable, 부분 인덱스는 `SUCCESS`·`PENDING` 기준이다.
   - **`V1__init.sql`을 직접 고쳤다** (아직 배포 전이라 V2를 만들지 않음). 앞선 실행의 V1로 만든 DB가 있으면 Flyway 체크섬 오류가 나므로 지우고 다시 만든다 (`docker compose down -v && docker compose up -d`).
2. **`/error` 응답 형식** (`66ee53b`): 방화벽이 거절한 URL(`;`, `%2e%2e` 등)처럼 컨테이너가 `/error`로 넘기는 오류가 Boot 기본 형식(`{timestamp, status, error, path}`)으로 나갔다. `JsonErrorController`를 더해 `{ status, code, message, fieldErrors }`로 맞췄다 (400 → `VALIDATION_FAILED` 등 상태별 코드).
3. **긴 비밀번호 500** (`66ee53b`): 64자 이내라도 한글이 섞여 UTF-8 72바이트(BCrypt 한도)를 넘으면 `encode`가 예외를 던져 가입이 500이 됐다. 이제 400 `VALIDATION_FAILED`(`field: password`). 로그인은 원래도 401이었다 (`matches`가 false를 돌려줌).
4. **`.env.example` 함정** (`66ee53b`): `WEARLY_JWT_SECRET=` 빈 줄이 빈 문자열로 기본값을 덮어서, README대로 `cp .env.example .env` 하자마자 기동이 실패했다 (직접 재현: `wearly.jwt.secret must not be blank`). 그 줄을 주석으로 바꾸고 "빈 값도 덮어쓴다"는 안내로 고쳤다. 고친 뒤 복사한 `.env`로 기동되는 것을 확인했다.
5. **JPA·MyBatis 같은 트랜잭션 검증** (`2e9dccc`): 앞선 보고서가 "따로 시험하지 않았다"고 남긴 부분. 트랜잭션 매니저는 `JpaTransactionManager` 하나이고 MyBatis는 같은 DataSource를 쓰며, 한 트랜잭션에서 JPA로 쓴 미커밋 값을 MyBatis가 읽고 롤백하면 함께 사라지는 것을 테스트로 고정했다.

## 실행한 검증

### 환경

- Docker 데몬은 꺼져 있었지만 `dockerd`를 직접 띄워 쓸 수 있었다. Docker Hub가 429(요청 제한)를 내서 `pgvector/pgvector:pg17`과 `testcontainers/ryuk:0.13.0`은 `mirror.gcr.io`에서 받아 같은 이름으로 태그했다. **Testcontainers 테스트를 이 환경에서 그대로 돌렸다** (PostgreSQL 직접 설치는 필요 없었다).
- Maven Central도 간헐적으로 429를 내서, 이 환경의 `~/.gradle/init.d`에만 Google의 Maven Central 미러를 앞에 두었다. 저장소의 빌드 파일은 `mavenCentral()` 그대로다.
- start.spring.io, GitHub 릴리스 자산(Gradle 배포판)은 이 환경의 네트워크 정책에서 막혀 있다. 그래서 Gradle은 Nix 바이너리 캐시에서 받은 **9.8.0**으로 실행했다 (`./gradlew`의 9.8.1은 실행하지 못함).

### 테스트 결과

| 명령 | 결과 |
|---|---|
| `LC_ALL=C.UTF-8 gradle test --rerun-tasks` (Testcontainers, `pgvector/pgvector:pg17`) | **46개 통과, 실패 0, 건너뜀 0** |
| `LC_ALL=C.UTF-8 WEARLY_TEST_DB_URL=jdbc:postgresql://localhost:5433/wearly_ext_test gradle test --rerun-tasks` (외부 DB 경로, compose DB) | **46개 통과, 실패 0** (컨테이너를 띄우지 않음) |

`gradle`은 Gradle 9.8.0. 사용자 Mac에서는 `./gradlew test`로 같은 테스트를 돌린다.

| 테스트 클래스 | 개수 | 이번에 더한 것 |
|---|---|---|
| `AuthIntegrationTest` | 15 | 72바이트 넘는 비밀번호 → 400 |
| `ShortAccessTtlIntegrationTest` | 1 | |
| `ProfileIntegrationTest` | 3 | |
| `WardrobeIntegrationTest` | 8 | |
| `JpaMyBatisTransactionIntegrationTest` | 2 | 새로 추가 |
| `AiAnalyzeIntegrationTest` | 6 | 동시 8건 → 성공 3건, 오래된 PENDING 제외 |
| `AiFailureIntegrationTest` | 1 | |
| `ErrorFormatIntegrationTest` | 1 | 새로 추가 |
| `ClaudeAiProviderTest` | 6 | |
| `MockAiProviderTest` | 2 | |
| `ImageTypesTest` | 1 | |

요청받은 시나리오는 모두 들어 있다: 가입·중복 가입·로그인 실패·토큰 만료(서명한 만료 토큰 + 실제 1초 TTL)·refresh 회전·재사용 감지·logout·탈퇴 후 토큰 무효·아이템 CRUD·남의 아이템 404·필터 목록·통계·bulk·analyze(mock)·일일 한도 429. 모든 통합 테스트 컨텍스트가 Flyway 마이그레이션과 `ddl-auto: validate`를 거쳐 뜬다.

수정한 세 문제(동시 한도, `/error` 형식, 긴 비밀번호)는 **고치기 전 코드에서 새 테스트가 실패하는 것을 먼저 확인**한 뒤 고쳤다.

### 하네스 설정으로 직접 확인

계약 9장과 같은 설정(`SERVER_PORT=18080 WEARLY_ACCESS_TTL=PT3S WEARLY_AI_DAILY_LIMIT=3 WEARLY_AI_PROVIDER=mock`, DB `wearly_harness`를 새로 만듦)으로 jar를 띄우고 HTTP 스크립트로 22개 항목을 확인했다. 전부 기대대로였다.

- 가입 201, `user.id`는 문자열, `accessTokenExpiresIn` 3
- 4초 뒤 `TOKEN_EXPIRED` → **만료된 Access 토큰을 Authorization에 붙인 채** `/auth/refresh` 200 → 새 토큰으로 `/users/me` 200
- 회전 전 토큰 재사용 → `REFRESH_REUSED`, 그 뒤 family의 최신 토큰도 `REFRESH_INVALID`
- 대문자 이메일 로그인, bulk 순서, stats total
- 분석 5건 동시 → 200 ×3, 429 `AI_LIMIT_EXCEEDED` ×2
- logout 204 뒤 refresh `REFRESH_INVALID`, 탈퇴 204 뒤 Access `TOKEN_INVALID`·Refresh `REFRESH_INVALID`

### 그 밖의 수동 확인

- `docker compose up -d` 후 `gradle bootRun`: Flyway 적용, JPA validate 통과, 약 8초에 기동, AI 공급자 mock.
- `cp .env.example .env` 후 기동 (수정 전 실패, 수정 후 성공).
- 방화벽 거절 URL의 응답 형식 (수정 전 Boot 기본 형식, 수정 후 계약 형식).

## 실행하지 못한 검증

- **실제 Claude API 호출**: 키가 없어 하지 못했다. `ClaudeAiProviderTest`가 SDK 클라이언트를 로컬 가짜 HTTP 서버에 붙여 실제로 나가는 요청 JSON(이미지 base64, `output_config.format` JSON 스키마 enum, `effort`, `fallbacks`)과 응답 파싱·검증·거절·API 오류 처리를 확인한다. 실제 모델이 이 스키마·프롬프트로 좋은 값을 주는지, `fallbacks: "default"`와 베타 헤더 `server-side-fallback-2026-07-01`을 이 계정에서 받아 주는지는 키를 넣고 확인해야 한다.
- **앱(`mobile/src/api`)·하네스(`harness/`)와의 실제 연동**: 이 브랜치(dev 기준)에는 두 폴더가 없어 돌려 보지 못했다. 계약 문서만 보고 맞췄고, 위의 하네스 설정 스크립트로 대신 확인했다.
- **Gradle wrapper 9.8.1 자체**: 배포판을 받을 수 없어 9.8.0으로 돌렸다. 빌드 스크립트는 9.x 공통 기능만 쓴다.
- **사용자 Mac 환경**: macOS·Docker Desktop에서 Testcontainers 실행은 확인하지 못했다 (리눅스 Docker 29 + cgroup v1에서 확인).

## 계약과 다르게(또는 빈틈을 채워) 정한 점

계약 문서는 고치지 않았다. 아래는 계약에 없거나 모호해서 정한 것이다 (앞선 실행이 정한 것을 검토해 그대로 두었고, 이번에 바꾼 것은 표시했다).

1. **추가 오류 코드**: `FORBIDDEN`(403), `METHOD_NOT_ALLOWED`(405), `UNSUPPORTED_MEDIA_TYPE`(415, JSON 엔드포인트에 Content-Type이 틀리거나 analyze가 multipart가 아닌 경우), `INTERNAL_ERROR`(500)를 같은 JSON 형식으로 낸다. 없는 경로는 인증 후 404 `NOT_FOUND`. **(이번)** `/error`로 가는 컨테이너 오류도 같은 형식(400이면 `VALIDATION_FAILED`, `fieldErrors`는 빈 배열).
2. **`/auth/refresh`에 토큰이 없거나 빈 문자열**이면 400이 아니라 401 `REFRESH_INVALID` (계약 표의 "Refresh 토큰 없음").
3. **공개 경로(`/api/v1/auth/**`)는 Authorization 헤더를 무시**한다. 앱이 만료된 Access 토큰을 붙인 채 refresh를 불러도 막히지 않는다.
4. **JWT 시계 오차 허용 0초**. Spring 기본값(60초)이면 하네스의 `PT3S` 만료가 1분 동안 안 일어난다.
5. **ID 경로 변수가 숫자가 아니면 404** (예: `/wardrobe/items/abc`).
6. **시각 형식**: `createdAt`은 밀리초까지 ISO-8601 UTC (`2026-10-17T03:39:37.430Z`, 0밀리초면 `…:37Z`). JS `Date`는 둘 다 읽는다.
7. **검증 길이**: 아이템 `name` 1~40자, `brand` 최대 40자, `wornCount` ≥ 0. `seasons`·`styles`는 빈 배열 허용, 중복은 제거하고 순서는 지킨다. 프로필 `workDays`는 중복 제거 후 오름차순으로 저장한다. **(이번)** 비밀번호는 8~64자 규칙에 더해 UTF-8 72바이트 이하(BCrypt 한도).
8. **프로필**: 저장 전 GET의 `name`은 기본값 "웨어리 사용자"가 아니라 **가입한 회원 이름**이다 (나머지는 `DEFAULT_PROFILE`과 같음). `PUT`에서 `onboarded`를 빼면 `true`로 저장한다. 이름은 `tb_user.name`에만 둔다.
9. **PATCH**: 보낸 필드만 덮어쓰고, `null`을 보내면 그 값을 비운다(`brand`, `lastWornAt`). 필수 필드에 `null`은 400. 병합 결과에 등록과 같은 검증(짝 검증 포함)을 한다. 모르는 필드(`id`, `createdAt` 등)는 무시한다.
10. **bulk**: 빈 배열은 201 + 빈 목록. 하나라도 틀리면 아무것도 저장하지 않고 400 (`field`는 `items[3].subType` 형태).
11. **목록 정렬**: `recent` = 등록 최신순, `worn` = 착용 횟수 많은 순 → 최근 착용일 → id, `name` = `lower(name) COLLATE "C"` (가나다·유니코드 순, DB 로케일과 무관). 잘못된 `category`·`status`·`color`·`sort`는 400. `q`는 이름·브랜드 대소문자 무시 부분 일치, `%`·`_`는 글자 그대로 찾는다.
12. **통계** (Asia/Seoul): `wornLast30Days` = 마지막 착용일이 오늘 포함 최근 30일(오늘-29일 ~ 오늘). `sleeping` = 한 번도 안 입었거나 마지막 착용일이 오늘-60일보다 이전, 안 입은 것·오래된 것부터 최대 10개, 상태와 무관. `byColor`·`byStyle`은 개수가 같으면 코드 알파벳순. `utilization30d`는 HALF_UP 소수 둘째 자리, 아이템이 없으면 0. (참고: 앱 마이 탭은 `diffDays <= 30`, 옷장 분석은 `< 30`으로 서로 다르다. 서버는 후자와 같다.)
13. **AI 분석**
    - 형식: 파트의 Content-Type이 jpeg(jpg)·png·webp·heic(heif)이면 받고, 없거나 `application/octet-stream`이면 첫 바이트로 판별한다. 그 밖은 400 `VALIDATION_FAILED`(`field: image`). 빈 파일·파트 없음도 400.
    - 크기: 10MB(10,485,760바이트)까지, 넘으면 413. multipart 한도와 서비스 검사 둘 다 둔다.
    - **(이번)** 한도: 성공 + 진행 중(5분 안의 `PENDING`)을 센다. 동시 요청에도 한도를 넘지 않는다. 진행 중인 요청이 있으면 그만큼 `remainingToday`가 먼저 줄어 보인다.
    - 공급자 실패는 `FAILED`와 `error_code`로 남기고 한도에서 뺀다. 이미지는 저장하지 않고 SHA-256과 바이트 수만 남긴다.
    - **HEIC + Claude**: Claude API는 HEIC를 받지 않아 Claude 공급자에서 HEIC는 502 `AI_UNAVAILABLE`이다 (목업은 정상).
    - Claude 응답 `name`이 40자를 넘으면 잘라 쓴다. 허용 목록 밖 값·짝 불일치·범위 밖 숫자는 거부 → 502.
    - 응답 `model`은 실제로 답한 모델 ID다 (폴백 시 설정값과 다를 수 있다).
14. **Claude 요청**: 모델 `claude-opus-5-5`(설정 가능), `max_tokens` 16000, thinking 파라미터 없음(Opus 5.5는 항상 적응형 사고), `output_config.effort`는 `medium`(`WEARLY_AI_EFFORT`), 구조화 출력은 `output_config.format` JSON 스키마에 허용 값을 `enum`으로 넣는다. 거절 대비로 서버 측 폴백(`fallbacks: "default"` + 베타 헤더)을 켰다. 원치 않으면 `ClaudeAiProvider.buildParams`에서 두 줄을 지운다.
15. **스키마**
    - `tb_user.status`는 `ACTIVE`·`SUSPENDED`. 탈퇴는 행 삭제(하위 데이터 CASCADE)다.
    - `tb_user_profile`·`tb_user_local_credential`도 identity PK를 두고 `user_id`는 UNIQUE.
    - 테이블만 만든 것들(코디·피드백·예정일·착용 기록·진단·리포트·이미지·취향)의 컬럼은 앱 `types.ts` 모양을 보고 정했다. 2차 API에서 바뀔 수 있다.
    - `vector` 확장은 DB에 있을 때만 만든다. 아직 vector 컬럼은 없다 (임베딩 모델·차원을 정한 뒤 추가).
    - **(이번)** `tb_ai_analysis_log.status`에 `PENDING` 추가, `completed_at` 추가, `latency_ms` nullable.

## 로컬에서 보강이 필요한 점

1. **이전 V1로 만든 DB 정리**: 앞선 실행의 브랜치로 `bootRun`을 해 본 적이 있으면 V1 체크섬이 달라 기동이 실패한다. `docker compose down -v && docker compose up -d`로 다시 만든다 (하네스 DB는 매번 새로 만들므로 상관없다).
2. **Mac에서 `./gradlew test`**: Testcontainers가 Docker Desktop에서 뜨는지, wrapper 9.8.1이 받아지는지 확인 (처음엔 `pgvector/pgvector:pg17` 이미지를 받는다).
3. **앱·하네스 연동**: `mobile/src/api`와 `harness/`를 이 브랜치와 합쳐 하네스 시나리오를 돌린다. 특히 위 "정한 점" 2·6·8·9·11·13번이 앱 기대와 맞는지.
4. **Claude 실제 호출**: `backend/.env`에 `ANTHROPIC_API_KEY`를 넣고 실제 옷 사진으로 품질·지연·토큰 수 확인. `effort`와 프롬프트 조정이 필요할 수 있다. 서버 측 폴백 베타가 받아들여지는지도 확인.
5. **HEIC**: iOS 사진은 HEIC가 많다. 앱에서 `expo-image-manipulator` 등으로 JPEG로 바꿔 올리면 Claude 공급자에서도 동작한다 (서버 변환은 하지 않았다).
6. **운영 준비**(범위 밖): 소셜 로그인, Redis 요청 제한, 로그 마스킹, `WEARLY_JWT_SECRET` 비밀 관리, CORS origin 운영 값, PostgreSQL RLS.
7. 리눅스 CI에서 돌릴 때는 `LC_ALL=C.UTF-8`을 준다 (한글 테스트 이름으로 보고서 파일을 만들 때 필요).
