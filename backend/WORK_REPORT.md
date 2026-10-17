# 백엔드 3차 작업 보고 (2026-10-17): 헥사고날 구조 + AI 키 웹 입력

대상 계약: [08_백엔드_구조.md](../doc/08_백엔드_구조.md)(1단계), [07_관리자_사이트.md](../doc/07_관리자_사이트.md) 6장(2단계). 계약 문서(06·07·08)는 고치지 않았다.

## 브랜치

- `feat/backend-hexagonal` (기준: `origin/dev`의 `41acc14`). 이 이름으로 푸시됐다. PR은 만들지 않았다.
- 커밋: 1단계 `97509d4` → 2단계(이 보고와 함께).
- `backend/.gitignore`의 `out/`(IDE 출력 폴더)가 `port/out`·`adapter/out` 패키지까지 무시해 처음 푸시한 1단계 커밋(`cbecab0`)에 새 파일 31개가 빠져 있었다. 규칙을 `/out/`·`/bin/`(backend 루트만)으로 고치고 1단계 커밋을 고쳐 다시 푸시했다(`97509d4`, 이 브랜치는 이 세션에서 만든 것). 고친 커밋만 따로 체크아웃해 112개 통과를 확인했다.
- 바뀐 곳: `backend/` 전부, `doc/05_백엔드_API.md`(3차 관련 부분만). `mobile/`·`harness/`·`admin/`·`doc/06_*`·`doc/07_*`·`doc/08_*`는 건드리지 않았다.
- 새 라이브러리: `com.tngtech.archunit:archunit-junit5:1.5.1`(테스트만, Maven Central 최신). 암호화는 JDK `javax.crypto`라 추가 의존성 없음.

## 1단계: 헥사고날 구조 (동작 변경 없음)

### 컨텍스트별 패키지

모든 컨텍스트가 `domain` / `application(port.in · port.out · service)` / `adapter(in.web · out.persistence · out.query · out.<기술>)` 모양이다.

| 컨텍스트 | domain (순수 자바) | port.in (주요) | 출력 어댑터 |
|---|---|---|---|
| `auth` | `RefreshToken`(회전·재사용 판단 `check()`), `RefreshTokens`(원문 생성·SHA-256), `LocalCredential`, `TokenPolicy` | `AuthUseCase`, `AuthenticationUseCase`(매 요청 회원 상태), `AccountUseCase`(시작 시 관리자·재확인·토큰 폐기) | `persistence`(자격 증명·Refresh 토큰 JPA), `security`(BCrypt, JWT 발급) |
| `user` | `User`, `UserProfile`(기본값·저장 전 정리), `DressCode`, `WorkType` | `UserUseCase`, `ProfileUseCase` | `persistence`(회원·프로필 JPA) |
| `wardrobe` | `WardrobeItem`, `ItemInput`, `ItemCodeValidator`, `WardrobeStatsRules`, `KeyCount` | `WardrobeUseCase`, `WardrobeStatsUseCase` | `persistence`(쓰기·단건 JPA), `query`(목록·통계 MyBatis) |
| `code` | `Code`, `CodeAttrs`, `CodeRules` | `CodeCatalog`(스냅샷), `CodeManagementUseCase` | `persistence`(관리자 쓰기 JPA), `query`(스냅샷 캐시·사용 수 MyBatis) |
| `ai` | `AiProviderSetting`, `AiProviderSelection`, `AiRunMode`, `AiProviderType`·`OptionSpec`, `AnalysisPrompt`, `ImageTypes`, `AiCallRecord` … | `AnalyzeImageUseCase`, `ProviderTestUseCase`, `AiProviderAdminUseCase`, `AiStatusUseCase` | `persistence`, `query`, `ai/claude`·`ai/openai`·`ai/gemini`·`ai/mock`(공급자), `env`(키 환경변수) |
| `admin` | `AuditTargets`, `SearchText` | `AdminDashboardUseCase`, `AdminUserUseCase`, `AdminCodeUseCase`, `AdminAiProviderUseCase`, `EnsureAdminUseCase` | `persistence`(변경 이력 = `RecordAuditPort`), `query`(대시보드·목록 읽기 모델 MyBatis) |
| `common` | `domain`(FieldError·PatchBody·ImageUpload·Ids·Times·Zones, `code.CodeSnapshot` 등), `error`, `web`(CurrentUser·PathIds·Uploads·JsonPatchBody), `config`, `mybatis` | | |

