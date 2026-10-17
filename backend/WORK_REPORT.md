# 백엔드 2차 작업 보고 (2026-10-17)

계약: [`doc/06_코드_관리.md`](../doc/06_코드_관리.md) + [`06_코드_초기값.json`](../doc/06_코드_초기값.json), [`doc/07_관리자_사이트.md`](../doc/07_관리자_사이트.md). 계약 문서는 고치지 않았다.

## 브랜치 (꼭 읽어 주세요)

- 기준: `origin/dev`의 `50f4837`(관리자 사이트·AI 공급자 관리 계약 문서).
- **`feat/backend-admin`으로는 푸시하지 못했다.** 작업하는 동안 **다른 Claude 세션**(`session_01U2FYW1aJj2fnDyvu5eircN`)이 같은 작업을 따로 구현해 `feat/backend-admin`에 먼저 올렸다(`9deb29d`, `20f2cd4`, 같은 dev 기준). 덮어쓰지 않고, 지시대로 이 세션에 허용된 이름으로 푸시했다:
  **`claude/wearly-backend-admin-kocdh2`**
- 두 구현은 거의 같은 설계다(아래 "계약과 다르게 정한 점"이 대부분 겹친다). 둘 중 **하나만 골라 dev에 합쳐야 한다.** 둘을 섞으면 Flyway V2·V3가 충돌한다(같은 버전 번호, 내용 다름). 한쪽에만 있는 좋은 점은 골라서 옮기면 된다. 이번 세션은 저쪽 보고서를 보고 `WEARLY_AI_ALLOW_BASE_URL`(baseUrl 옵션 허용 스위치)을 같은 방식으로 넣었다.
- PR은 만들지 않았다. `main`·`dev`에 직접 푸시하지 않았다. `mobile/`, `harness/`, `admin/`, `doc/06_*`·`doc/07_*`는 고치지 않았다. 바뀐 것은 `backend/` 아래와 `doc/05_백엔드_API.md`(06·07을 가리키는 안내)뿐이다.

## 버전

1차와 같다. 새 의존성은 없다(OpenAI·Gemini는 SDK 없이 Spring `RestClient` + JDK HttpClient).

| 항목 | 버전 |
|---|---|
| Spring Boot | 4.1.1 (Hibernate 7.4.5, Flyway 12.4.0, Jackson 3.1.5) |
| mybatis-spring-boot-starter | 4.1.0 |
| Anthropic Java SDK | 2.73.0 |
| Java / Gradle | 21 / 9.8.1 |

## 마이그레이션

| 파일 | 내용 |
|---|---|
| `V2__codes.sql` | `tb_code_group`·`tb_code`(계약 2장 DDL 그대로) + 초기값 7그룹 102코드(JSON 그대로, 그룹 `sort_order`는 JSON 순서대로 10~70). `tb_wardrobe_item`의 category·sub_type·color·pattern·seasons·styles·status CHECK, `tb_user_profile`의 colors·moods CHECK 삭제. 아이템 코드 컬럼을 `VARCHAR(30)`으로 넓힘(관리자 코드가 30자까지라서). `tb_outfit_item`·`tb_daily_look_item`의 item_role CHECK에 `DRESS` 추가 |
| `V3__admin_ai.sql` | `tb_user.role`(`USER`·`ADMIN`, CHECK). `tb_ai_provider`(계약 3장 컬럼 + `enabled`면 모델 필수 CHECK) 초기값 claude(사용, `claude-opus-5-5`, `{"effort":"medium"}`)·openai·gemini(미사용, 모델 비움). `tb_ai_analysis_log.provider` CHECK 삭제·`VARCHAR(30)`, feature CHECK에 `ADMIN_TEST` 추가, 검색용 인덱스. `tb_admin_audit_log`(관리자 FK는 `ON DELETE SET NULL`) |

V1은 고치지 않았다. 기존 데이터는 변환 없이 그대로 새 코드에 맞는다.

## 테스트 결과

| 명령 | 결과 |
|---|---|
| `LC_ALL=C.UTF-8 ./gradlew test` (Testcontainers, `pgvector/pgvector:pg17`) | **78개 전부 통과, 실패 0, 건너뜀 0** (16개 클래스) |

