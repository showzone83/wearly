# 백엔드 1차 작업 보고 (2026-10-17)

## 브랜치

- `feat/backend` (기준: `origin/dev`, 계약 문서 커밋 `3f1c5d7` 위). PR은 만들지 않았다.
- `mobile/`, `harness/`, `doc/`는 건드리지 않았다. 바뀐 것은 `backend/` 아래뿐이다.

## 확정한 버전

| 항목 | 버전 | 비고 |
|---|---|---|
| Spring Boot | **4.1.1** | Gradle 플러그인 `io.spring.dependency-management` 1.1.7 |
| Gradle wrapper | 9.8.1 | Kotlin DSL, Java 21 toolchain |
| mybatis-spring-boot-starter | **4.1.0** | Maven Central 최신. mybatis 3.5.19, mybatis-spring 4.1.0, Boot 4.1.0 기준으로 빌드된 버전 |
| Anthropic Java SDK | **2.73.0** (`com.anthropic:anthropic-java`) | Maven Central 최신 |
| 함께 들어온 주요 버전 | Hibernate 7.4.5, Flyway 12.4.0, Jackson 3.1.5, PostgreSQL JDBC 42.7.13, Testcontainers 2.0.5 | Boot BOM |

**MyBatis 호환성**: initializr는 MyBatis를 Boot [4.0.0, 4.1.0-M1)로만 표시하지만, Maven Central에 4.1.0 스타터가 있고 Boot 4.1.1에서 컨텍스트 기동, 매퍼 XML 로딩, TypeHandler(TEXT[]·JSONB), 매퍼 직접 호출 테스트가 모두 통과했다. **Boot 4.0.x로 내리지 않았다.**
start.spring.io는 이 클라우드 환경의 네트워크 정책에서 막혀 있어서 프로젝트를 받지 않고 직접 구성했다 (빌드 파일·wrapper는 initializr 기본 구성과 같은 모양).

## 실행한 검증

### 환경

- 처음에는 Docker 데몬이 꺼져 있었지만 `dockerd`를 직접 띄워 Docker를 쓸 수 있었다. 그래서 **Testcontainers 테스트를 이 환경에서 그대로 돌렸다** (PostgreSQL 직접 설치는 필요 없었다).
- 루트 `docker-compose.yml`의 `wearly-postgres`(pgvector/pgvector:pg17, PostgreSQL 17.11, vector 0.8.7)도 띄워 `bootRun`과 외부 DB 테스트 경로를 확인했다.

### 테스트 결과

| 명령 | 결과 |
|---|---|
| `LC_ALL=C.UTF-8 ./gradlew test` (Testcontainers) | **41개 전부 통과, 실패 0, 건너뜀 0** |
| `LC_ALL=C.UTF-8 WEARLY_TEST_DB_URL=jdbc:postgresql://localhost:5433/wearly_ext_test ./gradlew test --rerun-tasks` (외부 DB 경로) | **41개 전부 통과** (컨테이너를 만들지 않고 compose DB에 붙음) |

| 테스트 클래스 | 개수 |
|---|---|
| `AuthIntegrationTest` | 15 |
| `ShortAccessTtlIntegrationTest` | 1 |
| `ProfileIntegrationTest` | 3 |
| `WardrobeIntegrationTest` | 8 |
| `AiAnalyzeIntegrationTest` | 4 |
| `AiFailureIntegrationTest` | 1 |
| `ClaudeAiProviderTest` | 6 |
| `MockAiProviderTest` | 2 |
| `ImageTypesTest` | 1 |

요청받은 항목은 모두 들어 있다: 가입·중복·로그인 실패·토큰 만료(서명한 만료 토큰 + 실제 1초 TTL)·refresh 회전·재사용 감지·logout·탈퇴 후 토큰 무효·아이템 CRUD·남의 아이템 404·필터 목록·통계·bulk·analyze(mock)·일일 한도 429. 모든 통합 테스트 컨텍스트가 Flyway 마이그레이션과 `ddl-auto: validate`를 거쳐 뜬다.

### 수동 확인 (curl)

- `docker compose up -d` 후 `./gradlew bootRun`: Flyway V1 적용, JPA validate 통과, 기동 약 12초.
- 하네스와 같은 설정(`SERVER_PORT=18080 WEARLY_ACCESS_TTL=PT3S WEARLY_AI_DAILY_LIMIT=3 WEARLY_AI_PROVIDER=mock`, DB `wearly_harness`)으로 jar를 띄워 3초 뒤 `TOKEN_EXPIRED`가 나오는 것을 확인.
- `backend/.env`의 값(`SERVER_PORT`, `WEARLY_AI_DAILY_LIMIT`)이 반영되는 것을 확인 (확인 뒤 파일 삭제).
- `SPRING_PROFILES_ACTIVE=prod`에서 `WEARLY_JWT_SECRET`이 없으면 기동 실패하는 것을 확인.

## 실행하지 못한 검증