- JPA 엔티티는 `*Entity`로 이름을 바꿔 `adapter.out.persistence`에만 두고, 어댑터가 도메인 모델과 바꾼다. 쓰기 포트는 JPA, 조회 포트(목록·검색·집계)는 MyBatis. XML은 `resources/mapper/<context>/`.
- 컨텍스트 사이 호출은 상대 `port.in`으로만 한다. 관리자 API는 admin 컨텍스트가 code·ai·user·auth 유스케이스를 같은 트랜잭션에서 부르고 `RecordAuditPort`로 변경 이력을 남긴다.
- `MultipartFile`은 웹 어댑터에서 `ImageUpload`(byte[]·MIME)로, PATCH JSON은 `PatchBody`(관리자 API)·`ItemPatch`(옷장)로 바꿔 넘긴다. 유스케이스는 Jackson·Bean Validation을 모른다.
- 환경변수(`WearlyProperties`)는 `common.config.ApplicationSettingsConfig`가 유스케이스용 값(`AiSettings`, `TokenPolicy`)으로 바꾼다.

### ArchUnit 규칙 (`HexagonalArchitectureTest`, 12개)

1. `domain`은 `java..`와 `common.domain..`(·도메인 패키지)만 의존한다 (Spring·JPA·MyBatis·Jackson 어노테이션 없음)
2. `application`은 어댑터·`common.config/web/mybatis`·JPA·Hibernate·MyBatis·Spring Data·Spring Web/HTTP·Servlet·Security·Jackson·Bean Validation·Anthropic SDK를 모른다
3. `application`이 쓰는 Spring은 `stereotype`(@Service)·`transaction.annotation`(@Transactional)뿐 (나머지는 java·slf4j·app.wearly)
4. `adapter.in`은 `port.out`·`application.service`·`adapter.out`을 부르지 않는다
5. `adapter.out`은 `application.service`·`adapter.in`을 부르지 않는다
6. 컨텍스트 A의 클래스가 컨텍스트 B를 쓰면 B의 `application.port.in..`만 (직접 의존 전부 검사)
7. `common.domain`은 컨텍스트를 모른다
8. `@Entity`는 `adapter.out.persistence`에만 / 9. `@Mapper`는 `adapter.out.query`에만 / 10. `@RestController`는 `adapter.in.web`에만 / 11. `@Service`는 `application.service`에만 / 12. `port.out` 구현은 `adapter.out`에만

규칙이 실제로 잡는지 확인하려고 도메인 클래스에 Spring 어노테이션·다른 컨텍스트 도메인 참조, 관리자 서비스에 다른 컨텍스트 JPA 엔티티 참조를 잠깐 넣어 돌렸고 1·2·6번이 실패하는 것을 본 뒤 되돌렸다.

### 1단계 테스트 결과

명령: `LC_ALL=C.UTF-8 ./gradlew test` (Testcontainers `pgvector/pgvector:pg17`, `dockerd`를 직접 띄움) → **112개 통과, 실패 0, 건너뜀 0**