| 테스트 클래스 | 개수 | 2차에서 |
|---|---|---|
| `AuthIntegrationTest` | 15 | 그대로 |
| `ShortAccessTtlIntegrationTest` | 1 | 그대로 |
| `ProfileIntegrationTest` | 3 | 잘못된 색 예시를 `pink`→`rainbow` (pink는 이제 있는 코드) |
| `WardrobeIntegrationTest` | 8 | 통계 키에 `DRESS`, 잘못된 색 예시 변경 |
| `CodesIntegrationTest` | 5 | 새로: `/codes` 형식·ETag 304(W/·여러 값·`*`)·공개 경로, JEANS·DRESS·JUMPSUIT·denim·FLORAL·FEMININE 등록, 모르는 코드·parent 불일치 400(bulk 포함), 비활성 코드 400과 PATCH에서 안 바꾼 비활성 값 통과(아이템·프로필), 통계 키 |
| `AdminCodeIntegrationTest` | 6 | 새로: 관리자 권한 403·401, `usageCount`, 색 CRUD·pairs 양방향(추가·제거·삭제 시)·version 변경, 사용 중 삭제 409, 검증·중복 409, 종류 비활성 시 하위 세부 종류 선택 불가, 순서 바꾸기, 변경 이력 |
| `AdminIntegrationTest` | 7 | 새로: 시작 시 관리자 생성·JWT role, 기존 계정 승격(정지 해제)·약한 비밀번호 거부, 회원 정지 → 로그인 403·토큰 401·refresh 401·해제·역할 변경·자기 자신 400, 회원 목록·페이지, 대시보드·AI 기록 검색·상세, AI 공급자 CRUD·`keyConfigured`·`usable`·baseUrl 거부·옵션 검증, CORS 5173 |
| `AiProviderRoutingIntegrationTest` | 6 | 새로(`db` 모드, 세 공급자 모두 가짜 서버): 우선순위 순 시도·검증 실패→5xx→성공, 사용 안 함·모델 없음·키 없음 건너뛰기, 시간 초과→다음, 전부 실패 502·각각 기록·한도 안 셈, 공급자 없음 502(목업 대체 없음)·시작 WARN/INFO 로그, 시험 분석은 사용 안 함이어도 호출·한도에 안 셈·키 미노출 |
| `AiAnalyzeIntegrationTest` | 4 | 코드 기준 검증으로 바꿈 |
| `AiFailureIntegrationTest` | 1 | 공급자 선택(`AiProviderRegistry`)을 가짜로 바꿔 502·실패 기록 |
| `ClaudeAiProviderTest` | 7 | 활성 코드로 만든 프롬프트·스키마(52·22개), 비활성 코드 제외·거부 |
| `OpenAiProviderTest` | 5 | 새로: Responses API 요청(input_text·input_image data URL·text.format json_schema strict·store false), output_text 파싱·토큰, 거절·잘림·짝 틀림, 401 키 조각 지우기·503, 시간 초과·HEIC |
| `GeminiAiProviderTest` | 3 | 새로: generateContent 요청(x-goog-api-key 헤더·URL에 키 없음·inlineData·responseMimeType·responseJsonSchema), 생각 part 제외·토큰 합산, 차단·잘림·400 키 지우기·500 |
| `AiModeTest` | 3 | 새로(1차 `AiProviderConfigTest` 대체): 기본 `db`, mock, 예전 claude, auto·모르는 값 시작 실패 |
| `MockAiProviderTest` | 3 | 활성 코드에서만 고름, 비활성 종류의 세부 종류 제외 |
| `ImageTypesTest` | 1 | 그대로 |

### 실제 기동 확인 (curl)

- `docker compose up -d` 후 빈 DB `wearly_boot`에 `WEARLY_ADMIN_EMAIL/PASSWORD`만 주고 `./gradlew bootRun`:
  Flyway V1→V2→V3 적용, `관리자 계정을 만들었습니다`, 키가 없으니 `WARN 쓸 수 있는 AI 공급자가 없습니다…`, `코드 7개 그룹, 102개 코드` 로그 확인. 기동 약 7초.
