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
> **4차 변경**: AI 코디 추천 `POST /api/v1/outfits/recommendations`(`V5__outfit_recommend.sql`) → [09_AI_코디_추천.md](09_AI_코디_추천.md). 백엔드 코드 스타일(Google Java Style) → 08 문서 5장.

## 1. 범위

| 포함 | 제외 (다음 단계) |
|---|---|
| 이메일·비밀번호 가입과 로그인, JWT Access 토큰, Refresh 토큰 회전 | 소셜 로그인 (카카오·애플·구글) |
| 내 정보 조회, 회원 탈퇴 | 이미지 업로드·저장 (사진은 앱 기기에만 둔다) |
| 취향·근무 패턴 프로필 조회·저장 | 코디 저장·피드백·예정일·착용 기록 API (테이블만 만든다) |
| 옷장 아이템 CRUD, 일괄 등록, 필터 목록, 통계 | 추천·진단의 서버 이전 (앱의 규칙 엔진 유지) |
| AI 사진 분석 (기본 Claude, 목업은 명시할 때만) | 소셜 토큰 검증, App Check, Redis 요청 제한 |

이메일·비밀번호 로그인은 기술 결정 D5에서 출시 범위에서 뺀 수단이다. 소셜 로그인 키를 받기 전까지 토큰 흐름을 먼저 완성하기 위한 **임시 수단**이며, 자격 증명을 `tb_user_local_credential`에 따로 두어 나중에 소셜 계정과 같은 자리에서 교체할 수 있게 한다.

## 2. 실행 환경

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.x (MyBatis 스타터가 호환되지 않으면 4.0.x) |
| Java | 21 (Gradle toolchain) |
| 빌드 | Gradle Kotlin DSL, wrapper 포함 |
| DB | `docker compose up -d db` → `wearly-postgres`, `localhost:5433`, DB·계정·비밀번호 모두 `wearly` (로컬 전용). `docker compose up -d --build`는 백엔드(8080)·관리자 사이트(5173)까지 띄운다 |
| 스키마 | Flyway가 만든다. JPA는 `ddl-auto: validate` |
| 서버 포트 | 8080 |
| 비밀 값 | `backend/config/application-local.yml` (git·docker 이미지 제외, 견본 `application-local.example.yml`, `.env` 쓰지 않음 — 08 문서 6장). `wearly.ai.keys.ANTHROPIC_API_KEY`·`OPENAI_API_KEY`·`GEMINI_API_KEY`(공급자 행의 `api_key_env` = 서버 설정 키 이름), `wearly.jwt.secret`, `wearly.admin.email`·`wearly.admin.password`(·`wearly.admin.name`), 3차 `wearly.secret-key`(웹 입력 AI 키의 마스터 키, base64 32바이트). 같은 값을 OS 환경변수(`ANTHROPIC_API_KEY`, `WEARLY_JWT_SECRET`, `WEARLY_ADMIN_EMAIL` …, `WEARLY_SECRET_KEY`)로도 줄 수 있다 |

### 데이터 접근 분담

- **JPA**: 쓰기와 단건 조회. 엔티티 생명주기·연관 삭제가 필요한 곳 (회원, 자격 증명, 로그인 이력, 프로필, 아이템 등록·수정·삭제). Refresh 토큰·로그인 세션은 7차부터 Valkey(16 문서).
- **MyBatis**: 읽기 전용 조회. 동적 필터 목록, 집계 통계, AI 일일 사용량 집계처럼 SQL이 더 읽기 쉬운 곳.
- 같은 트랜잭션 매니저를 쓴다. MyBatis 매퍼는 XML(`resources/mapper/*.xml`)로 둔다.

## 3. 테이블 (Flyway `V1__init.sql`, 2차 `V2__codes.sql`·`V3__admin_ai.sql`, 3차 `V4__ai_provider_secret.sql`, 4차 `V5__outfit_recommend.sql`)

