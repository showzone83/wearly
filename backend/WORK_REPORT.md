# 백엔드 2차 작업 보고 (2026-10-17)

대상 계약: [06_코드_관리.md](../doc/06_코드_관리.md) + [06_코드_초기값.json](../doc/06_코드_초기값.json), [07_관리자_사이트.md](../doc/07_관리자_사이트.md). 계약 문서(06·07)는 고치지 않았다.

## 브랜치

- `feat/backend-admin` (기준: `origin/dev`, 계약 문서 커밋 `50f4837` 위). 이 이름으로 푸시됐다. PR은 만들지 않았다.
- 바뀐 곳: `backend/` 전부, `doc/05_백엔드_API.md`(06·07 문서를 가리키게 관련 부분만). `mobile/`·`harness/`·`admin/`·`doc/06_*`·`doc/07_*`는 건드리지 않았다.

## 버전

1차와 같다. 새 라이브러리는 추가하지 않았다 (OpenAI·Gemini는 SDK 없이 Spring `RestClient` + JDK `HttpClient`).

| 항목 | 버전 |
|---|---|
| Spring Boot | 4.1.1 (Hibernate 7.4.5, Flyway 12.4.0, Jackson 3.1.5, Testcontainers 2.0.5) |
| mybatis-spring-boot-starter | 4.1.0 |
| Anthropic Java SDK | 2.73.0 |
| Java / Gradle | 21 / 9.8.1 |

## 마이그레이션

| 파일 | 내용 |
|---|---|
| `V2__codes.sql` | `tb_code_group`·`tb_code`(계약 2장 그대로) + 초기값 7그룹 102코드를 JSON 그대로 INSERT (JSON에서 생성). `tb_wardrobe_item`의 category·sub_type·color·pattern·seasons·styles·status CHECK와 `tb_user_profile`의 colors·moods CHECK 삭제. 코드 컬럼 길이를 `VARCHAR(30)`으로 넓힘. `tb_outfit_item`·`tb_daily_look_item`의 item_role CHECK에 `DRESS` 추가. `tb_user_style.style_code` CHECK도 삭제(STYLE 코드라서) |
| `V3__admin_ai.sql` | `tb_user.role`(`USER`·`ADMIN`, 기본 `USER`), `tb_ai_provider`(초기값 claude 사용·openai·gemini 꺼짐, CHECK: type은 CLAUDE·OPENAI·GEMINI, enabled면 model 필수), `tb_ai_analysis_log`의 provider CHECK 삭제·`VARCHAR(30)`·feature에 `ADMIN_TEST` 추가·한도 부분 인덱스를 `ITEM_ANALYZE`로 좁힘, `tb_admin_audit_log`(관리자 탈퇴 시 admin_user_id는 NULL), 조회용 인덱스 |

V1은 고치지 않았다. 데이터 변환은 없다 (1차 값은 모두 새 코드에 있다).

## 테스트 결과

명령: `LC_ALL=C.UTF-8 ./gradlew test` (Testcontainers, `dockerd`를 직접 띄움) → **78개 전부 통과, 실패 0, 건너뜀 0**

| 테스트 클래스 | 개수 | | 테스트 클래스 | 개수 |
|---|---|---|---|---|
| `AuthIntegrationTest` | 15 | | `AiProviderFallbackIntegrationTest` | 4 |
| `ShortAccessTtlIntegrationTest` | 1 | | `AiAnalyzeIntegrationTest` | 4 |
| `ProfileIntegrationTest` | 3 | | `AiProviderModeTest` | 3 |
| `WardrobeIntegrationTest` | 8 | | `ClaudeAiProviderTest` | 7 |
| `CodeIntegrationTest` | 6 | | `OpenAiProviderTest` | 5 |
| `AdminCodeIntegrationTest` | 6 | | `GeminiAiProviderTest` | 4 |
| `AdminAiProviderIntegrationTest` | 3 | | `MockAiProviderTest` | 3 |
| `AdminUserIntegrationTest` | 5 | | `ImageTypesTest` | 1 |

