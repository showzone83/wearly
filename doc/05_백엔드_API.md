# 05. 백엔드 API 계약 (1차: 토큰 로그인 · 옷장 · AI 분석)

> 백엔드(`backend/`), 앱(`mobile/src/api/`), 연동 하네스(`harness/`)가 함께 따르는 계약이다. 셋 중 하나를 바꾸면 이 문서부터 고친다.
>
> **2차 변경**: 옷 속성 값(종류·세부 종류·색·패턴·계절·스타일·보유 상태)은 DB 코드 테이블로 옮겼다 → [06_코드_관리.md](06_코드_관리.md).
> 관리자 역할·관리자 API·AI 공급자(Claude·OpenAI·Gemini) 관리는 → [07_관리자_사이트.md](07_관리자_사이트.md).
> 이 문서에서 두 문서와 겹치는 부분은 06·07 문서가 우선이다.
>
> **3차 변경**: 백엔드 내부 구조를 헥사고날로 바꿨다 → [08_백엔드_구조.md](08_백엔드_구조.md) (API·DB는 그대로).
> AI 키를 관리자 화면에서 넣는 기능(쓰기 전용 · 암호화 저장, `V4__ai_provider_secret.sql`)은 → 07 문서 6장.
>
> **7차 변경(2026-10-17)**: Refresh 토큰·로그인 세션을 DB에서 Valkey로 옮겼다(`tb_refresh_token` 삭제, `tb_auth_event` 추가, `V10__auth_session_valkey.sql`). Access 토큰에 `sid`(세션 ID)가 들어가고 매 요청 세션이 살아 있는지 확인한다. 관리자 로그인 사용자 보기·강제 로그아웃 → [16_세션_Valkey.md](16_세션_Valkey.md).
>
> **사진 저장(2026-10-18)**: 옷 사진을 S3 호환 오브젝트 스토리지(로컬 MinIO)에 서버가 저장한다(`V12__item_image_storage.sql`). 이미지 API(`/images/upload-url`·`/images/{id}/complete`), 아이템 `image`·`imageId`, analyze `{imageId}` → [18_사진_저장.md](18_사진_저장.md). 이 문서의 "이미지 업로드·저장 제외"·"사진은 앱 기기에만" 문구는 계정 모드에서 18 문서가 우선이다.
>
> **장소별 날씨(2026-10-18)**: 예정일 코디에서 다른 장소·먼 날짜의 날씨를 본다. `GET /api/v1/weather/day`·`GET /api/v1/weather/places`, 평년값, 오류 코드 `WEATHER_LIMIT`(`V13__place_weather.sql`) → [19_장소별_날씨.md](19_장소별_날씨.md). 날씨 수집·관리자 API는 [17_날씨.md](17_날씨.md).
>
> **오류 응답(2026-10-18)**: 오류 응답에서 `message`를 없애고 `{ status, code, params?, fieldErrors: [{ field, code, params? }], traceId }`로 바꿨다. 화면 문구는 앱·관리자가 코드별 표로 정한다 → [20_오류_응답.md](20_오류_응답.md). 오류 코드 표는 이 문서에서 없애고 20 문서·`error-codes.json`을 기준으로 한다.
>
> **예정 코디(2026-10-18)**: 예정 코디를 서버에 저장한다. `GET·POST·PATCH·DELETE /api/v1/plans`, `POST /api/v1/plans/bulk`, 오류 코드 `PLAN_LIMIT`(`V14__planned_outfit.sql`) → [21_예정_코디_저장.md](21_예정_코디_저장.md).
>
> **관리자 계정 보완(2026-10-18)**: 관리자 사이트 로그인에 2단계 인증(TOTP)이 붙었다. `POST /auth/login`이 토큰 대신 MFA 대기 토큰을 줄 수 있고, `POST /auth/mfa/setup`·`/auth/mfa/verify`, `POST /admin/me/password`, `/admin/me/mfa/*`, `GET /admin/auth-events`, 새 오류 코드 8개(`V15`·`V16`) → [22_관리자_계정_관리.md](22_관리자_계정_관리.md) 4.1.
>
> **이슈 #4·#5·#6, D-1 알림, 프롬프트 캐싱(2026-10-18)**: 옷 색 1~3개·구입일·초봄/초여름/초가을 계절(`V20`, 6장 "옷장"), 나의 정보 연령대·체형·키·핏(`V21`, 6장 "회원·프로필"), 계절 테마 `seasonMode`(6장 "앱 설정", [12](12_디자인_테마.md) 7장), 푸시 토큰 `PUT·DELETE /me/push-token`(`V17`, [23](23_D-1_알림.md)), AI 프롬프트 캐싱(7장).
>
> **4차 변경**: AI 코디 추천 `POST /api/v1/outfits/recommendations`(`V5__outfit_recommend.sql`) → [09_AI_코디_추천.md](09_AI_코디_추천.md). 백엔드 코드 스타일(Google Java Style) → 08 문서 5장.

## 1. 범위

| 포함 | 제외 (다음 단계) |
|---|---|
| 이메일·비밀번호 가입과 로그인, JWT Access 토큰, Refresh 토큰 회전 | 소셜 로그인 (카카오·애플·구글) |
| 내 정보 조회, 회원 탈퇴 | ~~이미지 업로드·저장~~ → 사진 저장에서 포함 (18 문서). 게스트 사진은 기기에만 |
| 취향·근무 패턴 프로필 조회·저장 | 코디 저장·피드백·착용 기록 API (테이블만 만든다). ~~예정일~~ → 예정 코디 API는 포함 (21 문서) |
| 옷장 아이템 CRUD, 일괄 등록, 필터 목록, 통계 | 추천·진단의 서버 이전 (앱의 규칙 엔진 유지) |
| AI 사진 분석 (기본 Claude, 목업은 명시할 때만) | 소셜 토큰 검증, App Check, Redis 요청 제한 |

이메일·비밀번호 로그인은 기술 결정 D5에서 출시 범위에서 뺀 수단이다. 소셜 로그인 키를 받기 전까지 토큰 흐름을 먼저 완성하기 위한 **임시 수단**이며, 자격 증명을 `tb_user_local_credential`에 따로 두어 나중에 소셜 계정과 같은 자리에서 교체할 수 있게 한다.

## 2. 실행 환경

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.x (MyBatis 스타터가 호환되지 않으면 4.0.x) |
| Java | 21 (Gradle toolchain) |
| 빌드 | Gradle Kotlin DSL, wrapper 포함 |
| DB | `docker compose up -d db` → `wearly-postgres`, `localhost:5433`, DB·계정·비밀번호 모두 `wearly` (로컬 전용). `docker compose up -d --build`는 Valkey·MinIO(옷 사진, 9000·9001)·백엔드(8080)·관리자 사이트(5173)까지 띄운다 |
| 스키마 | Flyway가 만든다. JPA는 `ddl-auto: validate` |
| 서버 포트 | 8080 |
| 비밀 값 | `backend/config/application-local.yml` (git·docker 이미지 제외, 견본 `application-local.example.yml`, `.env` 쓰지 않음 — 08 문서 6장). `wearly.ai.keys.ANTHROPIC_API_KEY`·`OPENAI_API_KEY`·`GEMINI_API_KEY`(공급자 행의 `api_key_env` = 서버 설정 키 이름), `wearly.jwt.secret`, `wearly.storage.*`(옷 사진 저장소, 로컬 MinIO 값이 기본이라 로컬에서는 적지 않는다, 18 문서 3장), `wearly.admin.email`·`wearly.admin.password`(·`wearly.admin.name`), 3차 `wearly.secret-key`(웹 입력 AI 키의 마스터 키, base64 32바이트). 같은 값을 OS 환경변수(`ANTHROPIC_API_KEY`, `WEARLY_JWT_SECRET`, `WEARLY_ADMIN_EMAIL` …, `WEARLY_SECRET_KEY`)로도 줄 수 있다 |