- `GET /codes` 200 + ETag → 같은 ETag로 304. 관리자 로그인(`user.role: ADMIN`) → 대시보드·공급자 목록(`keyConfigured:false`, `usable:false`)·회원 목록·변경 이력 200. 관리자로 색 `teal`(pairs `white`) 추가 201 → `/codes`의 version이 바뀌고 `white`의 pairs에 `teal`이 들어감, 예전 ETag는 200. 삭제 204.
- 일반 회원: 사진 분석 502 `AI_UNAVAILABLE`(목업으로 대신하지 않음), 관리자 API 403. 시험 분석은 `{ ok:false, errorCode:"KEY_MISSING" }`.
- `./gradlew bootRunMock`: `AI 공급자: mock (목업 실행 — DB 설정을 무시…)`, 사진 분석 200 `provider:"mock"`(새 코드 `FLORAL`·`FEMININE`이 나옴).
- **연동 하네스**(`harness/`, 고치지 않고 실행만): 하네스 설정(18080, `PT3S`, 한도 3, mock)으로 띄우고 `./run.sh --external http://localhost:18080` → **통과 18 · 실패 2 · 건너뜀 3**. 실패 2개는 하네스가 1차 고정 목록을 기대해서다(06 계약대로 바뀐 동작):
  - #13 통계 `byCategory`에 `DRESS: 0`이 더 있음
  - #15 목업 제안 색 `yellow`를 허용 값으로 보지 않음 (하네스의 색 목록이 12개)
  → 하네스의 기대값을 `/codes`(또는 06 초기값) 기준으로 바꾸면 된다. 건너뜀 3개는 `--external`이 Access 3초 TTL을 보장하지 않아서 건너뛰는 시나리오다.

## 실행하지 못한 검증

- **실제 Claude·OpenAI·Gemini 호출**: 키가 없어 못 했다. 세 어댑터 모두 로컬 가짜 HTTP 서버로 실제 나가는 요청 JSON·헤더와 응답 파싱·오류 처리만 확인했다.
- **공식 문서 확인 방법**: 이 클라우드 환경의 네트워크 정책이 `developers.openai.com`·`platform.openai.com`·`ai.google.dev`를 막아 WebFetch로 문서 페이지를 읽지 못했다. 대신 각 사가 공개한 **기계용 공식 명세**로 확인했다.
  - OpenAI: `openai/openai-openapi`의 `openapi.yaml`(v2.3.0) — `POST /responses`, `CreateResponse`(input·`input_text`·`input_image.image_url` data URL·`detail`), `text.format`(`type: json_schema`, `name`, `schema`, `strict`), `max_output_tokens`·`temperature`·`store`, `Response`(`status`·`incomplete_details.reason`·`output[].type=message`·`output_text`·`refusal`), `usage.input_tokens/output_tokens`, 오류 `{ error: { message } }`, Bearer 인증.
  - Gemini: `generativelanguage.googleapis.com/$discovery/rest?version=v1beta` — `models.generateContent`(`v1beta/{model}:generateContent`), `contents[].parts[].inlineData{mimeType,data}`, `generationConfig.responseMimeType`·`responseJsonSchema`·`maxOutputTokens`·`temperature`, `candidates[].finishReason`·`content.parts[].thought`, `promptFeedback.blockReason`, `usageMetadata.promptTokenCount/candidatesTokenCount/thoughtsTokenCount`, `modelVersion`. `x-goog-api-key` 헤더는 Google 공식 `google-genai` 파이썬 SDK(2.31.0)가 쓰는 방식으로 확인.
  - 실제 키로 한 번씩 확인이 필요하다: OpenAI strict 스키마가 52개 enum을 그대로 받는지, Gemini `responseJsonSchema`가 이 스키마(enum·required)를 받는지, 각 모델의 이미지 품질·지연·토큰.
- **사용자 Mac**: macOS·Docker Desktop에서 Testcontainers·`bootRun`은 확인하지 못했다(리눅스 Docker 29에서 확인).
- **앱·관리자 사이트 연동**: `mobile/`·`admin/`은 범위 밖이라 붙여 보지 않았다(하네스만 위처럼 실행).

## 계약과 다르게(또는 빈틈을 채워) 정한 점

### 공급자 선택·호출 (07 문서 3장)