요청한 항목: `/codes` 형식·ETag 304 · 새 코드(JEANS·DRESS·denim·FLORAL·SPORTY…)로 등록 · 모르는 코드/비활성 코드/parent 불일치 400 · PATCH에서 안 바꾼 비활성 필드 통과 · 관리자 권한 403 · 코드 CRUD·순서·사용 중 삭제 409·pairs 양방향·캐시 갱신(version 변경) · AI 공급자 CRUD·`keyConfigured` · 우선순위 순 시도와 실패 시 다음 공급자(가짜 OpenAI·Gemini 서버) · 전부 불가 502 · 시험 분석이 한도에 안 셈 · OpenAI·Gemini 어댑터 요청/응답 단위 테스트 · 회원 정지 시 로그인 403·토큰 401 · 감사 로그 · 시작 시 관리자 계정 생성 — 모두 들어 있다.

1차 테스트 중 바뀐 것: `AiFailureIntegrationTest`(공급자 빈을 Mockito로 바꾸던 방식)와 `AiProviderConfigTest`(키 없으면 시작 실패)는 새 규칙과 맞지 않아 지우고 `AiProviderFallbackIntegrationTest`·`AiProviderModeTest`로 바꿨다. 1차 테스트의 "허용 목록 밖 색" 예시 `pink`는 이제 정식 코드라 `fuchsia`로 바꿨다. 통계 기대값에 `DRESS` 키가 들어갔다.

### 실제 기동 확인 (`bootRun` + curl)

`docker compose up -d`의 `wearly-postgres`에서:

1. `SPRING_FLYWAY_TARGET=1`로 V1만 적용하고 1차 모양 데이터(회원·프로필 colors `navy,burgundy`·아이템 `TOP/KNIT/navy/STRIPE/LAUNDRY`·AI 기록 provider `claude`)를 넣음
2. `WEARLY_ADMIN_EMAIL/PASSWORD`, 가짜 `OPENAI_API_KEY`로 `./gradlew bootRun` → **V2·V3가 기존 데이터 위에 적용**, `관리자 계정: … 새로 만듦`, 키가 없어 `쓸 수 있는 AI 공급자가 없습니다` WARN 후 정상 기동, `코드 캐시: 7개 그룹, 102개 코드`
3. curl: `GET /codes` 200 + `ETag`·`Cache-Control: no-cache`, 같은 ETag로 304 · 관리자 로그인 `role: ADMIN` · 대시보드(1차 기록 포함 집계) · 공급자 목록(`openai`만 `keyConfigured: true`) · openai에 모델 넣고 켜기 → `usable: true` · 회원 목록·코드 사용 수(1차 아이템 반영) · PATTERN 코드 추가 201 → 예전 ETag로 200(버전 바뀜) · 일반 회원 사진 분석 → openai 호출 실패 → 502 + 실패 기록 · 변경 이력 3건 · 일반 회원의 관리자 API 403 · 서버 로그에 키 문자열 0건

## 실행하지 못한 검증

- **실제 Claude·OpenAI·Gemini 호출**: 키가 없고, 이 환경의 네트워크 정책이 `api.openai.com` 등을 막는다(위 3번에서 프록시 403 확인). 세 공급자 모두 가짜 HTTP 서버로 요청 JSON·응답 파싱·오류 처리만 확인했다.
- **공식 문서 확인 방법**: `platform.openai.com`·`developers.openai.com`·`ai.google.dev`가 이 환경에서 막혀(DNS 실패·프록시 403) 사람용 문서 페이지는 열지 못했다. 대신 **각 사가 공개한 기계 판독용 공식 명세**로 확인했다.
  - OpenAI: `github.com/openai/openai-openapi`의 `openapi.yaml`(OpenAPI 3.1, v2.3.0) — `POST /responses`의 `CreateResponse`·`InputImageContent`(`image_url` data URL, `detail` 필수: low·high·auto·original)·`TextResponseFormatJsonSchema`(`text.format` = type·name·schema·strict)·`Response`(status, incomplete_details.reason, output[].content[] output_text/refusal)·`ResponseUsage`(input_tokens·output_tokens)·`ErrorResponse`(error.message), 인증 `Bearer`, 서버 `https://api.openai.com/v1`
  - Gemini: `generativelanguage.googleapis.com/$discovery/rest?version=v1beta` — `models.generateContent`(`v1beta/{model}:generateContent`), `Part.inlineData{mimeType,data}`, `GenerationConfig.responseFormat.text{mimeType: APPLICATION_JSON, schema}`(이 명세에서 `responseSchema`는 "Deprecated. Use response_format"), `responseMimeType`+`responseJsonSchema`(대안), `maxOutputTokens`·`temperature`, `Candidate.finishReason`·`PromptFeedback.blockReason`, `UsageMetadata.promptTokenCount`·`candidatesTokenCount`·`thoughtsTokenCount`, `modelVersion`