### 데이터 접근 분담

- **JPA**: 쓰기와 단건 조회. 엔티티 생명주기·연관 삭제가 필요한 곳 (회원, 자격 증명, 로그인 이력, 프로필, 아이템 등록·수정·삭제). Refresh 토큰·로그인 세션은 7차부터 Valkey(16 문서).
- **MyBatis**: 읽기 전용 조회. 동적 필터 목록, 집계 통계, AI 일일 사용량 집계처럼 SQL이 더 읽기 쉬운 곳.
- 같은 트랜잭션 매니저를 쓴다. MyBatis 매퍼는 XML(`resources/mapper/*.xml`)로 둔다.

## 3. 테이블 (Flyway `V1__init.sql`, 2차 `V2__codes.sql`·`V3__admin_ai.sql`, 3차 `V4__ai_provider_secret.sql`, 4차 `V5__outfit_recommend.sql`)

2차에서 더한 테이블: `tb_code_group`·`tb_code`(06 문서 2장), `tb_ai_provider`·`tb_admin_audit_log`, `tb_user.role`(07 문서 2·3장).
3차에서 더한 테이블: `tb_ai_provider_secret`(웹 입력 AI 키의 AES-256-GCM 암호문, 07 문서 6장).
4차: `tb_ai_analysis_log.feature` CHECK에 `OUTFIT_RECOMMEND`(AI 코디 추천, 이미지 칸은 NULL)를 더하고, 한도 집계 인덱스를 `(user_id, feature, created_at) WHERE status = 'SUCCESS'`로 바꿨다 (09 문서).
관리자 계정 보완(2026-10-18, 22 문서 4.1): `V15__admin_mfa.sql` — `tb_user_mfa`(TOTP 비밀 암호문·nonce·`last_used_step`)·`tb_user_mfa_recovery_code`(복구 코드 SHA-256), `V16__auth_event_mfa.sql` — `tb_auth_event.type`에 `LOGIN_FAILED`·`MFA_SUCCESS`·`MFA_FAILED`·`MFA_LOCKED`를 더하고 최신순 인덱스.
관리자 회사 계정 로그인(2026-10-18, 22 문서 4.2): `V22__admin_sso.sql` — `tb_user_sso`(IdP 계정 = 발급자 `issuer` + `subject`, `UNIQUE(issuer, subject)`)를 더하고, `tb_auth_event`에 `sso_provider`·`sso_amr` 열과 `SSO_LOGIN`·`SSO_DENIED` 종류를 더한다.
D-1 알림(2026-10-18, 이 문서 6장 "푸시 토큰", [23](23_D-1_알림.md)): `V17__push_token.sql` — `tb_push_token`(Expo 푸시 토큰, 회원·기기당 하나)·`tb_push_send_log`(D-1 발송 기록, 같은 예정은 하루 한 번).
이슈 #4(2026-10-18): `V20__item_colors_purchase.sql` — `tb_wardrobe_item`에 `sub_colors TEXT[]`(보조 색 0~2개)·`purchased_at DATE NOT NULL`·`purchased_at_estimated`, 기존 행은 구입일 = 등록일(서울)·추정 표시, 코드 SEASON에 `EARLY_SPRING`·`EARLY_SUMMER`·`EARLY_FALL` 3개 (06 문서).
이슈 #5(2026-10-18): `V21__user_personal_info.sql` — `tb_user_profile`에 `age_range`·`body_type`·`height_cm`(130~210)·`fit_preference`(모두 NULL 허용), 코드 그룹 `AGE_RANGE`·`BODY_TYPE`·`FIT` (06 문서).
Flyway 번호는 V1~V17, V20, V21이다. AI 호출 기록(`tb_ai_analysis_log`)에 캐시 토큰 칸을 더하는 마이그레이션은 아직 없다(V19 예정, 아래 7장 "프롬프트 캐싱").
사진 저장(2026-10-18): `V12__item_image_storage.sql` — V1의 빈 `tb_item_image`를 지우고 다시 만든다(`purpose`·`status`·`declared_size`·`upload_key`·`deleted_at` 등, 아이템당 사진 하나의 부분 유니크 인덱스, 18 문서 4장). V11은 날씨(17 문서).
예정 코디(2026-10-18): `V14__planned_outfit.sql` — V1의 빈 `tb_planned_outfit`을 지우고 다시 만든다(`client_id`·장소 4칸·`weather`/`outfit` JSONB·`forecast_ready`, `(user_id, client_id)` 부분 유니크, 21 문서 3장).
장소별 날씨(2026-10-18): `V13__place_weather.sql` — `tb_weather_place_snapshot`(REGION 밖 좌표의 예보 캐시)·`tb_weather_climate`(격자·월별 평년값)를 더하고 `tb_weather_call_log`의 `run_id`·`region_code`를 NULL 허용으로 바꿨다(`call_kind` = `FORECAST`·`ON_DEMAND`·`SEARCH`·`CLIMATE`, 19 문서 7장).
7차(2026-10-17): `V10__auth_session_valkey.sql` — `tb_refresh_token` 삭제(토큰·세션은 Valkey), `tb_auth_event`(로그인 이력: LOGIN·LOGOUT·REFRESH_REUSED·FORCED_LOGOUT, 16 문서 3장) 추가.
6차(2026-10-17): `V8__app_setting.sql` — `tb_app_setting`(앱 전체 설정, 초기값 `theme` = `{"themeId":"nyang"}`, 12 문서 3장). `V9__wardrobe_item_retire.sql` — `tb_wardrobe_item`에 `retired_at`·`retire_reason`·`retire_memo`, 부분 인덱스 `ix_wardrobe_item_in_use (user_id) WHERE retired_at IS NULL`, CHECK `ck_wardrobe_item_retired`(사용 중지면 시각·이유가 함께 있다), 코드 그룹 `RETIRE_REASON` 5개(13 문서 2장).

모든 이름은 소문자 snake_case, 접두사 `tb_`. PK는 `BIGINT GENERATED ALWAYS AS IDENTITY`. 시각은 `TIMESTAMPTZ`, 날짜는 `DATE`. 회원 삭제 시 모든 하위 데이터는 `ON DELETE CASCADE`로 지운다.

