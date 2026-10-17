# Wearly 백엔드

웨어리 앱의 API 서버. 계약은 [`doc/05_백엔드_API.md`](../doc/05_백엔드_API.md)를 따른다 (1차: 토큰 로그인 · 옷장 · AI 분석).

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.1 (Java 21 toolchain, Gradle 9.8.1 wrapper, Kotlin DSL) |
| 데이터 접근 | Spring Data JPA (Hibernate 7.4) + MyBatis (mybatis-spring-boot-starter 4.1.0) |
| DB | PostgreSQL 17 + pgvector, 스키마는 Flyway (`src/main/resources/db/migration`) |
| 인증 | Spring Security OAuth2 Resource Server, JWT HS256 (Nimbus) |
| AI | Anthropic Java SDK 2.73.0 (`com.anthropic:anthropic-java`), 기본은 목업 공급자 |

## 실행

```bash
# 1) 저장소 루트에서 DB 띄우기 (localhost:5433, DB·계정·비밀번호 wearly)
docker compose up -d

# 2) 서버 실행 (backend/ 에서)
cd backend
cp .env.example .env   # 처음 한 번. 필요한 값만 채운다 (기본값을 쓸 항목은 주석으로 둔다)
./gradlew bootRun
```

- 서버: `http://localhost:8080`, API 기본 경로 `/api/v1`, 상태 확인 `GET /actuator/health`
- 처음 뜰 때 Flyway가 `V1__init.sql`로 테이블을 만든다. JPA는 `ddl-auto: validate`라서 스키마와 엔티티가 어긋나면 기동이 실패한다.
- `ANTHROPIC_API_KEY`를 비워 두면 목업 공급자로 사진 분석을 한다 (같은 사진 = 같은 결과).
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
| `WEARLY_AI_PROVIDER` | `auto` | `mock` · `claude` · `auto`(키가 있으면 Claude) |
| `WEARLY_AI_MODEL` | `claude-opus-5-5` | Claude 모델 ID |
| `WEARLY_AI_DAILY_LIMIT` | `10` | 회원별 하루 AI 분석 횟수 (Asia/Seoul 날짜, 성공만 센다. 동시 요청에도 넘지 않는다) |
| `WEARLY_AI_EFFORT` | `medium` | Claude `output_config.effort` (`low`~`max`) |
| `WEARLY_AI_TIMEOUT` | `PT60S` | Claude 호출 제한 시간 |
| `ANTHROPIC_API_KEY` | (없음) | Claude API 키. 서버에만 둔다 |

하네스 예: `SERVER_PORT=18080 WEARLY_ACCESS_TTL=PT3S WEARLY_AI_DAILY_LIMIT=3 WEARLY_AI_PROVIDER=mock SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/wearly_harness`

## 테스트

```bash
./gradlew test
```

- 통합 테스트는 **Testcontainers**로 `pgvector/pgvector:pg17` 컨테이너를 한 번 띄워 쓴다 (Docker 필요). 서버를 무작위 포트로 띄워 실제 HTTP로 부른다.
- Docker 없이 이미 떠 있는 DB로 돌리려면 `WEARLY_TEST_DB_URL`을 준다. 테스트는 매번 새 이메일로 데이터를 만들고 지우지 않으므로 **개발 DB가 아닌 빈 DB**를 쓴다.

  ```bash
  docker exec wearly-postgres psql -U wearly -c "create database wearly_test"
  WEARLY_TEST_DB_URL=jdbc:postgresql://localhost:5433/wearly_test ./gradlew test
  # 계정이 다르면 WEARLY_TEST_DB_USERNAME / WEARLY_TEST_DB_PASSWORD
  ```

- Claude 공급자는 실제 키 없이 로컬 가짜 Messages API 서버로 요청 본문·응답 파싱·허용 목록 검증을 확인한다 (`ClaudeAiProviderTest`).
- 리눅스에서 로케일이 UTF-8이 아니면 Gradle이 한글 테스트 이름으로 보고서 파일을 못 만든다. `LC_ALL=C.UTF-8 ./gradlew test`로 돌린다 (macOS는 해당 없음).