1. **`WEARLY_AI_PROVIDER`**: `db`(새 기본)·`mock`. 1차 기본값 `claude`는 `db`로 받는다(기존 `.env` 호환, 초기값에서 claude가 우선순위 1이라 같은 동작). `auto`·모르는 값은 시작 실패(안내는 `AiProviderFailureAnalyzer`). 1차의 "claude인데 키 없으면 시작 실패" 규칙과 테스트는 없앴다. `WEARLY_AI_MODEL`·`WEARLY_AI_EFFORT`도 없앴다(공급자 행에서 바꾼다).
2. **다음 공급자로 넘어가는 실패**: 계약 예시(시간 초과·5xx·응답 검증 실패)뿐 아니라 **4xx(키 오류 등)·네트워크·거절·잘림·지원하지 않는 이미지 형식까지 모두** 넘어간다. 한 공급자 설정 실수로 분석 전체가 멈추지 않게.
3. **공급자가 없을 때 502**는 호출이 없으므로 `tb_ai_analysis_log`에 남기지 않는다(WARN 로그만).
4. **키 환경변수 이름**은 `^[A-Z][A-Z0-9_]*_API_KEY$`만 받는다. 관리자가 `SPRING_DATASOURCE_PASSWORD`·`WEARLY_JWT_SECRET` 같은 서버 비밀을 공급자에게 보내게 만들지 못하게.
5. **`options.baseUrl`은 `WEARLY_AI_ALLOW_BASE_URL=true`일 때만** 받는다(기본 false). false면 관리자 API가 400으로 거부하고, DB에 값이 있어도 무시해 키가 공식 주소로만 나간다. 테스트(가짜 서버)만 켠다.
6. **키 미노출**: 키는 Spring `Environment`(환경변수·`.env`)에서 호출 직전에만 읽는다. 공급자 오류 메시지는 키 값과 키 모양 문자열(`sk-…`, `AIza…`, 가린 키 `sk-ab***yz` 포함)을 지운 뒤에만 로그·시험 분석 응답에 넣는다. 관리자 응답에는 `apiKeyEnv`(이름)와 `keyConfigured`만.
7. **공급자 ID**: 영문 소문자로 시작하는 소문자·숫자·`_`·`-` 1~30자. `mock`은 목업 응답과 겹쳐 예약. 중복은 409 **`PROVIDER_EXISTS`**(계약에 없는 코드). POST의 `type`은 CLAUDE·OPENAI·GEMINI만(`MOCK`은 DB CHECK에는 남겨 두었고, 직접 넣은 행은 목업으로 동작). priority 기본값은 지금 최대값+1, 범위 0~10000.
8. **options** (`GET /admin/ai/types`): CLAUDE `effort`(low~max, 기본 medium)·`maxTokens`(기본 16000)·`timeoutSeconds`·`baseUrl` / OPENAI `temperature`(넣을 때만 보냄)·`maxTokens`(→`max_output_tokens`, 기본 16000)·`timeoutSeconds`·`baseUrl` / GEMINI `temperature`·`maxTokens`(→`maxOutputTokens`, 기본 16000)·`timeoutSeconds`·`baseUrl`. 모르는 키·형식 오류는 400(`options.키`). `null` 값은 "기본값"으로 보고 지운다. type을 바꾸면 기존 options를 새 type 기준으로 다시 검사한다.
9. **OpenAI**: Responses API, user 메시지 하나에 `input_text`(프롬프트)+`input_image`(data URL, `detail: auto`), `text.format` strict 스키마(`additionalProperties:false`), `store:false`. 재시도 없음. HEIC는 보내지 않고 `UNSUPPORTED_MEDIA`로 다음 공급자.
10. **Gemini**: 키는 URL `key=` 쿼리 대신 `x-goog-api-key` 헤더(URL이 로그에 남지 않게). `responseJsonSchema`(JSON Schema)를 쓰고 `additionalProperties`는 넣지 않는다(서버 검증이 대신). 출력 토큰 = `candidatesTokenCount + thoughtsTokenCount`, `thought:true` part는 결과에서 뺀다. 모델은 `gemini-x`·`models/gemini-x` 둘 다. **HEIC를 받는 유일한 공급자**라 iOS 사진은 Claude·OpenAI가 넘기고 Gemini가 받을 수 있다.
11. **Claude**: 1차 그대로(SDK, `output_config.format`·effort·서버 측 폴백 `fallbacks:"default"`). SDK 재시도는 1차처럼 2회. 모델·effort·max_tokens·제한 시간·baseUrl은 공급자 행에서.
12. **집계 기준**: 공급자 목록 `today`와 대시보드 `aiToday`·`aiByProvider7d`는 **모든 기능(사진 분석 + 시험 분석)**을 센다(비용·장애 확인용). 회원 목록 `aiToday`는 한도와 같은 기준(오늘 성공한 회원 사진 분석). 대시보드 7일은 한국 날짜로 오늘 포함 7일(오늘-6일 0시부터).
13. **시험 분석**: 목업 실행이어도 그 행의 실제 공급자를 부른다(비교가 목적). 사용 여부는 보지 않는다. 모델이 없거나 키가 비면 호출·기록 없이 200 `{ ok:false, errorCode:"MODEL_MISSING" | "KEY_MISSING" }`. 사진 형식 오류는 400.