2차에서 더한 테이블: `tb_code_group`·`tb_code`(06 문서 2장), `tb_ai_provider`·`tb_admin_audit_log`, `tb_user.role`(07 문서 2·3장).
3차에서 더한 테이블: `tb_ai_provider_secret`(웹 입력 AI 키의 AES-256-GCM 암호문, 07 문서 6장).
4차: `tb_ai_analysis_log.feature` CHECK에 `OUTFIT_RECOMMEND`(AI 코디 추천, 이미지 칸은 NULL)를 더하고, 한도 집계 인덱스를 `(user_id, feature, created_at) WHERE status = 'SUCCESS'`로 바꿨다 (09 문서).
7차(2026-10-17): `V10__auth_session_valkey.sql` — `tb_refresh_token` 삭제(토큰·세션은 Valkey), `tb_auth_event`(로그인 이력: LOGIN·LOGOUT·REFRESH_REUSED·FORCED_LOGOUT, 16 문서 3장) 추가.
6차(2026-10-17): `V8__app_setting.sql` — `tb_app_setting`(앱 전체 설정, 초기값 `theme` = `{"themeId":"nyang"}`, 12 문서 3장). `V9__wardrobe_item_retire.sql` — `tb_wardrobe_item`에 `retired_at`·`retire_reason`·`retire_memo`, 부분 인덱스 `ix_wardrobe_item_in_use (user_id) WHERE retired_at IS NULL`, CHECK `ck_wardrobe_item_retired`(사용 중지면 시각·이유가 함께 있다), 코드 그룹 `RETIRE_REASON` 5개(13 문서 2장).

모든 이름은 소문자 snake_case, 접두사 `tb_`. PK는 `BIGINT GENERATED ALWAYS AS IDENTITY`. 시각은 `TIMESTAMPTZ`, 날짜는 `DATE`. 회원 삭제 시 모든 하위 데이터는 `ON DELETE CASCADE`로 지운다.

| 테이블 | 용도 | 1차 API 사용 |
|---|---|---|
| `tb_user` | 회원 (`email` 참고용·nullable, `name`, `status`) | O |
| `tb_user_local_credential` | 이메일·비밀번호 (임시). `email` 소문자 UNIQUE, `password_hash` | O |
| `tb_user_social` | 소셜 계정 (D5 설계 그대로, `UNIQUE(provider, provider_user_id)`) | 테이블만 |
| ~~`tb_refresh_token`~~ | 7차에 삭제. Refresh 토큰·세션은 Valkey `wearly:rt:*`·`wearly:session:*` (16 문서 3장) | — |
| `tb_auth_event` | 7차. 로그인 이력: `type`(LOGIN·LOGOUT·REFRESH_REUSED·FORCED_LOGOUT), `session_id`, `client`, `ip`, `user_agent` | O (16 문서) |
| `tb_user_profile` | 앱 `Profile` + `onboarded`. 배열은 `TEXT[]`/`SMALLINT[]`, 알림은 `JSONB` | O |
| `tb_user_style` | 취향 학습 점수 (`style_code`, `preference_score`) | 테이블만 |
| `tb_wardrobe_item` | 옷장 아이템. `seasons`·`styles`는 `TEXT[]`, `ai_suggestion JSONB`. 사용 중지는 `retired_at`(NULL이면 사용 중)·`retire_reason`·`retire_memo` | O |
| `tb_item_image` | 아이템 이미지 URL | 테이블만 |
| `tb_outfit` / `tb_outfit_item` | 코디 헤더(점수·`breakdown JSONB`·`context JSONB`) / 구성(`item_role` = 슬롯) | 테이블만 |
| `tb_outfit_feedback` | LIKE·DISLIKE, `reasons JSONB` | 테이블만 |
| `tb_planned_outfit` | 예정일 코디 | 테이블만 |
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