- **실제 Claude API 호출**: 키가 없어 하지 못했다. 대신 SDK 클라이언트를 로컬 가짜 HTTP 서버에 붙여 실제로 나가는 요청 JSON(이미지 base64, `output_config.format` JSON 스키마 enum, `effort`, `fallbacks`)과 응답 파싱·검증·거절·API 오류 처리를 확인했다. 실제 모델이 이 스키마와 프롬프트로 좋은 값을 주는지, `fallbacks: "default"` + 베타 헤더를 서버가 받아들이는지는 키를 넣고 확인해야 한다.
- **앱(`mobile/src/api`)·하네스(`harness/`)와의 실제 연동**: 이 브랜치(dev 기준)에는 두 폴더가 아직 없어 돌려 보지 못했다. 계약 문서만 보고 맞췄다.
- **사용자 Mac 환경**: macOS·Docker Desktop에서의 Testcontainers 실행은 확인하지 못했다 (리눅스 Docker 29에서 확인).
- JPA와 MyBatis를 **한 트랜잭션 안에서 섞어 쓰는 경우**는 지금 코드에 없어서 따로 시험하지 않았다 (같은 DataSource·`JpaTransactionManager`라 동작해야 한다).

## 계약과 다르게(또는 빈틈을 채워) 정한 점

계약 문서는 고치지 않았다. 아래는 계약에 없거나 모호해서 정한 것이다.

1. **추가 오류 코드**: 계약 표에 없는 상황용으로 `FORBIDDEN`(403), `METHOD_NOT_ALLOWED`(405), `UNSUPPORTED_MEDIA_TYPE`(415, JSON 엔드포인트에 Content-Type이 틀린 경우), `INTERNAL_ERROR`(500)를 같은 JSON 형식으로 낸다. 없는 경로는 인증 후 404 `NOT_FOUND`.
2. **`/auth/refresh`에 토큰이 없거나 빈 문자열**이면 400이 아니라 401 `REFRESH_INVALID` (계약 표의 "Refresh 토큰 없음"을 따름).
3. **공개 경로(`/api/v1/auth/**`)는 Authorization 헤더를 무시**한다. 앱이 만료된 Access 토큰을 붙인 채 refresh를 불러도 막히지 않게 하려는 것.
4. **JWT 시계 오차 허용 0초**. Spring 기본값(60초)이면 하네스의 `PT3S` 만료가 1분 동안 안 일어난다.
5. **ID 경로 변수가 숫자가 아니면 404** (예: `/wardrobe/items/abc`).
6. **시각 형식**: `createdAt`은 밀리초까지 ISO-8601 UTC (`2026-10-17T03:39:37.430Z`). 계약 예시(`…T03:00:00Z`)처럼 초 단위로 자르지는 않았다. JS `Date`는 둘 다 읽는다.
7. **검증 길이** (계약에 없음): 아이템 `name` 1~40자, `brand` 최대 40자, `wornCount` ≥ 0. `seasons`·`styles`는 빈 배열 허용, 중복은 제거하고 순서는 지킨다. 프로필 `workDays`는 중복 제거 후 오름차순으로 저장한다.
8. **프로필**: 저장 전 GET의 `name`은 기본값 "웨어리 사용자"가 아니라 **가입한 회원 이름**을 준다 (나머지는 `DEFAULT_PROFILE`과 같음). `PUT`에서 `onboarded`를 빼면 `true`로 저장한다. 이름은 `tb_user.name`에만 두고 프로필 테이블에는 따로 두지 않는다.
9. **PATCH 의미**: 보낸 필드만 덮어쓰고, `null`을 보내면 그 값을 비운다(`brand`, `lastWornAt`). 필수 필드에 `null`을 보내면 400. 병합 결과에 등록과 같은 검증(짝 검증 포함)을 한다. `id`·`createdAt` 등 모르는 필드는 무시한다.
10. **bulk**: 빈 배열은 201 + 빈 목록. 하나라도 틀리면 아무것도 저장하지 않고 400 (`field`는 `items[3].subType` 형태).
11. **목록 정렬**: `recent` = 등록 최신순, `worn` = 착용 횟수 많은 순 → 최근 착용일 → id, `name` = `lower(name) COLLATE "C"` (가나다·유니코드 순). DB의 glibc `en_US.utf8` 정렬은 한글 순서가 어긋나서(화 < 블 < 숫자) 로케일과 무관한 정렬을 골랐다. 영문은 대소문자 무시. 잘못된 `category`·`status`·`color`·`sort` 값은 400. `q`는 이름·브랜드 대소문자 무시 부분 일치, `%`·`_`는 글자 그대로 찾는다.
12. **통계 날짜 기준** (Asia/Seoul): `wornLast30Days` = 마지막 착용일이 오늘 포함 최근 30일(오늘-29일 ~ 오늘). `sleeping` = 한 번도 안 입었거나 마지막 착용일이 오늘-60일보다 이전, 안 입은 것·오래된 것부터 최대 10개, 상태(세탁·수선 중)와 상관없이 포함. `byColor`·`byStyle`은 개수가 같으면 코드 알파벳순. `utilization30d`는 반올림(HALF_UP) 소수 둘째 자리, 아이템이 없으면 0.
13. **AI 분석**
    - 형식: 파트의 Content-Type이 jpeg(jpg)·png·webp·heic(heif)이면 받고, 없거나 `application/octet-stream`이면 첫 바이트로 판별한다. 그 밖은 400 `VALIDATION_FAILED`(`field: image`). 빈 파일·파트 없음도 400.
    - 크기: 정확히 10MB(10,485,760바이트)까지 허용, 넘으면 413. multipart 한도(`max-file-size: 10MB`)와 서비스 검사 둘 다 둔다.
    - 한도는 호출 전 확인한다. 동시에 여러 요청이 오면 한도를 1~2회 넘길 수 있다 (락 없음, Redis 요청 제한은 범위 밖).
    - 공급자 실패도 `tb_ai_analysis_log`에 `status='FAILED'`와 `error_code`로 남기되 한도에는 세지 않는다. 로그에는 이미지 대신 SHA-256과 바이트 수를 남긴다.
    - **HEIC + Claude**: Claude 비전 API는 HEIC를 받지 않는다. Claude 공급자일 때 HEIC 사진은 502 `AI_UNAVAILABLE`가 된다 (목업은 정상). → 앱에서 JPEG로 바꿔 보내는 것을 권장 (아래 보강 항목).
    - Claude 응답 `name`이 40자를 넘으면 잘라서 쓴다 (프롬프트는 20자 이내를 요청). 나머지 허용 목록 밖 값·짝 불일치·범위 밖 숫자는 거부 → 502.
    - 응답 `model`은 실제로 응답한 모델 ID다 (폴백 시 설정값과 다를 수 있음).