### 코드 (06 문서, 07 문서 4장)

14. **코드 형식**: COLOR `^[a-z][a-z0-9_]{0,29}$`, 그 밖 `^[A-Z][A-Z0-9_]{0,29}$`(영문으로 시작해야 함).
15. **attrs**: 그룹에 없는 키는 400(`attrs.키`). 필수 — CATEGORY `slot`(없으면 null), SUB_TYPE `volume`·`warmth`·`shape`, COLOR `hex`·`neutral`·`light`(`pairs` 없으면 []), ITEM_STATUS `recommendable`. PATTERN·SEASON·STYLE은 빈 객체만. `hex`는 대문자로, `pairs`는 중복 제거·알파벳순·자기 자신 금지로 저장.
16. **pairs 양방향**: 색을 추가·수정하면 내 pairs에 있는 색에는 나를 넣고, 없는 색에서는 나를 뺀다. 색을 지우면 모든 색의 pairs에서 뺀다. 상대 색 변경은 `CODE_PAIRS_SYNC` 이력으로 따로 남긴다.
17. **parent**: SUB_TYPE만, 필수(있는 CATEGORY 코드, 비활성이어도 됨). 다른 그룹에 보내면 400.
18. **삭제 409 `CODE_IN_USE`**: 쓰는 아이템, 쓰는 프로필(COLOR→colors, STYLE→moods), CATEGORY는 하위 SUB_TYPE이 있을 때도. 메시지에 이유와 개수를 넣는다. (테이블만 있는 코디·착용 기록·`tb_planned_outfit.concepts`·`tb_user_style`은 보지 않는다. V1의 `ck_planned_outfit_concepts`·`ck_user_style_code`는 계약 목록에 없어 남겨 두었다.)
19. **순서**: 그룹의 코드 전부를 한 번씩 보내야 한다(빠짐·중복·모르는 코드 400). 응답은 그 순서의 코드 배열.
20. **PATCH**: 보낸 필드만, `attrs`는 통째로. `code`를 다른 값으로 보내면 400. 실제로 바뀐 게 없으면 이력·캐시 갱신도 없다(version 그대로).
21. **version** = `tb_code`의 `max(updated_at)`(마이크로초 ISO) + `#` + 코드 수. 응답 `ETag: "<version>"`, `Cache-Control: no-cache`. `If-None-Match`는 여러 값·약한 비교(`W/`)·`*`를 받는다. CORS에 `If-None-Match` 요청 헤더와 `ETag` 노출을 더했다.
22. **`/codes` 공개 응답**에는 그룹 `description`을 넣지 않는다(계약 예시 모양). 만료된 Authorization 헤더가 붙어 와도 무시한다.
23. **"새로 고르는 값"**: 등록·bulk는 모두 새 값. PATCH는 저장값과 같으면 비활성이어도 통과(안 보낸 필드도, 같은 값을 다시 보낸 필드도). 배열은 원소마다 저장된 배열에 있었는지로 본다. 프로필 PUT도 저장돼 있던 colors·moods는 통과. 비활성 = `active=false` 또는 SUB_TYPE의 상위 CATEGORY가 비활성. 메시지: 모르는 값 "허용되지 않는 값이에요.", 비활성 "더 이상 고를 수 없는 값이에요.", 짝 "카테고리와 맞지 않는 세부 종류예요."
24. **필터**(`category`·`status`·`color`)는 비활성 코드도 받는다. 모르는 값만 400.
25. **통계 키 순서**: 코드 `sort_order` 순. 코드 테이블에 없는 값이 데이터에 남아 있으면(직접 넣은 경우) 맨 뒤에 붙인다.