```json
{ "status": 401, "code": "TOKEN_EXPIRED", "message": "로그인이 만료됐어요.", "fieldErrors": [] }
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력 검증 실패. `fieldErrors: [{ field, message }]` |
| 401 | `UNAUTHORIZED` | 토큰 없음 |
| 401 | `TOKEN_EXPIRED` | Access 토큰 만료 → 앱이 자동 갱신 |
| 401 | `TOKEN_INVALID` | 서명·형식 오류, 탈퇴·정지한 회원, `sid`가 없는 옛 토큰, 세션이 끊김(로그아웃·관리자 강제 로그아웃·정지·탈퇴·재사용 감지, 7차) |
| 401 | `INVALID_CREDENTIALS` | 이메일 또는 비밀번호 불일치 (어느 쪽인지 알리지 않음) |
| 401 | `REFRESH_INVALID` | Refresh 토큰 없음·만료·폐기(세션이 끊김) |
| 401 | `REFRESH_REUSED` | 이미 회전된 Refresh 토큰 재사용 → 그 세션 전체 끊기 |
| 403 | `FORBIDDEN` | 권한 없음 (관리자 API에 일반 회원 등) |
| 403 | `ACCOUNT_SUSPENDED` | 정지된 회원의 로그인 (비밀번호가 맞을 때만, 07 문서 2장) |
| 403 | `REAUTH_FAILED` | AI 키 넣기·지우기에서 관리자 비밀번호(`currentPassword`) 재확인 실패 (07 문서 6장) |
| 404 | `NOT_FOUND` | 없거나 남의 리소스 (남의 것도 404) |
| 409 | `EMAIL_TAKEN` | 가입 이메일 중복 |
| 409 | `CODE_EXISTS` · `CODE_IN_USE` · `PROVIDER_EXISTS` | 관리자 코드 중복·사용 중 삭제, AI 공급자 ID 중복 (07 문서 4장) |
| 409 | `SECRET_KEY_MISSING` | 서버에 마스터 키(`wearly.secret-key` / 환경변수 `WEARLY_SECRET_KEY`)가 없어 AI 키를 웹에서 넣을 수 없음 (07 문서 6장) |
| 400 | `API_KEY_INVALID` | 저장 전 키 확인(각 사 모델 목록 조회) 실패 (07 문서 6장) |
| 400 | `WARDROBE_INSUFFICIENT` | AI 코디 추천에 쓸 옷이 모자람. `message`에 모자란 칸 코드 (예: `BOTTOM 또는 DRESS, SHOES`) (09 문서) |
| 413 | `IMAGE_TOO_LARGE` | 분석 이미지 10MB 초과 |
| 429 | `AI_LIMIT_EXCEEDED` | AI 사진 분석·AI 코디 추천 일일 한도 초과 (기능별로 따로 센다) |
| 502 | `AI_UNAVAILABLE` | AI 공급자 오류 |

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
| POST | `/auth/signup` | `{ email, password, name }` | 201 `AuthResponse` |
| POST | `/auth/login` | `{ email, password }` | 200 `AuthResponse` |
| POST | `/auth/refresh` | `{ refreshToken }` | 200 `AuthResponse` |
| POST | `/auth/logout` | `{ refreshToken }` | 204 (없는 토큰이어도 204) |

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

`name`은 1~20자. `email`은 소문자로 바꿔 저장한다.

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
}
```

기본값은 앱 `DEFAULT_PROFILE`(`mobile/src/data/seed.ts`)과 같게 한다. `PUT`의 `name`은 `tb_user.name`도 함께 바꾼다.

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
| POST | `/wardrobe/items/analyze` | multipart, 파트 이름 `image` (jpeg·png·webp·heic, 10MB) | `AnalyzeResponse` | AI 공급자 |