14. **Claude 요청 구성**: 모델 `claude-opus-5-5`(설정 가능), `max_tokens` 16000, thinking 파라미터는 보내지 않음(Opus 5.5는 항상 적응형 사고), `output_config.effort`는 `medium`(`WEARLY_AI_EFFORT`로 변경), 구조화 출력은 `output_config.format`의 JSON 스키마에 허용 값을 `enum`으로 넣음. 거절(refusal) 대비로 **서버 측 폴백**(`fallbacks: "default"`, 베타 헤더 `server-side-fallback-2026-07-01`)을 켰다. 원치 않으면 `ClaudeAiProvider.buildParams`에서 두 줄을 지우면 된다.
15. **스키마 세부**
    - `tb_user.status`는 `ACTIVE`·`SUSPENDED`. 탈퇴는 상태 변경이 아니라 행 삭제(하위 데이터 CASCADE)다.
    - `tb_user_profile`·`tb_user_local_credential`도 identity PK를 두고 `user_id`는 UNIQUE.
    - 테이블만 만든 것들(코디·피드백·예정일·착용 기록·진단·리포트·이미지·취향)의 컬럼은 앱 `types.ts` 모양을 보고 정했다. 2차 API에서 바뀔 수 있다.
    - `vector` 확장은 DB에 설치돼 있을 때만 만든다 (`pg_available_extensions` 확인). 지금은 vector 컬럼이 없다.

## 로컬에서 보강이 필요한 점

1. **Mac에서 `./gradlew test`** 한 번 돌려 Testcontainers가 Docker Desktop에서 뜨는지 확인 (처음엔 `pgvector/pgvector:pg17` 이미지를 받는다).
2. **앱·하네스 연동**: `mobile/src/api`와 `harness/`를 이 브랜치와 합친 뒤 하네스 시나리오를 돌려 계약 차이가 없는지 확인. 특히 위 "정한 점" 2·6·8·9·11·13번이 앱 기대와 맞는지.
3. **Claude 실제 호출**: `backend/.env`에 `ANTHROPIC_API_KEY`를 넣고 실제 옷 사진 몇 장으로 분석 품질·지연·토큰 수 확인. `effort`(`low`/`medium`)와 프롬프트 조정이 필요할 수 있다. 서버 측 폴백 베타가 이 계정에서 받아들여지는지도 확인.
4. **HEIC**: iOS 사진은 HEIC가 많다. 앱에서 `expo-image-manipulator` 등으로 JPEG로 바꿔 올리면 Claude 공급자에서도 동작한다 (서버 변환은 하지 않았다).
5. **운영 준비**(범위 밖으로 남긴 것): 소셜 로그인, AI 한도의 동시성 보장(Redis/Bucket4j 또는 DB 락), 로그 마스킹, `WEARLY_JWT_SECRET` 비밀 관리, CORS origin 운영 값, PostgreSQL RLS.
6. 리눅스 CI에서 돌릴 때는 `LC_ALL=C.UTF-8`을 준다 (한글 테스트 이름 보고서 파일 생성 문제).