- **앱·하네스·관리자 사이트와의 연동**: 이 브랜치(dev 기준)에는 바뀐 앱·관리자 사이트가 없어 돌려 보지 못했다. 계약 문서만 보고 맞췄다.
- **여러 서버 인스턴스**: 코드 캐시는 인스턴스 안에서만 갱신된다 (단일 서버 전제).
- **사용자 Mac 환경**에서의 Testcontainers 실행.

## 계약과 다르게(또는 빈틈을 채워) 정한 점

### 공급자 선택·호출 (07 문서 3장)

1. **`WEARLY_AI_PROVIDER` 값**: `db`(새 기본)·`mock`. 1차 기본값 `claude`는 `db`와 같은 뜻으로 받는다(기존 `.env` 호환). `auto`·그 밖의 값은 1차처럼 시작 실패(안내는 `AiProviderFailureAnalyzer`).
2. **다음 공급자로 넘어가는 실패**: 계약 예시(시간 초과·5xx·응답 검증 실패)에 더해 **4xx(키 오류·요청 거부)·연결 실패·거절(refusal)·잘림까지 모두** 다음으로 넘어간다. 공급자 하나의 설정 실수로 분석 전체가 멈추지 않게 하려는 것이다.
3. **쓸 수 있는 공급자가 없을 때 502**는 호출이 없으므로 `tb_ai_analysis_log`에 행을 남기지 않는다 (서버 WARN 로그만).
4. **시험 분석**: 목업 실행이어도 DB 행의 실제 공급자를 부른다 (공급자 비교가 목적). 모델이 없거나 키 환경변수가 비면 호출·기록 없이 `{ ok:false, errorCode: "MODEL_MISSING" | "KEY_MISSING" }`.
5. **키 환경변수 이름 제한**: `api_key_env`는 `^[A-Z][A-Z0-9_]*_API_KEY$`만 허용한다. 관리자가 `SPRING_DATASOURCE_PASSWORD`·`WEARLY_JWT_SECRET` 같은 서버 비밀을 공급자에게 보내게 만드는 것을 막는다.
6. **`options.baseUrl`**은 `WEARLY_AI_ALLOW_BASE_URL=true`일 때만 받는다(기본 false, 테스트는 true). 키를 다른 주소로 보내는 것을 막는다.
7. **공급자 ID**: 영문 소문자로 시작하는 소문자·숫자·`_`·`-` 1~30자. `mock`은 목업 응답(`provider: "mock"`)과 겹쳐 예약. 중복은 409 **`PROVIDER_EXISTS`**(계약에 없는 코드). `type: MOCK`은 400.
8. **options 규칙** (`GET /admin/ai/types`로 화면에 준다): CLAUDE `effort`(low~max, 기본 medium)·`maxTokens`(기본 16000)·`timeoutSeconds`·`baseUrl` / OPENAI `temperature`(보낼 때만)·`maxTokens`(max_output_tokens, 기본 16000)·`detail`(기본 auto)·`timeoutSeconds`·`baseUrl` / GEMINI `temperature`·`maxTokens`(maxOutputTokens, 기본 8192)·`schemaField`(`responseFormat` 기본 | `responseJsonSchema`)·`timeoutSeconds`·`baseUrl`. 모르는 키는 400(`options.키`).
9. **OpenAI 구현 세부**: Responses API, `store: false`(응답 보관 안 함), 프롬프트와 이미지(data URL)를 user 메시지 하나로, `text.format`은 `strict: true`. 미디어 형식 사전 검사는 하지 않는다(HEIC는 API가 거부하면 다음 공급자로).
10. **Gemini 구현 세부**: 키는 URL `key=` 쿼리 대신 `x-goog-api-key` 헤더로 보낸다(URL이 로그·오류 메시지에 남는 것을 피함). 출력 토큰은 `candidatesTokenCount + thoughtsTokenCount`(생각 토큰도 출력 과금이라서). `thought: true` 파트는 결과 텍스트에서 뺀다. 모델은 `gemini-x`·`models/gemini-x` 둘 다 받는다.
11. **Claude 구현 세부**: 1차 그대로(SDK, `output_config.format`·effort·서버 측 폴백). 다음 공급자로 넘어가므로 SDK 재시도를 2→1회로 줄였다. 모델·effort·max_tokens·제한 시간은 공급자 행에서 읽는다(1차의 `WEARLY_AI_MODEL`·`WEARLY_AI_EFFORT` 환경변수는 없앴다).
12. **키 비노출**: 공급자 오류가 401·403이면 원문 메시지를 버린다(OpenAI는 가린 키 조각을 넣는다). 그 밖의 메시지도 키 값과 키 모양 문자열(`sk-…`, `AIza…`, `Bearer …`)을 지운다.
13. **`today`·`aiToday` 집계 기준**: 공급자 목록 `today`와 대시보드 `aiToday`는 모든 기능(사진 분석 + 시험 분석)을 센다(비용·장애 확인용). 회원 목록 `aiToday`는 한도와 같은 기준(오늘 성공한 사진 분석)이다. `aiByProvider7d`·`newUsers7d`는 지금부터 7일 전까지(롤링).