**사용하지 않는 옷** (6차, 계약 13 문서): `retire`의 `reason`은 활성 `RETIRE_REASON` 코드, `memo`는 앞뒤 공백을 지운 뒤 200자 이하(빈 메모는 `null`로 저장). 틀리면 400 `VALIDATION_FAILED`(`reason`·`memo`), 남의 옷·없는 옷은 입력보다 먼저 404. 사용 중지는 보유 상태(`status`)와 별개이고, 사용 중지한 옷도 단건 조회·PATCH·완전 삭제가 된다. 통계는 사용 중인 옷만 세고 `retired`·`retiredByReason`을 따로 준다. AI 코디 추천 후보에서 빠지며, 고정(`context.fixed`)·중심(`context.anchorItemId`)으로 주면 400 `VALIDATION_FAILED`("사용하지 않는 옷이에요."). 코드 삭제 가드(`CODE_IN_USE`)는 사용 중지한 옷도 센다.

2차부터 `Category`·`SubType`·`ColorKey`·`Pattern`·`Season`·`StyleTag`·보유 상태는 **코드 문자열**이다. 허용 값·검증 규칙(새 값은 활성 코드만, PATCH에서 안 바꾼 비활성 값은 통과, subType의 parent = category)은 06 문서 4장을 따른다. `WardrobeStats.byCategory`·`byStatus`의 키는 활성 코드 전부 + 아이템이 있는 비활성 코드다.