### 관리자·회원 (07 문서 2·4장)

26. **권한은 요청마다 DB 역할**로 판단한다. JWT `role` 클레임은 관리자 사이트 표시용. 역할을 바꾸면 기존 토큰에도 바로 반영된다.
27. **정지**: 로그인은 비밀번호가 맞을 때만 403 `ACCOUNT_SUSPENDED`(틀리면 401 `INVALID_CREDENTIALS`). 기존 토큰 401 `TOKEN_INVALID`, refresh 401 `REFRESH_INVALID`(회원 상태 확인으로 막는다. Refresh 행을 따로 폐기하지는 않는다 — 해제하면 기존 refresh가 다시 쓰인다). 자기 자신 정지·역할 내리기는 400.
28. **시작 시 관리자**: 이미 있는 계정은 역할을 올리고 정지돼 있으면 ACTIVE로 되돌린다. 비밀번호·이름은 바꾸지 않는다. 새로 만들 때 비밀번호가 가입 규칙(8~64자, 영문+숫자)에 안 맞으면 만들지 않고 WARN. 이름은 20자로 자른다. 이력은 `ADMIN_BOOTSTRAP_CREATE`·`ADMIN_BOOTSTRAP_PROMOTE`(관리자 ID null).
29. **AI 기록 검색**: `from`·`to`는 `YYYY-MM-DD`(한국 날짜, to는 그날 끝까지) 또는 ISO-8601 시각. `userEmail` 부분 일치(대소문자 무시). 계약에 없는 `feature`(`ITEM_ANALYZE`·`ADMIN_TEST`) 필터를 더했다. 결과 요약은 `"이름 (종류/세부 종류, 색)"`.
30. **변경 이력 action**: `CODE_CREATE`·`CODE_UPDATE`·`CODE_PAIRS_SYNC`·`CODE_REORDER`(targetId = 그룹)·`CODE_DELETE`, `AI_PROVIDER_CREATE`·`_UPDATE`·`_DELETE`, `USER_UPDATE`, `ADMIN_BOOTSTRAP_*`. 코드 targetId는 `GROUP:CODE`. 이력은 변경과 같은 트랜잭션에 저장(함께 커밋·롤백). `targetType` 필터는 `CODE`·`AI_PROVIDER`·`USER`, 그 밖은 400.
31. **페이지**: `page`<0, `size` 1~100 밖은 400. 공급자 목록(`/admin/ai/providers`)은 계약대로 배열(페이지 없음).

## 앱·관리자 사이트가 알아야 할 응답 형식