| 테이블 | 용도 | 1차 API 사용 |
|---|---|---|
| `tb_user` | 회원 (`email` 참고용·nullable, `name`, `status`) | O |
| `tb_user_local_credential` | 이메일·비밀번호 (임시). `email` 소문자 UNIQUE, `password_hash` | O |
| `tb_user_social` | 소셜 계정 (D5 설계 그대로, `UNIQUE(provider, provider_user_id)`) | 테이블만 |
| ~~`tb_refresh_token`~~ | 7차에 삭제. Refresh 토큰·세션은 Valkey `wearly:rt:*`·`wearly:session:*` (16 문서 3장) | — |
| `tb_auth_event` | 7차. 로그인 이력: `type`(LOGIN·LOGOUT·REFRESH_REUSED·FORCED_LOGOUT, V16에서 LOGIN_FAILED·MFA_SUCCESS·MFA_FAILED·MFA_LOCKED, V22에서 SSO_LOGIN·SSO_DENIED와 `sso_provider`·`sso_amr` 추가), `session_id`, `client`, `ip`, `user_agent` | O (16 문서, 22 문서) |
| `tb_user_mfa` / `tb_user_mfa_recovery_code` | V15. 관리자 TOTP 비밀(AES-256-GCM 암호문, AAD `mfa:{user_id}`)·마지막 사용 단계 / 복구 코드 해시 10개(`used_at`) | O (22 문서 4.1.4) |
| `tb_user_sso` | V22. 관리자 회사 계정(IdP) 연결: `user_id`, `provider`(설정 공급자 ID), `issuer`+`subject`(UNIQUE), `email`, `connected_at`, `last_login_at`. 고객 소셜 로그인(`tb_user_social`)과 섞지 않는다 | O (22 문서 4.2.3) |
| `tb_push_token` / `tb_push_send_log` | V17. 앱이 등록한 Expo 푸시 토큰(회원·기기당 하나, 같은 토큰은 한 행) / D-1 알림 발송 기록(`UNIQUE(예정, 종류, 발송일)`, 30일 보관) | O ([23](23_D-1_알림.md)) |
| `tb_user_profile` | 앱 `Profile` + `onboarded`. 배열은 `TEXT[]`/`SMALLINT[]`, 알림은 `JSONB`. 나의 정보 `age_range`·`body_type`·`height_cm`·`fit_preference`(V21) | O |
| `tb_user_style` | 취향 학습 점수 (`style_code`, `preference_score`) | 테이블만 |
| `tb_wardrobe_item` | 옷장 아이템. `seasons`·`styles`는 `TEXT[]`, `ai_suggestion JSONB`. 색은 주 색상 `color` + 보조 색 `sub_colors TEXT[]`(0~2개), 구입일 `purchased_at`·`purchased_at_estimated`(V20). 사용 중지는 `retired_at`(NULL이면 사용 중)·`retire_reason`·`retire_memo` | O |
| `tb_item_image` | 옷 사진 메타데이터(V12에서 다시 만듦). `image_id` UUID, `item_id`(아이템당 하나), `status`(PENDING·READY·DELETED), 크기·가로·세로, 오브젝트 키. 파일은 오브젝트 스토리지(18 문서 4장) | O (18 문서) |
| `tb_outfit` / `tb_outfit_item` | 코디 헤더(점수·`breakdown JSONB`·`context JSONB`) / 구성(`item_role` = 슬롯) | 테이블만 |
| `tb_outfit_feedback` | LIKE·DISLIKE, `reasons JSONB` | 테이블만 |
| `tb_planned_outfit` | 예정 코디(V14에서 다시 만듦). 날짜·시간대·장소·날씨·코디(`outfit JSONB`), 앱 기기 id는 `client_id` | O (21 문서) |
| `tb_daily_look` / `tb_daily_look_item` | 착용 기록, `UNIQUE(user_id, worn_date)` | 테이블만 |
| `tb_look_diagnosis` | 진단 (`axes JSONB`, `swap JSONB`) | 테이블만 |
| `tb_wardrobe_report` | 옷장 분석 리포트 (`report JSONB`) | 테이블만 |
| `tb_ai_analysis_log` | AI 호출 기록: 공급자·모델·상태·지연·토큰 수·결과. 일일 한도와 '코디당 AI 비용' 지표에 쓴다 | O |
| `tb_app_setting` | 앱 전체 설정. `setting_key` PK, `value JSONB`, `updated_by`(관리자, 탈퇴하면 NULL), `updated_at`(바꾼 적 없으면 NULL) | O (12 문서) |

열거형 값은 앱 `mobile/src/data/types.ts`의 문자열을 그대로 `VARCHAR`로 저장한다 (`CHECK` 제약 권장). 단, 옷 속성 7개 그룹은 2차부터 CHECK 대신 코드 테이블로 서버가 검증한다 (06 문서). 코디 칸(`item_role`)에 `DRESS`가 들어갔다.

## 4. 공통 규칙

- 기본 경로 `/api/v1`, JSON, UTF-8. 필드는 camelCase.
- **ID는 JSON에서 문자열**이다 (`"id": "42"`). 앱의 기존 문자열 ID와 맞추고 JS 정수 한계를 피한다.
- 날짜는 `YYYY-MM-DD`, 시각은 ISO-8601 UTC (`2026-10-17T03:00:00Z`).
- 인증: `Authorization: Bearer <accessToken>`. 공개 경로는 `/api/v1/auth/**`, `GET /api/v1/codes`(2차), `GET /api/v1/app/config`(6차, 12 문서), `/actuator/health`뿐이다. `/api/v1/admin/**`는 `ADMIN`만 (07 문서).
- CORS: `http://localhost:8081`, `http://localhost:19006`, `http://127.0.0.1:8081` 허용 (Expo 웹), 관리자 사이트 `http://localhost:5173`(2차).

### 오류 응답

서버는 **문장을 보내지 않고 코드값만** 보낸다. 모양, 오류 분류, `traceId`, 코드 카탈로그, 앱·관리자 문구 표, 새 코드를 더하는 순서는 [20_오류_응답.md](20_오류_응답.md)에 있다.

```json
{ "status": 401, "code": "TOKEN_EXPIRED", "fieldErrors": [], "traceId": "5e4d6d86a1dd42a1800fd0fcb51335fd" }
```

```ts
interface ErrorBody {
  status: number;                       // HTTP 상태와 같다
  code: string;                         // ErrorCode 이름 (추가만 한다)
  params?: Record<string, unknown>;     // 그 코드의 상세 값. 있을 때만 (예: WARDROBE_INSUFFICIENT { missing, alternatives })
  fieldErrors: { field: string; code: string; params?: Record<string, unknown> }[];  // 입력 검증 사유 (VALIDATION_FAILED). 항상 배열
  traceId: string;                      // 응답 헤더 X-Request-Id와 같다. 서버 로그에서 이 값으로 찾는다
}
```

- `message` 필드는 없다. 앱·관리자는 `code`·`params`로 화면 문구를 정한다 (앱 `mobile/src/lib/errorText.ts`, 관리자 `admin/src/lib/errorText.ts`).
- 서버 내부 정보(예외 메시지·SQL·클래스 이름·스택·공급자 응답 원문)는 응답에 나가지 않는다.
- 시스템 오류(500 `INTERNAL_ERROR`, 503 `SERVICE_UNAVAILABLE`)는 화면에 "오류 번호 + traceId 앞 8자"를 보인다.
- **오류 코드 표(코드·HTTP 상태·params·권장 문구)는 [`backend/src/main/resources/error-codes.json`](../backend/src/main/resources/error-codes.json)** 이 기준이다 (20 문서 5장, 자주 쓰는 params는 2장) (오류 코드 51개, 필드 오류 코드 21개). 이 문서에서는 코드별 상황을 각 엔드포인트 설명에 적는다.

코드별 상황은 해당 기능 문서에 있다.