```ts
// 앱 WardrobeItem에서 imageUri를 뺀 모양. 사진은 앱이 아이템 id별로 기기에 따로 보관한다.
interface ItemDto {
  id: string;
  name: string;
  category: Category;
  subType: SubType;
  color: ColorKey;
  pattern: Pattern;
  seasons: Season[];
  styles: StyleTag[];
  formality: 1 | 2 | 3;
  status: 'OWNED' | 'LAUNDRY' | 'REPAIR';
  brand?: string | null;
  wornCount: number;
  lastWornAt?: string | null;  // YYYY-MM-DD
  createdAt: string;
  retiredAt: string | null;     // 6차: 사용 중지 시각 (ISO). null이면 사용 중
  retireReason: string | null;  // RETIRE_REASON 코드
  retireMemo: string | null;
}
type ItemInput = Omit<ItemDto, 'id' | 'createdAt' | 'retiredAt' | 'retireReason' | 'retireMemo'> & { wornCount?: number; lastWornAt?: string | null };

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
| GET | `/app/config` | 공개 | — | `{ themeId, version }`. `ETag: "<version>"`, `Cache-Control: no-cache`, `If-None-Match`가 같으면 304 (코드 API와 같음) |
| GET | `/admin/app/theme` | ADMIN | — | `{ themeId, updatedAt, updatedBy }` |
| PUT | `/admin/app/theme` | ADMIN | `{ themeId }` | 위와 같은 모양. 변경 이력 `APP_THEME_UPDATE` |

- `themeId`는 `nyang`·`closeta`. 그 밖(빈 값·대소문자 다름 포함)은 400 `VALIDATION_FAILED`(`themeId`).
- `version`은 마지막으로 바꾼 시각(ISO-8601 UTC, 밀리초, = `updatedAt`). **바꾼 적이 없으면 `"initial"`**.
- 처음 상태에서 `updatedAt`·`updatedBy`는 `null`. `updatedBy`는 바꾼 관리자 이메일(탈퇴하면 `null`).
- 지금과 같은 테마로 PUT하면 아무것도 바꾸지 않고(시각·관리자 그대로, 이력 없음) 지금 상태를 200으로 돌려준다.
- 변경 이력: `targetType = APP_SETTING`, `targetId = theme`, `before`·`after` = `{ "themeId": ... }`. `GET /admin/audit-logs?targetType=APP_SETTING`.
- 공개 경로라 만료되거나 틀린 `Authorization` 헤더가 같이 와도 무시하고 200을 준다.

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
- 일일 한도 `WEARLY_AI_DAILY_LIMIT` (기본 10). 성공한 사진 분석(`feature='ITEM_ANALYZE'`)만 센다. 4차 AI 코디 추천은 `WEARLY_AI_OUTFIT_DAILY_LIMIT`(기본 20)로 따로 센다(`feature='OUTFIT_RECOMMEND'`, 09 문서). 관리자 시험 분석(`ADMIN_TEST`)은 세지 않는다. 집계는 `tb_ai_analysis_log`를 MyBatis로 센다 (Asia/Seoul 날짜 기준).
- 이미지는 저장하지 않는다. 로그에는 결과 JSON과 메타만 남긴다. 키 값은 어떤 응답·로그·변경 이력에도 내보내지 않는다 (3차: 관리자 응답의 `keyLast4`(웹 입력 키의 끝 4자)만 예외, 07 문서 6장).

## 8. 앱 API 모듈 계약 (`mobile/src/api/`)

하네스가 이 모듈을 Node에서 그대로 가져다 쓴다. 그래서 다음을 지킨다.

- **순수 TypeScript.** `react-native`, `expo-*`, `@/` 별칭을 import하지 않는다. 같은 폴더 안 상대 경로만 쓴다.
- `fetch`, `FormData`, `Blob`은 전역을 쓴다 (Node 22와 RN 모두 있음).

| 파일 | 내용 |
|---|---|
| `types.ts` | 위 DTO 타입 전부, `ApiErrorBody` |
| `errors.ts` | `class ApiError extends Error { status; code; fieldErrors }`, 네트워크 실패는 `code: 'NETWORK'` |
| `tokenStore.ts` | `interface TokenStore { get(): Promise<Tokens \| null>; set(t: Tokens): Promise<void>; clear(): Promise<void> }`, `Tokens = { accessToken, refreshToken }`, `createMemoryTokenStore()` |
| `client.ts` | `createApiClient({ baseUrl, tokenStore, onAuthLost? })` |

```ts
const api = createApiClient({ baseUrl, tokenStore, onAuthLost });
api.auth.signup({ email, password, name })   // 토큰 저장까지
api.auth.login({ email, password })          // 토큰 저장까지
api.auth.logout()                            // 서버 폐기 + 저장소 비움 (서버 실패해도 비움)
api.users.me() / api.users.deleteMe()        // deleteMe도 저장소 비움
api.profile.get() / api.profile.put(dto)
api.wardrobe.list(filter?) / get(id) / create(input) / bulkCreate(inputs)
api.wardrobe.update(id, patch) / remove(id) / stats()
api.wardrobe.analyze(file)                   // file: Blob | { uri, name, type } (RN)
```

- `TOKEN_EXPIRED`를 받으면 Refresh로 한 번 갱신하고 원래 요청을 한 번 다시 보낸다. 동시에 여러 요청이 만료돼도 갱신 요청은 **하나만** 보낸다 (single-flight).
- 갱신이 실패하면 저장소를 비우고 `onAuthLost()`를 부른 뒤 원래 오류를 던진다.

## 9. 연동 하네스 (`harness/`)

- 하네스 전용 DB `wearly_harness`(같은 컨테이너)를 매번 비우고, 백엔드를 포트 18080으로 띄운다.
- 짧은 Access 수명(`WEARLY_ACCESS_TTL=PT3S`)과 낮은 AI 한도(`WEARLY_AI_DAILY_LIMIT=3`), `WEARLY_AI_PROVIDER=mock`으로 띄워 만료·갱신·한도를 실제로 확인한다.
- 개발용 `backend/config/application-local.yml`(실제 AI 키·관리자 계정·마스터 키)은 읽지 않는다 (`WEARLY_LOCAL_CONFIG`를 없는 경로로 준다). DB는 compose의 `db`(`wearly-postgres`)를 쓴다.
- 시나리오는 `mobile/src/api/client.ts`를 그대로 import해 앱과 같은 코드로 서버를 부른다.
- 6차의 앱 설정·옷 사용 중지 시나리오(30~37)는 앱 모듈을 거치지 않고 원시 HTTP로만 서버 계약을 확인한다 (`harness/README.md`).