- 기존 78개: 그대로 통과. 테스트 파일은 패키지를 옮기지 않았고 **import만** 바꿨다(12개 파일). 기대값은 하나도 바꾸지 않았다. 테스트가 쓰던 package-private 멤버(`ClaudeAiProvider.FALLBACK_BETA`, `ImageTypes.resolve`, `AiAnalysisService.sha256`, `AiProviderRegistry.parseMode`, `AiProviderFailureAnalyzer`, `RefreshTokenService.hash`)는 본 코드에서 public으로 열었다.
- 새 ArchUnit 12개 + Spring 없는 도메인 단위 테스트 22개: `CodeChecksTest`(4), `CodeAttrsTest`(3), `RefreshTokenTest`(4), `AiProviderSelectionTest`(5), `WardrobeDomainTest`(4), `UserProfileTest`(2).

## 2단계: AI 키 웹 입력 (쓰기 전용 · 암호화 저장)

- `V4__ai_provider_secret.sql`: `tb_ai_provider_secret`(`provider_id` PK·FK `ON DELETE CASCADE`, `ciphertext`·`nonce` BYTEA, `key_version` SMALLINT 기본 1, `last4`, `updated_by` FK `ON DELETE SET NULL`, `updated_at`, nonce 12바이트 CHECK).
- `ai.adapter.out.crypto.AesGcmSecretCipher`: AES-256-GCM, 저장마다 무작위 12바이트 nonce, 태그 128비트, AAD = `provider_id`(UTF-8), 마스터 키 `WEARLY_SECRET_KEY`(base64 32바이트). 없으면 꺼짐 + 시작 WARN, base64가 아니거나 32바이트가 아니면 꺼짐 + ERROR. 켜지면 INFO `AI 키 웹 입력: 켜짐`.
- 키 고르기(`ApiKeyRules.choose`, `ApiKeyService`): 마스터 키가 있고 DB 키가 복호화되면 DB → `api_key_env` 환경변수 → 없음. 복호화한 키는 메모리 캐시(공급자별), 넣기·지우기·공급자 삭제 때 바로 + 트랜잭션이 끝난 뒤 한 번 더 비운다(`TransactionHooksPort`).
- `PUT/DELETE /api/v1/admin/ai/providers/{id}/api-key`: 재확인(`AccountUseCase.passwordMatches`, BCrypt) → 키 검증 → `verify`(기본 true)면 `ApiKeyVerifier`가 각 사 모델 목록 조회 → AES-GCM 저장 → 변경 이력 `AI_PROVIDER_KEY_SET`·`AI_PROVIDER_KEY_DELETE`(before·after = `keySource`·`keyLast4`).
- 각 사 엔드포인트 확인(사람용 문서 대신 기계 판독용 공식 명세·SDK):
  - Claude: `claude-api` 스킬 + Anthropic Java SDK 2.73.0 바이트코드(`ModelService.list(ModelListParams)`, `ModelListParams.Builder.limit(long)`) → `client.models().list(limit=1)` = `GET /v1/models?limit=1`, `x-api-key`·`anthropic-version` 헤더(SDK가 붙임). 재시도 0.
  - OpenAI: `github.com/openai/openai-openapi`의 `openapi.yaml` — `GET /models`(`operationId: listModels`), 서버 `https://api.openai.com/v1`, `ApiKeyAuth: http bearer`.
  - Gemini: `generativelanguage.googleapis.com/$discovery/rest?version=v1beta` — `models.list` = `GET v1beta/models`(`pageSize`·`pageToken`), API 키. 키는 2차와 같이 `x-goog-api-key` 헤더(URL에 남지 않게).
- 공급자 행에 `keySource`·`keyLast4`·`keyUpdatedAt`·`keyUpdatedBy`·`secretStoreEnabled` 추가(`keyConfigured` = `keySource != NONE`).
- 오류 코드 추가: 403 `REAUTH_FAILED`, 409 `SECRET_KEY_MISSING`, 400 `API_KEY_INVALID`.

### 2단계 테스트 결과

명령: `LC_ALL=C.UTF-8 ./gradlew test` → **129개 전부 통과, 실패 0, 건너뜀 0** (1단계 112 + 17)

