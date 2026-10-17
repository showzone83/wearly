# 05. 백엔드 API 계약 (1차: 토큰 로그인 · 옷장 · AI 분석)

> 백엔드(`backend/`), 앱(`mobile/src/api/`), 연동 하네스(`harness/`)가 함께 따르는 계약이다. 셋 중 하나를 바꾸면 이 문서부터 고친다.

## 1. 범위

| 포함 | 제외 (다음 단계) |
|---|---|
| 이메일·비밀번호 가입과 로그인, JWT Access 토큰, Refresh 토큰 회전 | 소셜 로그인 (카카오·애플·구글) |
| 내 정보 조회, 회원 탈퇴 | 이미지 업로드·저장 (사진은 앱 기기에만 둔다) |
| 취향·근무 패턴 프로필 조회·저장 | 코디 저장·피드백·예정일·착용 기록 API (테이블만 만든다) |
| 옷장 아이템 CRUD, 일괄 등록, 필터 목록, 통계 | 추천·진단의 서버 이전 (앱의 규칙 엔진 유지) |
| AI 사진 분석 (목업 공급자 기본, 키가 있으면 Claude) | 소셜 토큰 검증, App Check, Redis 요청 제한 |

이메일·비밀번호 로그인은 기술 결정 D5에서 출시 범위에서 뺀 수단이다. 소셜 로그인 키를 받기 전까지 토큰 흐름을 먼저 완성하기 위한 **임시 수단**이며, 자격 증명을 `tb_user_local_credential`에 따로 두어 나중에 소셜 계정과 같은 자리에서 교체할 수 있게 한다.

## 2. 실행 환경

| 항목 | 값 |
|---|---|
| Spring Boot | 4.1.x (MyBatis 스타터가 호환되지 않으면 4.0.x) |
| Java | 21 (Gradle toolchain) |
| 빌드 | Gradle Kotlin DSL, wrapper 포함 |
| DB | `docker compose up -d` → `wearly-postgres`, `localhost:5433`, DB·계정·비밀번호 모두 `wearly` (로컬 전용) |
| 스키마 | Flyway가 만든다. JPA는 `ddl-auto: validate` |
| 서버 포트 | 8080 |
| 비밀 값 | `backend/.env` (git 제외). `ANTHROPIC_API_KEY`, `WEARLY_JWT_SECRET` |

### 데이터 접근 분담

- **JPA**: 쓰기와 단건 조회. 엔티티 생명주기·연관 삭제가 필요한 곳 (회원, 자격 증명, Refresh 토큰, 프로필, 아이템 등록·수정·삭제).
- **MyBatis**: 읽기 전용 조회. 동적 필터 목록, 집계 통계, AI 일일 사용량 집계처럼 SQL이 더 읽기 쉬운 곳.
- 같은 트랜잭션 매니저를 쓴다. MyBatis 매퍼는 XML(`resources/mapper/*.xml`)로 둔다.

## 3. 테이블 (Flyway `V1__init.sql`)

모든 이름은 소문자 snake_case, 접두사 `tb_`. PK는 `BIGINT GENERATED ALWAYS AS IDENTITY`. 시각은 `TIMESTAMPTZ`, 날짜는 `DATE`. 회원 삭제 시 모든 하위 데이터는 `ON DELETE CASCADE`로 지운다.