| 코드 묶음 | 문서 |
|---|---|
| 인증·토큰: `UNAUTHORIZED`, `TOKEN_EXPIRED`, `TOKEN_INVALID`, `INVALID_CREDENTIALS`, `REFRESH_INVALID`, `REFRESH_REUSED`, `ACCOUNT_SUSPENDED`, `EMAIL_TAKEN` | 이 문서 5장, [16](16_세션_Valkey.md) |
| 관리자 회사 계정 로그인(SSO): `SSO_NOT_ADMIN`, `SSO_EMAIL_UNVERIFIED`, `SSO_STATE_INVALID`, `SSO_TOKEN_INVALID`, `SSO_IDP_ERROR`(`params.reason`), `SSO_PROVIDER_UNAVAILABLE`, `SSO_EXCHANGE_INVALID` | [22](22_관리자_계정_관리.md) 4.2.8 |
| 권한: `FORBIDDEN`, `REAUTH_FAILED` | [07](07_관리자_사이트.md) |
| 관리자 계정·2단계 인증: `LAST_ADMIN`, `MFA_REQUIRED`, `MFA_TOKEN_INVALID`, `MFA_CODE_INVALID`(`params.remainingAttempts`), `MFA_LOCKED`(`params.retryAfterSeconds`), `MFA_SETUP_REQUIRED`, `MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED` | [22](22_관리자_계정_관리.md) 4.1.8 |
| 관리자 코드·공급자: `CODE_EXISTS`, `CODE_IN_USE`, `PROVIDER_EXISTS`, `SECRET_KEY_MISSING`, `API_KEY_INVALID` | [06](06_코드_관리.md), [07](07_관리자_사이트.md) 4·6장 |
| AI: `AI_LIMIT_EXCEEDED`, `AI_UNAVAILABLE`(`params.feature`), `WARDROBE_INSUFFICIENT` | 이 문서 7장, [09](09_AI_코디_추천.md) |
| 사진: `IMAGE_INVALID`(`params.reason`), `IMAGE_NOT_UPLOADED`, `IMAGE_IN_USE`, `IMAGE_UPLOAD_LIMIT`, `IMAGE_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE` | [18](18_사진_저장.md) 6장 |
| 날씨: `WEATHER_UNAVAILABLE`, `WEATHER_LIMIT`(`params.kind`), `WEATHER_REFRESH_RUNNING`, `WEATHER_NO_PROVIDER` | [17](17_날씨.md), [19](19_장소별_날씨.md) |
| 예정 코디: `PLAN_LIMIT`(`params.max` = 500) | [21](21_예정_코디_저장.md) 2.2 |
| 공통: `VALIDATION_FAILED`, `NOT_FOUND`(남의 것도 404), `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `CONFLICT`, `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE` | [20](20_오류_응답.md) 3장 |

## 5. 토큰

| 항목 | 값 |
|---|---|
| Access | JWT HS256, `sub` = userId, `iss` = `wearly`, `role` = `USER`·`ADMIN`(2차), `sid` = 로그인 세션 ID(7차), 기본 30분 (`WEARLY_ACCESS_TTL`, 예: `PT30M`). `exp`는 초 단위 올림(수명이 설정값 이상) |
| 매 요청 확인 | 서명·발급자·만료 → (7차) `sid`의 세션이 Valkey에 있고 그 회원 것인지 → 회원이 ACTIVE인지. 만료는 `TOKEN_EXPIRED`, 나머지는 `TOKEN_INVALID`. 세션을 끊으면 남은 Access 수명과 상관없이 바로 401 |
| Refresh | 32바이트 난수 base64url (JWT 아님). 저장소(7차부터 Valkey)에는 SHA-256만 저장. 기본 30일 (`WEARLY_REFRESH_TTL`) |
| 세션 | 가입·로그인 한 번 = 세션 하나(예전 family). 요청 헤더 `X-Wearly-Client: app\|admin\|web`(없으면 User-Agent로 추정)와 IP(`X-Forwarded-For` 첫 값)를 남긴다 (16 문서) |
| 회전 | `/auth/refresh`마다 새 Refresh를 주고 이전 것은 ROTATED(+ `replacedBy`). 같은 세션이고 세션 만료가 연장된다. 동시에 같은 토큰으로 갱신하면 한쪽만 성공 |
| 재사용 감지 | 이미 회전된 토큰이 다시 오면 그 세션 전체를 끊고 `REFRESH_REUSED` |
| 로그아웃 | 그 Refresh 토큰의 세션을 끊는다 (그 세션의 Access 토큰도 바로 401) |
| 비밀번호 | BCrypt. 8~64자, 영문과 숫자를 모두 포함 |

## 6. 엔드포인트

### 인증

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/auth/signup` | `{ email, password, name, inviteCode? }` | 201 `AuthResponse`. `inviteCode`는 친구의 초대 코드(선택, 20자까지, 25 문서). 없는 코드여도 가입은 되고 가입 이력에 `UNKNOWN_CODE`로 남는다 |
| POST | `/auth/login` | `{ email, password }` | 200 `AuthResponse`. **관리자 사이트 로그인(`X-Wearly-Client: admin`)에서 2단계 인증이 필요하면** 200 `MfaChallenge`(아래) |
| POST | `/auth/refresh` | `{ refreshToken }` | 200 `AuthResponse` |
| POST | `/auth/logout` | `{ refreshToken }` | 204 (없는 토큰이어도 204) |
| POST | `/auth/mfa/setup` | `{ mfaToken }` | 200 `{ secret, otpauthUrl }`. 관리자 2단계 인증을 아직 등록하지 않았을 때 (22 문서) |
| POST | `/auth/mfa/verify` | `{ mfaToken, code }` 또는 `{ mfaToken, recoveryCode }` | 200 `AuthResponse` + `recoveryCodes?`(처음 등록할 때만 10개)·`recoveryCodesLeft` |
| GET | `/auth/sso/providers` | — | 200 `{ enabled, providers: [{ id, displayName }] }`. SSO가 꺼져 있으면 `{ enabled: false, providers: [] }`. 관리자 로그인 화면의 회사 계정 버튼 (22 문서 4.2) |
| GET | `/auth/sso/{provider}/start?redirect=` | — | 302 IdP 로그인. `redirect`는 관리자 사이트 주소(공급자 `redirect-allowlist`에 있어야 함, 아니면 400). state 쿠키 `wearly_sso_state`를 심는다 |
| GET | `/auth/sso/{provider}/callback` | IdP가 붙이는 `code`·`state`·`error`·`iss` | 302 관리자 사이트 `…?code=<교환 코드>` 또는 `…?error=<오류 코드>&status=…&traceId=…` |
| POST | `/auth/sso/exchange` | `{ code }` | 200 `AuthResponse` 또는 `MfaChallenge`(`/auth/login`과 같은 모양). 교환 코드는 1분·1회, 아니면 401 `SSO_EXCHANGE_INVALID`, 관리자 그룹이 아니게 됐으면 403 `SSO_NOT_ADMIN` |
| GET | `/auth/sso/{provider}/logout?redirect=` | — | 302 IdP 로그아웃(없는 IdP면 `redirect`로 바로) |

```ts
interface AuthResponse {
  accessToken: string;
  accessTokenExpiresIn: number;  // 초
  refreshToken: string;
  refreshTokenExpiresIn: number; // 초
  user: UserDto;
}
interface UserDto { id: string; email: string | null; name: string; role: 'USER' | 'ADMIN'; createdAt: string } // role은 2차
```

```ts
// 관리자 2단계 인증 대기 (22 문서 4.1.4). 앱(일반 회원)은 받지 않는다
interface MfaChallenge {
  mfa: { required: true; enrolled: boolean };   // enrolled=false면 /auth/mfa/setup 먼저, true면 바로 /auth/mfa/verify
  mfaToken: string;                             // 5분짜리 대기 토큰. 관리자 API에는 쓸 수 없다
  mfaTokenExpiresIn: number;                    // 300
}
```

SSO API 5개(`/auth/sso/**`)는 로그인 없이 부르는 공개 경로다. SSO가 꺼져 있으면(기본) `providers`만 200 빈 목록이고 나머지는 404 `NOT_FOUND`. 흐름·콜백 확인·계정 연결·설정은 [22](22_관리자_계정_관리.md) 4.2. 토큰은 URL에 싣지 않고 교환 코드만 싣는다.

`name`은 1~20자. `email`은 소문자로 바꿔 저장한다. 대기 토큰이 없거나 만료되면 401 `MFA_TOKEN_INVALID`, 코드가 틀리면 400 `MFA_CODE_INVALID`, 5번 틀리면 429 `MFA_LOCKED`. 일반 회원과 `X-Wearly-Client`가 `admin`이 아닌 로그인은 늘 `AuthResponse`다.