### 코드 (06 문서, 07 문서 4장)

14. **코드 형식**: COLOR는 `^[a-z0-9_]{1,30}$`, 그 밖은 `^[A-Z0-9_]{1,30}$` (계약 "영문 대문자·숫자·밑줄(COLOR는 영문 소문자)").
15. **attrs 검증**: 그룹에 없는 키는 400(`attrs.키`). 필수: CATEGORY `slot`(없으면 null), SUB_TYPE `volume`·`warmth`·`shape`, COLOR `hex`·`neutral`·`light`(`pairs`는 없으면 []), ITEM_STATUS `recommendable`. PATTERN·SEASON·STYLE은 빈 객체만. 저장할 때 `hex`는 대문자로, `pairs`는 중복 제거·알파벳순·자기 자신 금지. 정수 칸에 1.5 같은 실수는 400.
16. **parent**: SUB_TYPE만 가질 수 있고 필수(있는 CATEGORY 코드, 비활성이어도 됨). 다른 그룹에 parent를 보내면 400.
17. **삭제 409 `CODE_IN_USE`**: 쓰는 아이템, 쓰는 프로필(COLOR→colors, STYLE→moods), CATEGORY는 하위 SUB_TYPE이 있을 때도. 메시지에 이유와 개수("아이템 2개…")를 넣는다. COLOR를 지우면 다른 색의 `pairs`에서도 뺀다.
18. **순서 바꾸기**: 그룹의 코드 전부를 한 번씩 보내야 한다(빠짐·중복·모르는 코드는 400). 응답은 `GET /admin/codes`와 같은 전체 모양.
19. **PATCH**: 보낸 필드만 바꾸고 `attrs`는 통째로 바꾼다. `code`를 다른 값으로 보내면 400. 실제로 바뀐 것이 없으면 `version`도 그대로다.
20. **version** = `tb_code`의 `max(updated_at)`(UTC 밀리초 ISO) + `#` + 코드 수. 순서 바꾸기·pairs 반영은 내용이 같아도 `updated_at`을 올린다. 그룹 테이블은 API로 바꾸지 않으므로 넣지 않았다.
21. **`/codes` 응답**: 그룹 `description`은 관리자 API에만 넣었다(공개 응답은 계약 예시 모양 그대로). `Cache-Control: no-cache`(캐시는 하되 매번 ETag로 확인). 만료된 Authorization 헤더가 붙어 와도 무시한다(공개 경로). CORS에 `If-None-Match` 요청 헤더와 `ETag` 노출을 더했다.
22. **"새로 고르는 값" 판단**: 등록·bulk는 모든 값이 새 값. PATCH는 저장된 값과 같으면 비활성이어도 통과(보내지 않은 필드도, 같은 값을 다시 보낸 필드도). 배열(seasons·styles)은 원소마다 저장된 배열에 있었는지로 본다. 프로필 PUT도 저장돼 있던 colors·moods는 비활성이어도 둔다. 비활성은 `active=false`이거나, SUB_TYPE의 상위 CATEGORY가 비활성인 경우다. 오류 메시지: 모르는 값 "허용되지 않는 값이에요.", 비활성 "지금은 고를 수 없는 값이에요.", 짝 "카테고리와 맞지 않는 세부 종류예요."
23. **목록 필터**(`category`·`status`·`color`)는 저장된 값을 찾는 것이라 비활성 코드도 받는다. 모르는 값만 400.
24. **AI 프롬프트·스키마·검증·목업**은 "고를 수 있는" 코드(위 22번 기준)만 쓴다.