기존 테스트 기반 `IntegrationTestBase`에는 개발자 PC의 `WEARLY_SECRET_KEY`가 섞이지 않게 `ANTHROPIC_API_KEY=` 등과 같은 방식으로 `"WEARLY_SECRET_KEY="` 한 줄만 더했다(기대값 변경 없음).

| 테스트 | 개수 | 확인하는 것 |
|---|---|---|
| `AesGcmSecretCipherTest` (단위) | 5 | 암호화 왕복, 암호문 = 평문+16바이트·평문 없음, **nonce 매번 다름**, **AAD 다르면 실패**, **마스터 키 다르면 실패**, 암호문 변조·key_version 다르면 실패, 마스터 키 없음 WARN / 형식 오류 ERROR |
| `ApiKeyRulesTest` (단위) | 3 | 앞뒤 공백 제거·20~300자·공백 없음, **DB > ENV > NONE**, 마스터 키 없으면 DB 무시, 복호화 실패면 NONE, toString에 키 없음 |
| `ApiKeyServiceTest` (단위, 가짜 포트) | 4 | 출처 우선순위, 복호화 캐시(한 번만 읽음)·무효화, 마스터 키 없음 → ENV, 복호화 실패 → 쓸 수 없음 + ERROR 로그에 키 없음 |
| `ProviderApiKeyIntegrationTest` (통합, 마스터 키 있음) | 4 | **저장·교체·삭제**, ENV→DB→ENV·NONE→DB, DB 암호문·nonce·key_version, 시험 분석이 DB 키로 호출, **재확인 실패 403**(틀림·없음, PUT·DELETE), 일반 회원 403, 형식 400, **verify 실패 400**(가짜 OpenAI·Gemini·Claude 서버: 경로·쿼리·헤더 확인, 401·500·403), **공급자 삭제 시 비밀 행 삭제**(같은 ID 재생성 시 옛 키 안 살아남), **변경 이력 내용**(before·after 키가 keySource·keyLast4뿐), **서버 출력 캡처에 키 문자열 0건**·응답·이력에도 0건 |
| `ProviderApiKeyNoMasterKeyIntegrationTest` (통합) | 1 | **마스터 키 없음 409**(비밀번호 확인보다 먼저), `secretStoreEnabled: false`, 출처 ENV/NONE |

### 실제 기동 확인 (`bootRun` + curl, `docker compose`의 `wearly-postgres`)

1. `SPRING_FLYWAY_TARGET=3`로 V1~V3만 적용(2차 DB 상태). 이 상태에서는 JPA validate가 새 엔티티 테이블이 없어 기동을 멈춘다(예상대로).
2. `WEARLY_SECRET_KEY=$(openssl rand -base64 32)` + 관리자 계정으로 `./gradlew bootRun` → `Migrating schema "public" to version "4 - ai provider secret"`, `AI 키 웹 입력: 켜짐`, 관리자 생성.
3. curl: 목록 `keySource: NONE`·`secretStoreEnabled: true` → 비밀번호 틀림 403 `REAUTH_FAILED` → 가짜 키 `verify:false` 저장 200(`DB`·`keyLast4`·`keyUpdatedBy: admin@wearly.local`) → `verify` 기본(true)로 다른 가짜 키 → 400 `API_KEY_INVALID`(이 환경은 `api.openai.com`이 프록시에서 막혀 "Tunnel failed, got: 403"; 저장 안 됨) → DB 행(암호문 53바이트 = 37+16, nonce 12, key_version 1) → 삭제 200 `NONE` → 변경 이력 KEY_SET·KEY_DELETE(before/after에 keySource·keyLast4만).
4. 재기동 `OPENAI_API_KEY`(가짜) 추가: `ENV` → 웹 키 저장 `DB` → 삭제 `ENV`.
5. 마스터 키 없이 재기동(DB 키 남아 있음): WARN, 목록 `ENV`·`secretStoreEnabled: false`, 저장 409 `SECRET_KEY_MISSING`.
6. `WEARLY_SECRET_KEY='not-base64!!'`: ERROR 후 정상 기동(꺼짐).
7. 다른 마스터 키로 재기동: 시작 시 `AI 공급자 openai의 웹 입력 키를 복호화하지 못해…` ERROR, 목록 `NONE`(환경변수 키로 몰래 바꾸지 않음).
8. **서버 로그 6회분 전체에서 가짜 키 문자열(웹 키·환경변수 키) `grep -c` = 0**, 변경 이력 JSON에도 0건.