### 회원·프로필

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/users/me` | — | `UserDto` |
| DELETE | `/users/me` | — | 204. 회원과 모든 하위 데이터 삭제, 로그인 세션 전부 삭제(Access·Refresh 모두 401) |
| GET | `/users/me/profile` | — | `ProfileDto`. 저장 전이면 기본값과 `onboarded: false` |
| PUT | `/users/me/profile` | `ProfileDto` | `ProfileDto` |

```ts
// 앱 Profile과 같은 모양 + onboarded
interface ProfileDto {
  name: string;
  dressCodes: ('CASUAL' | 'BIZ_CASUAL' | 'FORMAL')[];
  moods: StyleTag[];
  colors: ColorKey[];
  workType: 'WEEKDAY' | 'WEEKEND' | 'CUSTOM';
  workDays: number[];          // 0=일 ~ 6=토
  workRule: boolean;
  offRule: boolean;
  notify: { morning: boolean; evening: boolean; dMinus1: boolean };
  onboarded: boolean;
  // 나의 정보 (이슈 #5, 모두 선택). PUT은 전체 교체라 빠지거나 null이면 비운다
  ageRange?: string | null;       // AGE_RANGE 코드 (AGE_20_EARLY ... AGE_50_PLUS)
  bodyType?: string | null;       // BODY_TYPE 코드 (STRAIGHT, WAVE, NATURAL, UNKNOWN)
  heightCm?: number | null;       // 130~210
  fitPreference?: string | null;  // FIT 코드 (SLIM, REGULAR, OVERSIZED)
}
```

기본값은 앱 `DEFAULT_PROFILE`(`mobile/src/data/seed.ts`)과 같게 한다. `PUT`의 `name`은 `tb_user.name`도 함께 바꾼다. 나의 정보 네 칸은 코드 테이블로 검증한다(활성 코드만, 틀리면 400 `VALIDATION_FAILED`, `heightCm`는 130~210이 아니면 `OUT_OF_RANGE`). 코디 추천 AI의 사용자 메시지에 "회원 정보" 한 줄로 들어간다 (09 문서 3장). 코드 값과 체형 설명은 06 문서.

### 친구 초대 (25 문서)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/users/me/invite` | — | 200 `{ inviteCode, invitedCount }`. 코드가 없으면 만든다(처음 한 번). `inviteCode`는 8자(화면에는 `XXXX-XXXX`) |
| POST | `/users/me/invite/shares` | `{ channel }` | 204. 초대한 이력. `channel`은 `SHARE`(공유 시트)·`SMS`·`COPY`, 틀리면 400 `VALIDATION_FAILED`(`channel` `NOT_ALLOWED`). 같은 방법은 60초에 한 번, 회원당 하루 30건까지만 기록하고 넘으면 **기록 없이 204** (25 문서 6장) |
| GET | `/users/me/invite/friends` | — | 200 `[{ name, joinedAt }]`. 내 코드로 가입한 친구, 최근 가입순, 최대 100명 |

로그인 필요. 관리자 이력 API(`/admin/invites/signups`·`/admin/invites/shares`)는 07 문서 4장.

### 옷장

| 메서드 | 경로 | 요청 | 응답 | 구현 |
|---|---|---|---|---|
| GET | `/wardrobe/items` | 쿼리 `category`, `status`, `color`, `q`(이름·브랜드 부분 일치), `sort`(`recent` 기본 · `worn` · `name`), `retired`(`false` 기본 = 사용 중인 옷만 · `true` = 사용 중지한 옷만 · `all`, 그 밖은 400) | `{ items: ItemDto[] }` | MyBatis 동적 SQL |
| GET | `/wardrobe/items/{id}` | — | `ItemDto` | JPA |
| POST | `/wardrobe/items` | `ItemInput` | 201 `ItemDto` | JPA |
| POST | `/wardrobe/items/bulk` | `{ items: ItemInput[] }` (최대 100) | 201 `{ items: ItemDto[] }` 요청 순서대로 | JPA |
| PATCH | `/wardrobe/items/{id}` | `Partial<ItemInput>` | `ItemDto` | JPA |
| DELETE | `/wardrobe/items/{id}` | — | 204. **완전 삭제** (13 문서) | JPA |
| POST | `/wardrobe/items/{id}/retire` | `{ reason, memo? }` | `ItemDto`. 이미 사용 중지면 이유·메모만 바꾸고 시각은 그대로 | JPA |
| POST | `/wardrobe/items/{id}/restore` | — | `ItemDto`. 사용 중이면 그대로 200 | JPA |
| GET | `/wardrobe/stats` | — | `WardrobeStats` | MyBatis 집계 |
| POST | `/wardrobe/items/analyze` | multipart, 파트 이름 `image` (jpeg·png·webp·heic, 10MB). 또는 JSON `{ imageId }`(내 READY 이미지, 18 문서) | `AnalyzeResponse` | AI 공급자 |

**옷 사진** (2026-10-18, 계약 18 문서): 등록·수정(bulk 항목 포함) 요청에 `imageId?: string | null`을 더했다. PATCH에서 `null`이면 사진 빼기, 필드를 빼면 그대로다. 내 READY 이미지만 붙일 수 있고, PENDING은 400 `IMAGE_NOT_UPLOADED`, 다른 아이템에 붙은 것은 409 `IMAGE_IN_USE`, 없는·남의 것은 404. 사진을 바꾸면 예전 사진은 버려진다. 아이템 응답(목록·상세·등록·수정·retire·restore)에는 `image`가 있다.

### 옷 사진 (18 문서)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/images/upload-url` | `{ purpose: 'ITEM', contentType: 'image/jpeg' \| 'image/png', size }` | 200 `UploadUrlResponse` (입력 오류 400 `VALIDATION_FAILED`, 한도 429 `IMAGE_UPLOAD_LIMIT`) |
| POST | `/images/{imageId}/complete` | — | 200 `ImageCompleteResponse` (400 `IMAGE_INVALID`·`IMAGE_NOT_UPLOADED`, 남의·없는 이미지 404). 이미 READY면 같은 모양으로 새 서명 URL |

```ts
interface UploadUrlResponse {
  imageId: string;            // UUID
  uploadUrl: string;          // 저장소에 바로 PUT하는 서명 URL (기본 10분)
  method: 'PUT';
  headers: Record<string, string>;  // { 'Content-Type': ... } 그대로 붙여 보낸다
  expiresAt: string;
}
interface ImageRef { imageId: string; url: string; thumbUrl: string; expiresAt: string }  // url·thumbUrl = GET 서명 URL (기본 1시간)
interface ImageCompleteResponse extends ImageRef { width: number; height: number }       // 저장한 original의 크기
```

앱은 서명 URL로 저장소에 직접 올리므로 `put`에는 토큰을 붙이지 않는다. 서명 URL은 받을 때마다 달라지니 `imageId`로 캐시한다. 처리 규칙(EXIF 제거·축소)과 오류 코드는 18 문서 6~7장.

**사용하지 않는 옷** (6차, 계약 13 문서): `retire`의 `reason`은 활성 `RETIRE_REASON` 코드, `memo`는 앞뒤 공백을 지운 뒤 200자 이하(빈 메모는 `null`로 저장). 틀리면 400 `VALIDATION_FAILED`(`reason`·`memo`), 남의 옷·없는 옷은 입력보다 먼저 404. 사용 중지는 보유 상태(`status`)와 별개이고, 사용 중지한 옷도 단건 조회·PATCH·완전 삭제가 된다. 통계는 사용 중인 옷만 세고 `retired`·`retiredByReason`을 따로 준다. AI 코디 추천 후보에서 빠지며, 고정(`context.fixed`)·중심(`context.anchorItemId`)으로 주면 400 `VALIDATION_FAILED`(필드 오류 `ITEM_RETIRED`). 코드 삭제 가드(`CODE_IN_USE`)는 사용 중지한 옷도 센다.