### 관리자·회원 (07 문서 2·4장)

25. **권한 판단은 매 요청 DB 역할**로 한다. JWT `role` 클레임은 화면 표시용. 그래서 역할을 내리거나 올리면 기존 토큰에도 바로 반영된다.
26. **정지**: 로그인은 비밀번호가 맞을 때만 403 `ACCOUNT_SUSPENDED`(틀리면 401 `INVALID_CREDENTIALS`, 정지 여부를 알리지 않음). 정지할 때 그 회원의 Refresh 토큰을 모두 폐기한다. 자기 자신 정지도 400(자기 역할 내리기와 같은 이유).
27. **시작 시 관리자**: 이미 있는 계정은 역할만 올리고 비밀번호·이름은 그대로. 이름은 20자로 자른다. 만들기·승격도 변경 이력에 `ADMIN_BOOTSTRAP_CREATE`·`ADMIN_BOOTSTRAP_PROMOTE`(targetType `USER`)로 남긴다. 탈퇴(정지 아님)는 1차처럼 행 삭제.
28. **AI 기록 검색**: `from`·`to`는 `YYYY-MM-DD`(한국 시간, 둘 다 그날 포함) 또는 ISO-8601 시각(from 포함, to 미포함). `userEmail`은 부분 일치(대소문자 무시). 계약에 없는 `feature`(`ITEM_ANALYZE`·`ADMIN_TEST`) 필터를 더했다. 목록 행의 결과 요약은 `"이름 · 종류/세부 종류 · 색"`.
29. **변경 이력 action 이름**: `CODE_CREATE`·`CODE_UPDATE`·`CODE_REORDER`(targetId = 그룹)·`CODE_DELETE`, `AI_PROVIDER_CREATE`·`_UPDATE`·`_DELETE`, `USER_UPDATE`. 코드의 targetId는 `GROUP:CODE`. 변경 이력은 변경과 같은 트랜잭션에 쓴다.
30. **목록 페이지**: `page`<0, `size`가 1~100 밖이면 400.

## 앱·관리자 사이트가 알아야 할 응답 형식

공통: ID는 문자열, 시각은 밀리초 ISO-8601 UTC, 오류는 `{ status, code, message, fieldErrors }`.