| 테이블 | 용도 | 1차 API 사용 |
|---|---|---|
| `tb_user` | 회원 (`email` 참고용·nullable, `name`, `status`) | O |
| `tb_user_local_credential` | 이메일·비밀번호 (임시). `email` 소문자 UNIQUE, `password_hash` | O |
| `tb_user_social` | 소셜 계정 (D5 설계 그대로, `UNIQUE(provider, provider_user_id)`) | 테이블만 |
| `tb_refresh_token` | `token_hash`(SHA-256 hex) UNIQUE, `family_id` UUID, `expires_at`, `revoked_at`, `replaced_by_id` | O |
| `tb_user_profile` | 앱 `Profile` + `onboarded`. 배열은 `TEXT[]`/`SMALLINT[]`, 알림은 `JSONB` | O |
| `tb_user_style` | 취향 학습 점수 (`style_code`, `preference_score`) | 테이블만 |
| `tb_wardrobe_item` | 옷장 아이템. `seasons`·`styles`는 `TEXT[]`, `ai_suggestion JSONB` | O |
| `tb_item_image` | 아이템 이미지 URL | 테이블만 |
| `tb_outfit` / `tb_outfit_item` | 코디 헤더(점수·`breakdown JSONB`·`context JSONB`) / 구성(`item_role` = 슬롯) | 테이블만 |
| `tb_outfit_feedback` | LIKE·DISLIKE, `reasons JSONB` | 테이블만 |
| `tb_planned_outfit` | 예정일 코디 | 테이블만 |
| `tb_daily_look` / `tb_daily_look_item` | 착용 기록, `UNIQUE(user_id, worn_date)` | 테이블만 |
| `tb_look_diagnosis` | 진단 (`axes JSONB`, `swap JSONB`) | 테이블만 |
| `tb_wardrobe_report` | 옷장 분석 리포트 (`report JSONB`) | 테이블만 |
| `tb_ai_analysis_log` | AI 호출 기록: 공급자·모델·상태·지연·토큰 수·결과. 일일 한도와 '코디당 AI 비용' 지표에 쓴다 | O |

열거형 값은 앱 `mobile/src/data/types.ts`의 문자열을 그대로 `VARCHAR`로 저장한다 (`CHECK` 제약 권장).

## 4. 공통 규칙

- 기본 경로 `/api/v1`, JSON, UTF-8. 필드는 camelCase.
- **ID는 JSON에서 문자열**이다 (`"id": "42"`). 앱의 기존 문자열 ID와 맞추고 JS 정수 한계를 피한다.
- 날짜는 `YYYY-MM-DD`, 시각은 ISO-8601 UTC (`2026-10-17T03:00:00Z`).
- 인증: `Authorization: Bearer <accessToken>`. 공개 경로는 `/api/v1/auth/**`와 `/actuator/health`뿐이다.
- CORS: `http://localhost:8081`, `http://localhost:19006`, `http://127.0.0.1:8081` 허용 (Expo 웹).

### 오류 응답

```json
{ "status": 401, "code": "TOKEN_EXPIRED", "message": "로그인이 만료됐어요.", "fieldErrors": [] }
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력 검증 실패. `fieldErrors: [{ field, message }]` |
| 401 | `UNAUTHORIZED` | 토큰 없음 |
| 401 | `TOKEN_EXPIRED` | Access 토큰 만료 → 앱이 자동 갱신 |
| 401 | `TOKEN_INVALID` | 서명·형식 오류, 탈퇴한 회원 |
| 401 | `INVALID_CREDENTIALS` | 이메일 또는 비밀번호 불일치 (어느 쪽인지 알리지 않음) |
| 401 | `REFRESH_INVALID` | Refresh 토큰 없음·만료·폐기 |
| 401 | `REFRESH_REUSED` | 이미 회전된 Refresh 토큰 재사용 → 같은 family 전체 폐기 |
| 404 | `NOT_FOUND` | 없거나 남의 리소스 (남의 것도 404) |
| 409 | `EMAIL_TAKEN` | 가입 이메일 중복 |
| 413 | `IMAGE_TOO_LARGE` | 분석 이미지 10MB 초과 |
| 429 | `AI_LIMIT_EXCEEDED` | AI 분석 일일 한도 초과 |
| 502 | `AI_UNAVAILABLE` | AI 공급자 오류 |

## 5. 토큰

| 항목 | 값 |
|---|---|
| Access | JWT HS256, `sub` = userId, `iss` = `wearly`, 기본 30분 (`WEARLY_ACCESS_TTL`, 예: `PT30M`) |
| Refresh | 32바이트 난수 base64url (JWT 아님). DB에는 SHA-256만 저장. 기본 30일 (`WEARLY_REFRESH_TTL`) |
| 회전 | `/auth/refresh`마다 새 Refresh를 주고 이전 것은 `revoked_at` + `replaced_by_id` |
| 재사용 감지 | 이미 회전된 토큰이 다시 오면 같은 `family_id` 전체 폐기, `REFRESH_REUSED` |
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
interface UserDto { id: string; email: string | null; name: string; createdAt: string }
```