2차부터 `Category`·`SubType`·`ColorKey`·`Pattern`·`Season`·`StyleTag`·보유 상태는 **코드 문자열**이다. 허용 값·검증 규칙(새 값은 활성 코드만, PATCH에서 안 바꾼 비활성 값은 통과, subType의 parent = category)은 06 문서 4장을 따른다. `WardrobeStats.byCategory`·`byStatus`의 키는 활성 코드 전부 + 아이템이 있는 비활성 코드다.

```ts
// 앱 WardrobeItem에서 imageUri를 뺀 모양. 서버에 올린 사진은 image (18 문서). 서버가 없는 게스트 사진은 앱이 기기에 둔다.
interface ItemDto {
  id: string;
  name: string;
  category: Category;
  subType: SubType;
  color: ColorKey;             // 주 색상. 늘 colors[0]과 같다 (예전 앱 호환)
  colors: ColorKey[];          // 이슈 #4: 1~3개, 첫 번째가 주 색상, 중복 없음
  pattern: Pattern;
  seasons: Season[];           // EARLY_SPRING, SPRING, EARLY_SUMMER, SUMMER, EARLY_FALL, FALL, WINTER
  styles: StyleTag[];
  formality: 1 | 2 | 3;
  status: 'OWNED' | 'LAUNDRY' | 'REPAIR';
  brand?: string | null;
  wornCount: number;
  lastWornAt?: string | null;  // YYYY-MM-DD
  purchasedAt: string;          // 이슈 #4: 구입일 YYYY-MM-DD. 늘 값이 있다
  purchasedAtEstimated: boolean; // true면 사용자가 모른다고 해서 등록일(서울)을 넣은 값
  createdAt: string;
  retiredAt: string | null;     // 6차: 사용 중지 시각 (ISO). null이면 사용 중
  retireReason: string | null;  // RETIRE_REASON 코드
  retireMemo: string | null;
  image: ImageRef | null;       // 사진 저장: 서버에 저장한 대표 사진, 없으면 null
}
type ItemInput = Omit<ItemDto, 'id' | 'createdAt' | 'retiredAt' | 'retireReason' | 'retireMemo' | 'image' | 'color' | 'colors' | 'purchasedAt' | 'purchasedAtEstimated'> & {
  colors?: ColorKey[];           // 1~3개. 또는 예전 color 하나. 둘 다 오면 colors를 쓰고, 둘 다 없으면 colors 필수 오류
  color?: ColorKey;
  purchasedAt?: string | null;   // 1990-01-01 ~ 오늘. 비우면 등록일(서울)로 넣고 purchasedAtEstimated=true
  wornCount?: number; lastWornAt?: string | null; imageId?: string | null;
};

// 이슈 #4 색·구입일 규칙
//  - colors는 1~3개 (4개 이상 400 colors TOO_MANY{max:3}), 중복 400 DUPLICATE, 각 값은 활성 COLOR 코드.
//  - PATCH에서 colors를 보내면 통째로 바꾼다. 예전 color만 보내면 주 색상만 바꾸고 보조 색은 그대로 둔다.
//  - PATCH purchasedAt: 날짜면 estimated=false, null이면 등록일로 되돌리고 estimated=true, 보내지 않으면 그대로.
//  - 범위 밖 구입일은 400 purchasedAt OUT_OF_RANGE{min:'1990-01-01', max: 오늘}.
//  - 목록 color 필터는 보조 색까지 맞고, 통계 byColor는 주 색상 기준, 코드 사용 수(CODE_IN_USE)는 보조 색도 센다.

// retired·retiredByReason 말고는 사용 중인 옷만 센다 (6차)
interface WardrobeStats {
  total: number;
  byCategory: Record<Category, number>;
  byColor: { color: ColorKey; count: number }[];   // 많은 순
  byStyle: { style: StyleTag; count: number }[];   // 많은 순
  byStatus: Record<'OWNED' | 'LAUNDRY' | 'REPAIR', number>;
  wornLast30Days: number;     // 최근 30일 안에 입은 아이템 수
  utilization30d: number;     // wornLast30Days / total, 0~1, 소수 둘째 자리
  sleeping: string[];         // 60일 넘게 안 입었거나 한 번도 안 입은 아이템 id (최대 10)
  retired: number;            // 6차: 사용 중지한 옷 수
  retiredByReason: { reason: string; count: number }[];  // 이유별, 많은 순 (같으면 코드 순)
}

interface AnalyzeResponse {
  suggestion: {
    name: string;
    category: Category;
    subType: SubType;
    color: ColorKey;
    colors: ColorKey[];       // 이슈 #4: 눈에 띄는 색 1~3개, 넓은 순. 첫 번째는 color와 같다
    pattern: Pattern;
    seasons: Season[];
    styles: StyleTag[];
    formality: 1 | 2 | 3;
    confidence: number;       // 0~1
  };
  provider: string;           // 2차: 응답한 공급자의 provider_id ("claude", "openai", "gemini", "mock" …)
  model: string | null;
  remainingToday: number;
}
```

서버는 `category`와 `subType`의 짝이 맞는지 검증한다. 짝은 2차부터 코드 테이블의 SUB_TYPE `parent`를 따른다 (06 문서).

### 앱 설정 (6차, 계약 12 문서 3장)

| 메서드 | 경로 | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/app/config` | 공개 | — | `{ themeId, seasonMode, version }`. `ETag: "<version>"`, `Cache-Control: no-cache`, `If-None-Match`가 같으면 304 (코드 API와 같음) |
| GET | `/admin/app/theme` | ADMIN | — | `{ themeId, seasonMode, updatedAt, updatedBy }` |
| PUT | `/admin/app/theme` | ADMIN | `{ themeId?, seasonMode? }` (보낸 것만 바꾼다) | 위와 같은 모양. 변경 이력 `APP_THEME_UPDATE` (바뀐 필드만 남김) |

- `themeId`는 `nyang`·`closeta`. 그 밖(빈 값·대소문자 다름 포함)은 400 `VALIDATION_FAILED`(`themeId`).
- `seasonMode`(이슈 #6, 12 문서 7장)는 `OFF`(기본)·`AUTO`·`SPRING`·`SUMMER`·`FALL`·`WINTER`. 그 밖은 400(`seasonMode`). 서버는 모드만 저장하고 AUTO의 지금 계절은 앱이 날짜로 계산한다.
- `version`은 마지막으로 바꾼 시각(ISO-8601 UTC, 밀리초, = `updatedAt`). **바꾼 적이 없으면 `"initial"`**.
- 처음 상태에서 `updatedAt`·`updatedBy`는 `null`. `updatedBy`는 바꾼 관리자 이메일(탈퇴하면 `null`).
- 지금과 같은 테마로 PUT하면 아무것도 바꾸지 않고(시각·관리자 그대로, 이력 없음) 지금 상태를 200으로 돌려준다.
- 변경 이력: `targetType = APP_SETTING`, `targetId = theme`, `before`·`after` = `{ "themeId": ... }`. `GET /admin/audit-logs?targetType=APP_SETTING`.
- 공개 경로라 만료되거나 틀린 `Authorization` 헤더가 같이 와도 무시하고 200을 준다.

### 날씨 (17·19 문서)

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/weather?region=` | 공개 | 서버에 저장된 지역 날씨(현재 + 오늘부터 예보). ETag/304. 17 문서 5장 |
| GET | `/weather/day?region=&date=` | 공개 | 한 지역·한 날짜의 날씨. 틀린 토큰은 무시. `source` = `FORECAST`·`CLIMATE` |
| GET | `/weather/day?lat=&lon=&date=` | 로그인 | REGION 밖 좌표의 날씨(격자 캐시·회원 한도). 토큰 없으면 401 `UNAUTHORIZED` |
| GET | `/weather/places?q=` | 로그인 | 장소 검색 `{ items: [{ label, region, country, lat, lon, regionCode, parentRegionCode }] }`, 최대 10개. 전국 시·군·구(268곳)에 맞으면 `regionCode`는 `null`, 상위 시·도는 `parentRegionCode` (19 문서 4장) |