```ts
// 공개
GET /api/v1/codes → 200 (ETag) | 304
{ version: string, groups: { group: string, name: string,
    codes: { code: string, label: string, parent: string | null, sortOrder: number, active: boolean,
             attrs: Record<string, unknown> }[] }[] }      // attrs 키 순서는 보장하지 않음(JSONB)

// 바뀐 것
UserDto = { id, email, name, role: 'USER' | 'ADMIN', createdAt }
AnalyzeResponse.provider = provider_id 문자열 ("claude" | "openai" | "gemini" | "mock" | 관리자가 만든 ID)
WardrobeStats.byCategory / byStatus = 코드 순서의 객체 (활성 코드 전부 + 아이템이 있는 비활성 코드)
오류 추가: 403 ACCOUNT_SUSPENDED, 409 CODE_EXISTS / CODE_IN_USE / PROVIDER_EXISTS

// 관리자 (ADMIN 전용, 아니면 403 FORBIDDEN)
Page<T> = { items: T[], page: number, size: number, total: number }
GET  /admin/dashboard → { users, newUsers7d, items, aiToday: { total, success, failed },
                          aiByProvider7d: { providerId, success, failed, avgLatencyMs: number | null, inputTokens, outputTokens }[] }
GET  /admin/codes → { version, groups: { group, name, description, codes: (CodeDto & { usageCount })[] }[] }
POST /admin/codes/{group} { code, label, parent?, sortOrder?, active?, attrs } → 201 CodeDto & { usageCount }
PATCH /admin/codes/{group}/{code} → CodeDto & { usageCount }
PUT  /admin/codes/{group}/order { codes } → GET /admin/codes와 같은 모양
DELETE /admin/codes/{group}/{code} → 204 | 409 CODE_IN_USE
GET  /admin/ai/providers → { providerId, type, displayName, model: string | null, apiKeyEnv, keyConfigured,
                             enabled, priority, options, usable, today: { success, failed } }[]
POST /admin/ai/providers → 201 ProviderDto, PATCH /admin/ai/providers/{id} → ProviderDto, DELETE → 204
POST /admin/ai/providers/{id}/test (multipart image) → 200 { ok, suggestion: AiSuggestion | null,
       errorCode: string | null, errorMessage: string | null, latencyMs, inputTokens: number | null,
       outputTokens: number | null, model: string | null }      // 실패해도 HTTP 200, ok=false
GET  /admin/ai/types → { types: { type, description, options: { name, kind: 'string'|'integer'|'number'|'enum'|'url',
       description, defaultValue?, min?, max?, values? }[] }[], baseUrlAllowed: boolean, mockMode: boolean }
GET  /admin/ai/logs?providerId&status&userEmail&feature&from&to&page&size → Page<{ id, createdAt, userId, userEmail,
       feature, providerId, model, status, latencyMs, inputTokens, outputTokens, imageBytes, resultSummary, errorCode }>
GET  /admin/ai/logs/{id} → 위 + { imageSha256, result }
GET  /admin/users?q&status&role&page&size → Page<{ id, email, name, role, status, createdAt, itemCount, aiToday }>
PATCH /admin/users/{id} { status?, role? } → 같은 행 모양
GET  /admin/audit-logs?targetType&page&size → Page<{ id, adminUserId, adminEmail, action, targetType, targetId,
       before, after, createdAt }>
```

- 관리자 사이트는 `/auth/login` 응답의 `user.role === 'ADMIN'`으로 들어갈지 정한다. 시험 분석은 실패해도 200이니 `ok`를 본다.
- `GET /admin/ai/types`의 `baseUrlAllowed`가 false면 `baseUrl` 입력란을 숨기는 것을 권한다(보내면 400).

## 로컬에서 보강이 필요한 점

1. **실제 키로 세 공급자 확인**: `.env`에 키를 넣고 관리자 사이트의 시험 분석으로 같은 사진을 돌려 결과·지연·토큰을 비교. 특히
   - OpenAI: 고른 모델이 `text.format` json_schema(strict)·`input_image`를 받는지, 추론 모델이면 `temperature`를 빼 둘 것(기본은 안 보냄)
   - Gemini: 고른 모델이 `generationConfig.responseFormat`을 받는지. 거부하면 공급자 options에 `"schemaField": "responseJsonSchema"`
   - Claude: 1차 보고 그대로(서버 측 폴백 베타가 이 계정에서 받아들여지는지)
2. **앱 연동**: 앱이 `GET /codes`로 코드를 받고 문자열 코드로 바꾼 뒤 하네스 시나리오를 다시 돌린다. 앱의 `provider: 'mock' | 'claude'` 타입을 `string`으로, `UserDto.role` 추가, 통계 키에 `DRESS` 등 새 키.
3. **관리자 사이트 연동**: 위 응답 형식으로 화면을 붙이고 CORS(`http://localhost:5173`)·토큰 갱신을 확인.
4. **HEIC**: Claude는 여전히 HEIC를 받지 않는다(그 공급자만 실패하고 다음 공급자로). 앱에서 JPEG로 바꿔 올리는 것을 권장(1차와 같음).
5. **운영 준비**(범위 밖): 여러 인스턴스일 때 코드 캐시 무효화(Redis pub/sub 등), AI 한도 동시성, 관리자 2단계 인증·IP 제한, `WEARLY_AI_ALLOW_BASE_URL=false` 유지.
6. Mac에서 `./gradlew test` 한 번 (Docker Desktop + Testcontainers).