## 실행하지 못한 검증

- **실제 Claude·OpenAI·Gemini 키로 verify**: 키가 없고 이 환경의 네트워크 정책이 각 사 API를 막는다(위 3번에서 프록시 403). 세 공급자 모두 가짜 HTTP 서버로 경로·쿼리·헤더·오류 처리만 확인했다. 특히 Claude의 실제 `GET /v1/models` 응답 파싱은 SDK에 맡겼다(가짜 서버는 최소 필드만 줬다).
- **연동 하네스 29개**(08 문서 4장): `harness/`는 앱 쪽 실행 환경(Node·앱 API 모듈)이 필요해 이 세션에서 돌리지 않았다. API 경로·요청·응답·오류 코드는 바꾸지 않았고 기존 통합 테스트 78개로 확인했다.
- **관리자 사이트 키 입력 화면과의 연동**(로컬에서 동시 작업 중), 사용자 Mac에서의 Testcontainers.
- **여러 서버 인스턴스**: 코드 캐시와 같이 복호화 키 캐시도 인스턴스 안에서만 무효화된다(단일 서버 전제).

## 계약과 다르게(또는 빈틈을 채워) 정한 점

### 1단계 (08 문서)

1. **코드 스냅샷 위치**: `CodeSnapshot`·`CodeEntry`·`CodeGroupEntry`·`CodeGroups`·`CodeChecks`는 옷장·프로필·AI·관리자가 모두 읽는 값이라 `code.domain`이 아니라 **`common.domain.code`**에 뒀다("컨텍스트 사이는 port.in만" 규칙을 지키면서 쓰려고). 스냅샷은 code의 입력 포트 `CodeCatalog`로 얻는다. 코드 테이블 쓰기 규칙(`CodeAttrs`·`CodeRules`)은 `code.domain`.
2. **관리자 API 위치**: `/admin/**` 컨트롤러는 모두 admin 컨텍스트에 두고, admin 서비스가 code·ai·user·auth 입력 포트를 부른다. 변경 이력은 admin의 `RecordAuditPort` 하나로 남기고 다른 컨텍스트는 이력을 모른다(컨텍스트 사이 순환 없음).
3. **사진 분석 컨트롤러**: `POST /wardrobe/items/analyze`는 ai 컨텍스트의 `AiAnalyzeController`(경로 그대로).
4. **관리자 읽기 모델**: 대시보드·회원 목록·AI 기록·변경 이력 조회 SQL(`AdminQueryMapper.xml`)은 여러 테이블을 읽지만 admin 소유의 읽기 전용 모델로 남겼다. 코드 사용 수(`CodeQueryMapper`)도 옷장 테이블을 읽는 그대로다.
5. **자격 증명 ↔ 회원 관계**: `LocalCredentialEntity`의 `@OneToOne User`를 `user_id` 값 컬럼으로 바꿨다(다른 컨텍스트 엔티티를 참조하지 않으려고). DB 스키마는 같다.
6. **조회 결과 행**: admin의 MyBatis 결과 클래스(`AdminRows`)는 `port.out`에 두고 MyBatis가 바로 채운다. 그 밖의 행 클래스(`ItemRow`·`CodeRow` 등)는 조회 어댑터 안에서 도메인 값으로 바꾼다.
7. **API 모양의 결과**: ID 문자열·ISO 시각처럼 응답 모양을 그대로 가진 결과 record(`ItemView`, `AdminViews.*` 등)는 `port.in`에 두고 컨트롤러가 그대로 돌려준다(어노테이션이 필요한 `GET /admin/ai/types`의 options와 요청 DTO만 웹 어댑터에 따로 둠).
8. **오류 코드**: `ErrorCode`가 Spring `HttpStatus` 대신 상태 번호(int)를 갖는다(유스케이스가 Spring Web 타입을 모르게). 응답은 같다.
9. **시작 시 실행**: `AdminBootstrap`(ApplicationRunner)·AI 공급자 상태 로그는 `adapter.in.startup`(입력 어댑터)에서 입력 포트를 부른다. 코드 캐시 워밍업은 캐시를 가진 조회 어댑터가 한다.
10. **startup 로그 한 줄 변화**: `AI 공급자 (시도 순서)` 항목에 `key=ENV|DB`를 더했다.