`name`은 1~20자. `email`은 소문자로 바꿔 저장한다.

### 회원·프로필

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/users/me` | — | `UserDto` |
| DELETE | `/users/me` | — | 204. 회원과 모든 하위 데이터 삭제, Refresh 전부 무효 |
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
| GET | `/wardrobe/items` | 쿼리 `category`, `status`, `color`, `q`(이름·브랜드 부분 일치), `sort`(`recent` 기본 · `worn` · `name`) | `{ items: ItemDto[] }` | MyBatis 동적 SQL |
| GET | `/wardrobe/items/{id}` | — | `ItemDto` | JPA |
| POST | `/wardrobe/items` | `ItemInput` | 201 `ItemDto` | JPA |
| POST | `/wardrobe/items/bulk` | `{ items: ItemInput[] }` (최대 100) | 201 `{ items: ItemDto[] }` 요청 순서대로 | JPA |
| PATCH | `/wardrobe/items/{id}` | `Partial<ItemInput>` | `ItemDto` | JPA |
| DELETE | `/wardrobe/items/{id}` | — | 204 | JPA |
| GET | `/wardrobe/stats` | — | `WardrobeStats` | MyBatis 집계 |
| POST | `/wardrobe/items/analyze` | multipart, 파트 이름 `image` (jpeg·png·webp·heic, 10MB) | `AnalyzeResponse` | AI 공급자 |

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
}
type ItemInput = Omit<ItemDto, 'id' | 'createdAt'> & { wornCount?: number; lastWornAt?: string | null };

interface WardrobeStats {
  total: number;
  byCategory: Record<Category, number>;
  byColor: { color: ColorKey; count: number }[];   // 많은 순
  byStyle: { style: StyleTag; count: number }[];   // 많은 순
  byStatus: Record<'OWNED' | 'LAUNDRY' | 'REPAIR', number>;
  wornLast30Days: number;     // 최근 30일 안에 입은 아이템 수
  utilization30d: number;     // wornLast30Days / total, 0~1, 소수 둘째 자리
  sleeping: string[];         // 60일 넘게 안 입었거나 한 번도 안 입은 아이템 id (최대 10)
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
  provider: 'mock' | 'claude';
  model: string | null;
  remainingToday: number;
}
```

서버는 `category`와 `subType`의 짝이 맞는지 검증한다. 짝은 앱 `mobile/src/data/constants.ts`의 `SUBTYPE`을 따른다.

## 7. AI 분석

- 공급자 인터페이스 하나에 구현 둘: `MockAiProvider`(기본), `ClaudeAiProvider`.
- `WEARLY_AI_PROVIDER` = `mock` | `claude` | `auto`(기본). `auto`는 `ANTHROPIC_API_KEY`가 있으면 Claude, 없으면 목업이다.
- Claude는 이미지와 함께 허용 값 목록을 주고 **구조화된 JSON 출력**으로 받는다. 허용 목록 밖 값은 서버가 거부하고 `AI_UNAVAILABLE`로 바꾼다. 모델은 `WEARLY_AI_MODEL`로 바꿀 수 있다.
- 목업은 이미지 바이트의 SHA-256으로 값을 정한다 (같은 사진 = 같은 결과).
- 일일 한도 `WEARLY_AI_DAILY_LIMIT` (기본 10, 기획서 무료회원 1회는 운영 설정에서 정한다). 성공한 호출만 센다. 집계는 `tb_ai_analysis_log`를 MyBatis로 센다 (Asia/Seoul 날짜 기준).
- 이미지는 저장하지 않는다. 로그에는 결과 JSON과 메타만 남긴다.

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
- 시나리오는 `mobile/src/api/client.ts`를 그대로 import해 앱과 같은 코드로 서버를 부른다.