```ts
// GET /api/v1/codes  (공개, ETag)
interface CodesResponse { version: string; groups: { group: string; name: string; codes: Code[] }[] }
interface Code { code: string; label: string; parent: string | null; sortOrder: number; active: boolean; attrs: Record<string, unknown> }
// GET /api/v1/admin/codes: 같은 모양 + groups[].description: string, codes[].usageCount: number

// UserDto (로그인·가입·refresh 응답 user, GET /users/me)
interface UserDto { id: string; email: string | null; name: string; role: 'USER' | 'ADMIN'; createdAt: string }

// AnalyzeResponse.provider: 공급자 ID 문자열 ("claude" | "openai" | "gemini" | 관리자가 만든 ID | "mock")
// ItemDto·ItemInput·WardrobeStats·AiSuggestion의 속성 값: 코드 문자열 (byCategory·byStatus는 Record<string, number>)

// 목록 공통
interface Page<T> { items: T[]; page: number; size: number; total: number }

// GET /admin/dashboard
interface Dashboard { users: number; newUsers7d: number; items: number;
  aiToday: { total: number; success: number; failed: number };
  aiByProvider7d: { providerId: string; success: number; failed: number; avgLatencyMs: number | null; inputTokens: number; outputTokens: number }[] }

// GET /admin/ai/providers → ProviderDto[]
interface ProviderDto { providerId: string; type: 'CLAUDE' | 'OPENAI' | 'GEMINI'; displayName: string; model: string | null;
  apiKeyEnv: string; keyConfigured: boolean; enabled: boolean; priority: number; options: Record<string, unknown>;
  usable: boolean; today: { success: number; failed: number } }
// POST /admin/ai/providers/{id}/test
interface ProviderTestResult { ok: boolean; suggestion: AiSuggestion | null; errorCode: string | null; errorMessage: string | null;
  latencyMs: number; inputTokens: number | null; outputTokens: number | null; model: string | null }
// GET /admin/ai/types
interface ProviderTypeInfo { type: string; displayName: string; defaultApiKeyEnv: string;
  options: { key: string; type: 'enum' | 'integer' | 'number' | 'url'; description: string; defaultValue: unknown;
             min: number | null; max: number | null; values: string[] | null }[] }

// GET /admin/ai/logs → Page<AiLogItem>, GET /admin/ai/logs/{id} → AiLogItem + { imageSha256, result }
interface AiLogItem { id: string; createdAt: string; userId: string; userEmail: string | null; feature: 'ITEM_ANALYZE' | 'ADMIN_TEST';
  providerId: string; model: string | null; status: 'SUCCESS' | 'FAILED'; latencyMs: number; inputTokens: number | null;
  outputTokens: number | null; imageBytes: number | null; resultSummary: string | null; errorCode: string | null }

// GET /admin/users → Page<UserItem>, PATCH /admin/users/{id} → UserItem
interface UserItem { id: string; email: string | null; name: string; role: 'USER' | 'ADMIN'; status: 'ACTIVE' | 'SUSPENDED';
  createdAt: string; itemCount: number; aiToday: number }

// GET /admin/audit-logs → Page<AuditItem>
interface AuditItem { id: string; adminUserId: string | null; adminEmail: string | null; action: string; targetType: string;
  targetId: string; before: object | null; after: object | null; createdAt: string }
```

새 오류 코드: 403 `ACCOUNT_SUSPENDED`, 409 `CODE_EXISTS`·`CODE_IN_USE`·`PROVIDER_EXISTS`. 관리자 API의 403은 1차부터 있던 `FORBIDDEN`. 코드 PUT order 응답은 그 순서의 `Code[]`, 코드 POST·PATCH 응답은 `Code` 하나(usageCount 없음).

## 로컬에서 보강이 필요한 점

1. **브랜치 정리**: `feat/backend-admin`(다른 세션)과 `claude/wearly-backend-admin-kocdh2`(이 세션) 중 하나를 골라 dev에 합친다. 둘 다 합치면 V2·V3가 충돌한다.
2. **하네스 기대값 갱신**: `harness/`의 통계 `byCategory` 기대값에 `DRESS`, 허용 색 목록을 22색(또는 `/codes` 기준)으로. 그 뒤 `./run.sh`(3초 TTL 포함 전체)로 다시 확인.
3. **실제 키로 확인**: `backend/.env`에 `ANTHROPIC_API_KEY`(·`OPENAI_API_KEY`·`GEMINI_API_KEY`)와 `WEARLY_ADMIN_*`를 넣고, 관리자 화면에서 openai·gemini 행에 모델을 넣어 **시험 분석**으로 세 공급자를 같은 사진으로 비교한다. OpenAI strict 스키마·Gemini `responseJsonSchema`가 이 스키마를 그대로 받는지 여기서 처음 확인된다.
4. **Mac에서 `./gradlew test`** (Docker Desktop의 Testcontainers).
5. **운영 준비**(범위 밖으로 남긴 것): 관리자 2단계 인증·IP 제한, 정지 시 Refresh 행 폐기 여부, AI 한도 동시성(Redis·DB 락), 코드 캐시의 다중 서버 무효화(지금은 서버 한 대 기준 메모리 캐시 — 서버를 여러 대 띄우면 다른 서버의 캐시는 재시작 전까지 옛 값), 공급자별 비용 단가.
6. 리눅스 CI는 `LC_ALL=C.UTF-8`.