| 테스트 | 확인하는 것 |
|---|---|
| `AuthIntegrationTest` | 가입·중복(대소문자 무시)·입력 검증, 로그인 실패, 토큰 없음/위조/다른 발급자, 만료 → `TOKEN_EXPIRED`, refresh 회전·재사용 감지(family 폐기, 다른 기기 family는 유지), 만료·없는 refresh, logout, 탈퇴 후 토큰 무효·하위 데이터 삭제, CORS, health |
| `ShortAccessTtlIntegrationTest` | Access 수명 1초로 띄워 실제 만료 후 refresh |
| `ProfileIntegrationTest` | 저장 전 기본값(`DEFAULT_PROFILE`)·`onboarded:false`, PUT 저장과 이름 동기화, 검증 |
| `WardrobeIntegrationTest` | CRUD·PATCH 병합, 남의 아이템 404, 카테고리·세부 종류 짝, 필터·정렬·검색(LIKE 이스케이프), 통계, bulk 순서·100개 제한·전부 아니면 전무, MyBatis TEXT[] 매핑 |
| `AiAnalyzeIntegrationTest` | 목업 결정성, 일일 한도 429, 동시 요청 8건 → 성공 3건(한도 3), 오래된 PENDING 제외, 10MB 초과 413, 형식·파트 검증, MyBatis JSONB 매핑 |
| `AiFailureIntegrationTest` | 공급자 오류 502, 실패는 한도에 안 셈 |
| `ClaudeAiProviderTest` 등 | Claude 요청 구성·응답 검증, 목업 짝 보장, 이미지 형식 판별 |
| `JpaMyBatisTransactionIntegrationTest` | 트랜잭션 매니저·DataSource가 하나, 한 트랜잭션에서 JPA가 쓴 미커밋 값을 MyBatis가 읽고 롤백하면 함께 사라짐 |
| `ErrorFormatIntegrationTest` | 방화벽이 거절한 URL 등 `/error`로 가는 오류도 계약 형식 |

## JPA와 MyBatis 분담

같은 DataSource와 트랜잭션 매니저(`JpaTransactionManager`)를 쓴다. MyBatis는 Spring이 관리하는 커넥션에 참여하므로 한 `@Transactional` 안에서 섞어 써도 된다.

| | JPA (쓰기·단건) | MyBatis (읽기 전용) |
|---|---|---|
| 회원·자격 증명·Refresh 토큰 | `User`, `LocalCredential`, `RefreshToken` | |
| 프로필 | `UserProfile` (TEXT[]·SMALLINT[]는 `@JdbcTypeCode(SqlTypes.ARRAY)`, 알림 JSONB는 `SqlTypes.JSON`) | |
| 옷장 | 등록·bulk·PATCH·삭제·단건 조회 (`WardrobeItem`) | 동적 필터 목록, `/wardrobe/stats` 집계 (`WardrobeQueryMapper.xml`) |
| AI | 호출 기록 저장 (`AiAnalysisLog`, 결과 JSONB), 한도 확인 때 회원 행 잠금 | 일일 사용량 집계, 최근 기록 조회 (`AiUsageMapper.xml`) |

- 매퍼 XML: `src/main/resources/mapper/*.xml`
- TypeHandler (`app.wearly.common.mybatis`): `StringListArrayTypeHandler`(TEXT[] ↔ `List<String>`), `JsonbMapTypeHandler`(JSONB ↔ `Map`). 매퍼의 `resultMap`에서 `typeHandler`로 지정한다.

### AI 일일 한도

1. 회원 행을 잠근 트랜잭션(`SELECT ... FOR UPDATE`)에서 오늘 쓴 횟수를 MyBatis로 센다. 한도면 429.
2. 남았으면 `tb_ai_analysis_log`에 `PENDING` 기록을 남겨 자리를 잡고 커밋한다.
3. 공급자(Claude)는 트랜잭션 밖에서 부른다. 끝나면 `SUCCESS` 또는 `FAILED`로 바꾼다.

센 횟수 = `SUCCESS` + 5분 안에 시작한 `PENDING`. 실패와 오래된 `PENDING`(서버가 중간에 죽은 경우)은 세지 않는다.

## 패키지 구조

```
app.wearly
├── auth        가입·로그인·refresh·logout, JWT 발급, 인증 오류 응답(EntryPoint)
├── user        회원(/users/me), 프로필
├── wardrobe    옷장 아이템, 목록·통계(MyBatis)
├── ai          AiProvider(Mock/Claude), 분석 서비스, 호출 기록
├── domain      앱 types.ts와 같은 열거형 (Category, SubType, ColorKey …)
├── common      오류 코드·응답 형식, ID·시각 변환, MyBatis TypeHandler
└── config      Security·CORS·JWT, 설정(WearlyProperties), Clock
```