### 2단계 (07 문서 6장)

11. **확인 순서**: 404(공급자) → 409 `SECRET_KEY_MISSING` → 403 `REAUTH_FAILED` → 400 `VALIDATION_FAILED`(apiKey) → 400 `API_KEY_INVALID`. 마스터 키가 없으면 비밀번호를 맞게 넣어도 저장할 수 없으니 먼저 알린다. `currentPassword`가 없거나 비어도 403(400이 아님).
12. **DELETE는 마스터 키가 없어도 된다**(남은 웹 키 정리용, 재확인은 함). 지울 웹 키가 없으면 200 + 공급자 행을 주고 **변경 이력은 남기지 않는다**.
13. **복호화 실패**: 웹 키가 있는데 복호화되지 않으면 환경변수 키로 넘어가지 않고 `keySource: NONE`(쓸 수 없음) + ERROR 로그. 시험 분석은 `KEY_MISSING`("웹에서 넣은 키를 복호화하지 못했어요 …"). 마스터 키 자체가 없을 때만 환경변수 키를 쓴다(계약 "환경변수 키는 그대로 쓴다").
14. **API_KEY_INVALID 메시지**: `"API 키를 확인하지 못했어요: " + 이유`. 401·403이면 공급자 원문을 버리고 `"<공급자>가 키를 받아들이지 않았어요 (HTTP 401)."`, 그 밖은 키·키 모양 문자열을 지운 원문(300자까지). 연결 실패·시간 초과도 400 `API_KEY_INVALID`.
15. **verify는 공급자 행의 `options.timeoutSeconds`·`baseUrl`**(`WEARLY_AI_ALLOW_BASE_URL=true`일 때만)을 따른다. Gemini는 `pageSize=1`, Claude는 `limit=1`로 가볍게 부른다.
16. **트랜잭션**: 키 저장과 변경 이력은 한 트랜잭션이다. verify(외부 호출)도 그 안에서 한다(관리자 전용·드묾, 최대 공급자 제한 시간만큼 커넥션을 잡음).
17. **변경 이력**: 기존 `AI_PROVIDER_CREATE/UPDATE/DELETE` 이력의 before·after(공급자 행 모양)에도 새 필드(`keySource`·`keyLast4`·`keyUpdatedAt`·`keyUpdatedBy`·`secretStoreEnabled`)가 같이 들어간다. 키 값은 없다.
18. **2차 README의 "키 일부도 응답에 안 나옴"** 원칙은 07 문서 6장에 따라 `keyLast4`(웹 키 끝 4자)만 예외가 됐다. 환경변수 키는 끝 4자도 주지 않는다.
19. **key_version**: 지금 1만 받는다(다른 값이면 복호화 실패로 처리). 마스터 키 교체 기능은 없다.
20. **updated_at**: 같은 키를 다시 넣어도 nonce가 달라 행이 바뀌고 `updated_at`이 올라간다.

## 관리자 사이트가 알아야 할 응답 형식

공통: ID는 문자열, 시각은 밀리초 ISO-8601 UTC, 오류는 `{ status, code, message, fieldErrors }`.