- `date`는 한국 날짜 `YYYY-MM-DD`, 오늘부터 1년 안. 응답 키 7개(`place`·`date`·`source`·`day`·`forecastUntil`·`fetchedAt`·`climate`)가 항상 있다. 좌표 응답의 `place.regionCode`는 `null`이다. 자세한 모양·검증·오류는 [19_장소별_날씨.md](19_장소별_날씨.md) 3·4장.
- 오류: 400 `VALIDATION_FAILED`(`date`·`region`·`lat`·`lon`·`q`), 401 `UNAUTHORIZED`, 429 `WEATHER_LIMIT`(`params.kind`: `ON_DEMAND`·`SEARCH`), 503 `WEATHER_UNAVAILABLE`(`params.kind: SEARCH`이면 장소 검색을 쓸 수 없음, 없으면 날씨 자체).
- 관리자 날씨 API(`/admin/weather/**`)는 17 문서 6장.

### 예정 코디 (21 문서)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/plans` | 쿼리 `from`·`to` (`YYYY-MM-DD`, 양 끝 포함, 선택) | 200 `{ items: PlanDto[] }` 날짜 → 시간대 순 |
| POST | `/plans` | `PlanInput` | 201 `PlanDto`. 같은 `clientId`의 예정이 있으면 그것을 고치고 200 |
| POST | `/plans/bulk` | `{ items: PlanInput[] }` (최대 100, `clientId` 필수) | 200 `{ items, created, updated }` 요청 순서대로, 하나라도 틀리면 하나도 저장 안 함 |
| PATCH | `/plans/{id}` | `Partial<PlanInput>` (보낸 필드만, `null`은 비움) | 200 `PlanDto` |
| DELETE | `/plans/{id}` | — | 204 |

- 모두 로그인 필요. 남의·없는 예정은 404. 회원당 500개, 넘으면 409 `PLAN_LIMIT`(`params.max`). 날짜는 오늘(한국) 앞뒤 1년.
- 응답 `PlanDto`에 `missingItemIds`(코디의 아이템 중 지금 옷장에 없는 id)가 있다. 필드·검증 표·동기화 규칙은 [21_예정_코디_저장.md](21_예정_코디_저장.md).

### 푸시 토큰 (D-1 알림, 23 문서 4장)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| PUT | `/me/push-token` | `{ token, platform, deviceId }` | 200 `{ deviceId, platform, serverPush, dayBeforeTime }`. 같은 기기면 바꾼다 |
| DELETE | `/me/push-token?deviceId=` | - | 204 (없어도 204). `deviceId`가 없으면 400 |

- 로그인 필요. `token`은 Expo 푸시 토큰 `ExponentPushToken[...]`(255자까지), `platform`은 `ios`·`android`, `deviceId`는 앱이 처음 실행할 때 만든 기기 ID(1~64자, 영문·숫자·`.` `_` `:` `-`). 틀리면 400 `VALIDATION_FAILED`(`token` `INVALID_FORMAT{format}`, `platform` `NOT_ALLOWED`, `deviceId`).
- `serverPush`가 `true`(서버 설정 `wearly.notify.push.enabled=true`이고 `provider=expo`)이면 서버가 이 기기에 D-1 알림을 보내므로 앱은 **로컬 D-1 알림을 끈다.** 기본은 `false`(토큰만 받아 둠)다. `dayBeforeTime`은 서버 D-1 작업 시각 `HH:mm`(한국 시간, 기본 `19:30`).
- 같은 토큰은 한 행만 둔다(기기에서 다른 계정으로 로그인하면 새 계정으로 옮긴다). 앱은 로그아웃 전에 지운다. 탈퇴하면 함께 지워지고, Expo가 `DeviceNotRegistered`로 답한 토큰은 서버가 지운다.

## 7. AI 분석

> 2차에서 공급자 선택이 DB 기반으로 바뀌었다. **공급자 선택·실패 처리·관리는 07 문서 3장을 따른다.** 아래는 요약이다.

- 공급자 구현: `ClaudeAiProvider`(Anthropic Java SDK), `OpenAiProvider`(Responses API, REST), `GeminiAiProvider`(generateContent, REST), `MockAiProvider`.
- `WEARLY_AI_PROVIDER` = `db`(기본, 1차 값 `claude`도 같은 뜻) | `mock`. 그 밖의 값(예전 `auto` 포함)은 시작 실패.
  - `mock`(`./gradlew bootRunMock`, 하네스·테스트)이면 언제나 목업. DB 설정은 무시한다.
  - 그 밖에는 `tb_ai_provider`에서 사용·모델·키를 모두 갖춘 공급자를 `priority` 순으로 시도하고, 실패하면 다음 공급자로 넘어간다. 실패도 각각 기록한다.
  - 키 고르기(3차, 07 문서 6장): 관리자 화면에서 넣은 키(DB, 복호화되면) → 서버 설정 키(`wearly.ai.keys.<api_key_env>` → 같은 이름 OS 환경변수) → 없음. 마스터 키(`wearly.secret-key`)가 없으면 DB 키는 보지 않고 서버 설정 키만 쓴다.
  - 쓸 수 있는 공급자가 없으면 502 `AI_UNAVAILABLE`. **목업으로 대신하지 않는다.** 서버는 이때도 시작하고 WARN을 남긴다 (1차의 "키 없으면 시작 실패"는 없어졌다).