```ts
// 공급자 행 (GET /admin/ai/providers 항목, POST·PATCH 응답, 아래 키 API 응답)
ProviderDto = {
  providerId: string, type: 'CLAUDE' | 'OPENAI' | 'GEMINI', displayName: string, model: string | null,
  apiKeyEnv: string, keyConfigured: boolean, enabled: boolean, priority: number,
  options: Record<string, unknown>, usable: boolean, today: { success: number, failed: number },
  // 3차
  keySource: 'DB' | 'ENV' | 'NONE',
  keyLast4: string | null,          // DB 키일 때만
  keyUpdatedAt: string | null,      // DB 키일 때만
  keyUpdatedBy: string | null,      // DB 키를 넣은 관리자 이메일 (탈퇴했으면 null)
  secretStoreEnabled: boolean,      // false면 입력 버튼을 막고 안내
}

PUT    /api/v1/admin/ai/providers/{id}/api-key  { apiKey: string, currentPassword: string, verify?: boolean /* 기본 true */ }
       → 200 ProviderDto
       | 404 NOT_FOUND | 409 SECRET_KEY_MISSING | 403 REAUTH_FAILED
       | 400 VALIDATION_FAILED (fieldErrors[].field === 'apiKey') | 400 API_KEY_INVALID (message에 이유)
DELETE /api/v1/admin/ai/providers/{id}/api-key  { currentPassword: string }   // DELETE에 JSON 본문
       → 200 ProviderDto (keySource가 ENV 또는 NONE으로) | 404 | 403 REAUTH_FAILED

// 변경 이력 (GET /admin/audit-logs?targetType=AI_PROVIDER)
action: 'AI_PROVIDER_KEY_SET' | 'AI_PROVIDER_KEY_DELETE'
before / after: { keySource: 'DB' | 'ENV' | 'NONE', keyLast4: string | null }
```

- `keyConfigured`는 `keySource !== 'NONE'`과 같다. 화면은 `keySource`로 "웹 입력 ••••abcd · 날짜 · 관리자 / 환경변수 `apiKeyEnv` / 없음"을 그리면 된다.
- 403이 두 가지다: 관리자가 아니면 `FORBIDDEN`, 비밀번호 재확인 실패면 `REAUTH_FAILED`(로그아웃시키지 말 것).
- `verify: true` 저장은 공급자 호출 시간만큼(최대 `options.timeoutSeconds`, 기본 60초) 걸릴 수 있다.

## 로컬에서 보강이 필요한 점

1. **`WEARLY_SECRET_KEY` 만들기**: `openssl rand -base64 32` 값을 `backend/.env`에 넣고 다시 시작(`.env.example` 참고). 이 값을 잃거나 바꾸면 넣어 둔 웹 키는 복호화되지 않으니 다시 넣어야 한다. 운영에서는 비밀 관리 도구에 둔다.
2. **실제 키로 verify**: 관리자 사이트에서 세 공급자 키를 `verify` 켠 채로 넣어 200이 나오는지, 틀린 키로 400 `API_KEY_INVALID`가 나오는지 확인. 그 뒤 시험 분석으로 DB 키가 쓰이는지(`keySource: DB`) 확인.
3. **하네스 29개**: 구조만 바꿨으므로 `harness/`를 한 번 돌려 API 동작이 그대로인지 확인.
4. **관리자 사이트 연동**: 위 응답 형식으로 키 입력 화면을 붙이고, 키를 브라우저 저장소·로그·쿼리 문자열에 남기지 않는지, 저장 뒤 입력칸을 비우는지 확인.
5. Mac에서 `./gradlew test` 한 번 (Docker Desktop + Testcontainers, 129개).
6. **운영 준비**(범위 밖): 여러 인스턴스일 때 코드·키 캐시 무효화, 마스터 키 교체(`key_version` 2 + 재암호화), 관리자 2단계 인증.