- 세 공급자 모두 이미지와 함께 **활성 코드**로 만든 허용 값 목록(코드+한국어 라벨, 세부 종류는 종류별)과 JSON 스키마를 주고 구조화 출력으로 받는다. 허용 목록 밖 값·짝 불일치는 서버가 거부하고 다음 공급자로 넘어간다 (06 문서 4장).
- 목업은 이미지 바이트의 SHA-256으로 활성 코드 중에서 값을 정한다 (같은 사진 + 같은 코드 = 같은 결과).
- 분석 결과 `colors`(1~3개, 이슈 #4): 허용 값은 `color`와 같은 활성 COLOR 코드, 첫 번째는 `color`와 같아야 하고 중복이 없어야 한다. 어긋나면 서버가 검증에서 거부하고 다음 공급자로 넘어간다.
- **프롬프트 캐싱** (2026-10-18, `5c457da`): 사진 분석과 코디 추천의 고정 지시문(지시·허용 값 목록·점수 기준)을 요청마다 바뀌는 사용자 메시지(사진 + 짧은 요청 문장)에서 떼어 system(OpenAI `instructions`, Gemini `systemInstruction`)으로 보낸다. Claude는 이 고정 부분 끝에 `cache_control`(ephemeral, 5분)을 붙인다. 설정은 `wearly.ai.prompt-cache.enabled`(`WEARLY_AI_PROMPT_CACHE_ENABLED`, 기본 `true`). OpenAI·Gemini는 같은 앞부분을 공급자가 자동으로 캐시하므로 이 설정과 상관없다. 스키마 객체는 모두 순서가 고정된 맵으로 만들어 요청마다 같은 바이트가 나가게 한다. `tb_ai_analysis_log`의 입력 토큰은 **캐시 읽기·쓰기를 포함한 입력 전체**로 공급자마다 같게 기록한다. 호출이 성공하면 INFO 한 줄 `AI {feature} 성공 provider=... model=... ms=... input=... output=... cacheRead=... cacheWrite=...`을 남긴다(공급자가 주지 않은 값은 `null`). 실측과 DB에 캐시 토큰 칸이 아직 없는 점은 [18](18_사진_저장.md) 13장.
- 일일 한도 `WEARLY_AI_DAILY_LIMIT` (기본 10). 성공한 사진 분석(`feature='ITEM_ANALYZE'`)만 센다. 4차 AI 코디 추천은 `WEARLY_AI_OUTFIT_DAILY_LIMIT`(기본 20)로 따로 센다(`feature='OUTFIT_RECOMMEND'`, 09 문서). 관리자 시험 분석(`ADMIN_TEST`)은 세지 않는다. 집계는 `tb_ai_analysis_log`를 MyBatis로 센다 (Asia/Seoul 날짜 기준).
- multipart로 보낸 이미지는 저장하지 않는다. `{ imageId }`로 분석할 때는 서버에 저장한 original을 읽어 같은 흐름에 넣는다(18 문서 7.4). 로그에는 결과 JSON과 메타만 남긴다. 키 값은 어떤 응답·로그·변경 이력에도 내보내지 않는다 (3차: 관리자 응답의 `keyLast4`(웹 입력 키의 끝 4자)만 예외, 07 문서 6장).

## 8. 앱 API 모듈 계약 (`mobile/src/api/`)

하네스가 이 모듈을 Node에서 그대로 가져다 쓴다. 그래서 다음을 지킨다.

- **순수 TypeScript.** `react-native`, `expo-*`, `@/` 별칭을 import하지 않는다. 같은 폴더 안 상대 경로만 쓴다.
- `fetch`, `FormData`, `Blob`은 전역을 쓴다 (Node 22와 RN 모두 있음).

| 파일 | 내용 |
|---|---|
| `types.ts` | 위 DTO 타입 전부, `ApiErrorBody` |
| `errors.ts` | `class ApiError extends Error { status; code; fieldErrors; params; traceId? }`. 서버가 `message`를 안 주므로 `message` 자리에 `code`를 담고, `fieldErrors`는 `{ field, code, params?, message }`(`message` = 서버 값이 없으면 `code`), `params`는 오류 `params`(없으면 빈 객체), `traceId`는 본문 `traceId`. 화면 문구는 `src/lib/errorText.ts`의 `errorText(e)`로 만든다. 네트워크 실패는 `code: 'NETWORK'`, 서명 URL로 올리기(PUT) 실패는 앱이 만드는 `code: 'UPLOAD_FAILED'`(저장소 응답은 서버 오류 형식이 아니다) |
| `tokenStore.ts` | `interface TokenStore { get(): Promise<Tokens \| null>; set(t: Tokens): Promise<void>; clear(): Promise<void> }`, `Tokens = { accessToken, refreshToken }`, `createMemoryTokenStore()` |
| `client.ts` | `createApiClient({ baseUrl, tokenStore, onAuthLost? })` |

```ts
const api = createApiClient({ baseUrl, tokenStore, onAuthLost });
api.auth.signup({ email, password, name, inviteCode? })   // 토큰 저장까지. inviteCode는 선택 (25 문서)
api.auth.login({ email, password })          // 토큰 저장까지
api.auth.logout()                            // 서버 폐기 + 저장소 비움 (서버 실패해도 비움)
api.users.me() / api.users.deleteMe()        // deleteMe도 저장소 비움
api.profile.get() / api.profile.put(dto)
api.invite.get() / recordShare(channel) / friends()   // 친구 초대 (로그인, 25 문서)
api.wardrobe.list(filter?) / get(id) / create(input) / bulkCreate(inputs)
api.wardrobe.update(id, patch) / remove(id) / stats()
api.wardrobe.analyze(file)                   // file: Blob | { uri, name, type } (RN)
api.wardrobe.analyzeImage({ imageId })       // 서버에 올린 사진으로 분석 (18 문서)
api.images.uploadUrl(req) / put(target, body) / complete(imageId)   // 사진 올리기 3단계 (put은 서명 URL로 직접, 토큰 없음)
api.weather.day({ region } | { lat, lon }, date)   // 한 장소·한 날짜의 날씨. region은 토큰 없이, 좌표는 로그인 (19 문서)
api.weather.places(q)                              // 장소 검색 (로그인)
api.plans.list({ from?, to? }) / create(input) / bulk(inputs)   // 예정 코디 (로그인, 21 문서)
api.plans.patch(id, patch) / remove(id)
api.push.register({ token, platform, deviceId }) / remove(deviceId)   // 푸시 토큰 (로그인, D-1 알림)
```

- `TOKEN_EXPIRED`를 받으면 Refresh로 한 번 갱신하고 원래 요청을 한 번 다시 보낸다. 동시에 여러 요청이 만료돼도 갱신 요청은 **하나만** 보낸다 (single-flight).
- 갱신이 실패하면 저장소를 비우고 `onAuthLost()`를 부른 뒤 원래 오류를 던진다.

## 9. 연동 하네스 (`harness/`)

- 하네스 전용 DB `wearly_harness`(같은 컨테이너)를 매번 비우고, 백엔드를 포트 18080으로 띄운다.
- 짧은 Access 수명(`WEARLY_ACCESS_TTL=PT3S`)과 낮은 AI 한도(`WEARLY_AI_DAILY_LIMIT=3`), `WEARLY_AI_PROVIDER=mock`으로 띄워 만료·갱신·한도를 실제로 확인한다.
- 개발용 `backend/config/application-local.yml`(실제 AI 키·관리자 계정·마스터 키)은 읽지 않는다 (`WEARLY_LOCAL_CONFIG`를 없는 경로로 준다). DB는 compose의 `db`(`wearly-postgres`)를 쓴다.
- 시나리오는 `mobile/src/api/client.ts`를 그대로 import해 앱과 같은 코드로 서버를 부른다.
- 사진 저장 시나리오(47~50)는 앱 모듈을 거치지 않고 원시 HTTP로 서명 URL까지 실제로 PUT·GET한다. 하네스 전용 버킷 `wearly-images-harness`(compose의 MinIO)를 쓴다 (18 문서 2장, `harness/README.md`).
- 장소별 날씨 시나리오(51~53)는 앱 모듈을 거치지 않고 원시 HTTP로 `GET /weather/day`·`/weather/places`를 확인한다. 하네스 백엔드는 목업 날씨·목업 평년값으로 돈다 (19 문서, `harness/README.md`).
- 시나리오 54(관리자 날씨 호출 이력 `callKind` 필터)는 원시 HTTP로, 예정 코디 시나리오(55~57)도 원시 HTTP로 `/plans`를 확인한다 (21 문서, `harness/README.md`). 오류 본문 형식 검사(`checkErrorBody`)는 `message`가 없고 `traceId`가 `X-Request-Id` 헤더와 같은지 본다 (20 문서).
- 6차의 앱 설정·옷 사용 중지 시나리오(32~40)는 앱 모듈을 거치지 않고 원시 HTTP로만 서버 계약을 확인한다 (`harness/README.md`). 계절 모드(이슈 #6)는 시나리오 34.
- 관리자 2단계 인증 시나리오(45)는 지정한 관리자가 TOTP를 등록하고 대기 토큰으로 로그인하는 과정을 확인한다. `harness/run.sh`는 `WEARLY_ADMIN_MFA_REQUIRED=false`와 고정 마스터 키로 백엔드를 띄워, 시나리오가 따로 등록한 관리자 말고는 비밀번호만으로 들어온다 (22 문서).
- 관리자 SSO 시나리오(46)는 SSO가 꺼진 기본 동작만 본다: 공급자 목록은 `{ enabled: false, providers: [] }`, 시작·콜백·교환·로그아웃은 404 `NOT_FOUND`. `harness/run.sh`는 `WEARLY_SSO_ENABLED=false`로 띄운다. 켠 흐름은 백엔드 `SsoIntegrationTest`(가짜 IdP)와 로컬 Keycloak(22 문서 4.2.5)으로 확인한다.
- 나의 정보 시나리오(9), 푸시 토큰 시나리오(58)도 있다. 번호는 `./run.sh --list` 기준(지금 58개).
