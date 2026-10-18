# Wearly 관리자 사이트 (`admin/`)

코드(옷 속성 값)·AI 공급자·회원을 관리하는 백오피스 웹. 계약은 [doc/07_관리자_사이트.md](../doc/07_관리자_사이트.md)와 [doc/06_코드_관리.md](../doc/06_코드_관리.md)를 따른다.

- React 19 + Vite 8 + TypeScript(strict), react-router 7, TanStack Query 5. UI 라이브러리 없이 `src/styles.css` 하나.
- 앱과 같은 톤: 밝은 회색 바탕, 검정, 짙은 초록 포인트, 세리프 제목(Noto Serif KR).

## 실행

```bash
cd admin
npm install
npm run dev          # http://localhost:5173
```

백엔드(`backend/`)가 `http://localhost:8080`에서 돌고 있어야 한다 (저장소 루트에서 `docker compose up -d --build db backend`, 또는 `backend/`에서 `./gradlew bootRun`). 다른 주소면 `admin/.env.local`에 적는다 (`.env.example` 참고).

```bash
VITE_API_URL=http://localhost:8080
```

docker로 띄우기: 저장소 루트에서 `docker compose up -d --build`를 하면 DB·백엔드와 함께 관리자 사이트 컨테이너(`wearly-admin`, nginx)가 `http://localhost:5173`에 뜬다. 이미지는 `admin/Dockerfile`(빌드 컨텍스트는 저장소 루트, 앱의 `mobile/src/data` 순수 TS와 마스코트 PNG도 복사)로 만들고, API 주소는 빌드 인자 `VITE_API_URL`(기본 `http://localhost:8080`)로 번들에 들어간다. `npm run dev`와 포트(5173)가 겹치므로 개발 서버를 쓸 때는 `docker compose up -d --build db backend`만 띄운다. 코드를 고친 뒤 컨테이너에 반영하려면 `docker compose up -d --build admin`.

검사:

```bash
npm run build        # tsc -b + vite build → dist/
npm run lint         # 코드 규칙 검사. import 순서·빈 줄은 npm run lint -- --fix로 고친다
npm run test:lint-rules   # 로컬 ESLint 규칙(eslint-rules/)과 import 그룹 설정 단위 테스트
npm run test:unit        # 화면 없이 도는 순수 함수 테스트(tests/*.test.mjs 7파일 43개: errorText[서버 오류 코드 51개 표 일치], mfa, modelPicker, seasonMode, userAgent, userInfo, weather)
```

## 관리자 계정 만들기

가입 API로는 관리자를 만들 수 없다. 백엔드 설정 파일 `backend/config/application-local.yml`(git 제외, 견본 `application-local.example.yml`)에 계정을 적어 두면 백엔드가 시작할 때 **관리자가 한 명도 없을 때만** 그 계정을 만든다. 같은 이메일로 이미 가입한 일반 회원은 관리자로 올리지 않는다(경고 로그만, doc/22 4.1.1). 2단계 인증을 켜 두려면(기본) 마스터 키 `wearly.secret-key`도 필요하다 (doc/07 2장, doc/08 6장).

```yaml
# backend/config/application-local.yml (git 제외)
wearly:
  admin:
    email: "admin@example.com"
    password: "admin1234pw"   # 8~64자, 영문과 숫자 포함
    name: "관리자"             # 선택, 기본 "관리자"
```

값을 넣고 백엔드를 다시 시작한 뒤(docker면 `docker compose restart backend`) 그 이메일·비밀번호로 로그인한다. 관리자 계정이 아니면 로그인 화면에서 "관리자 계정이 아닙니다"로 막는다. 관리자를 더 늘리려면 **회원** 화면에서 '관리자 지정'을 누른다. 비밀번호는 만든 뒤 **내 계정**에서 바꾸고 설정 파일의 `password`는 지워도 된다.

### 처음 로그인: 2단계 인증 (doc/22 4.1.6)

서버 설정 `wearly.admin.mfa.required`가 `true`(기본)이면 로그인 순서가 이렇다.

1. 이메일·비밀번호를 넣는다. 맞으면 서버가 토큰 대신 5분짜리 대기 토큰을 준다.
2. **처음이면 인증 앱 등록 화면**: QR(또는 수동 입력 키)을 Google Authenticator 같은 인증 앱에 등록하고 6자리 코드를 넣는다. "새 QR 받기"로 다시 받을 수 있다.
3. **복구 코드 10개**가 한 번만 나온다. 안전한 곳에 저장하고 "저장했어요"를 체크해야 들어간다.
4. 다음 로그인부터는 인증 앱의 6자리 코드를 넣는다. 휴대폰을 잃었으면 "복구 코드로 들어가기"로 복구 코드 하나를 넣는다(한 번 쓰면 못 쓴다).
5. 코드를 5번 틀리면 15분 잠긴다. 대기 토큰이 만료되면(5분) 처음 화면으로 돌아간다.

로컬 개발에서 번거로우면 백엔드 `application-local.yml`에 `wearly.admin.mfa.required: false`(이미 등록한 관리자는 그래도 코드를 확인한다). 복구 코드도 없을 때는 다른 관리자가 **회원** 화면에서 "2단계 인증 초기화"를 하거나 DB에서 지운다 (doc/22 4.1.7).

### 회사 계정 로그인 (SSO, doc/22 4.2)

서버가 `wearly.sso.enabled`(기본 꺼짐)를 켜고 공급자를 설정하면 로그인 화면 위쪽에 **"<공급자 이름> 로그인"** 버튼(예: "회사 계정 (Keycloak) 로그인")이 생긴다. 그 아래 "또는 비상 관리자 계정" 구분선 뒤에 이메일·비밀번호 칸이 그대로 있다. 꺼져 있거나 서버에 닿지 못하면 버튼은 보이지 않는다.

1. 버튼을 누르면 브라우저가 백엔드 `/auth/sso/{id}/start?redirect=<이 사이트>/sso/callback`을 거쳐 IdP 로그인 화면으로 간다.
2. 로그인을 마치면 `/sso/callback?code=…`로 돌아온다(`pages/SsoCallbackPage.tsx`). 사이트가 한 번만 쓰는 교환 코드를 `POST /auth/sso/exchange`로 보내 토큰을 받고 들어간다. 주소에는 토큰이 실리지 않는다.
3. 실패하면 `/sso/callback?error=…&status=…&traceId=…`로 돌아오고, 로그인 화면에 오류 문구가 보인다(예: 관리자 그룹이 아니면 `SSO_NOT_ADMIN`).
4. 로그아웃하면 이 사이트의 세션을 끊고, 회사 계정으로 들어온 세션은 IdP 로그아웃(`/auth/sso/{id}/logout?redirect=<이 사이트>/login`)도 거쳐 `/login`으로 돌아온다.

- 서버 설정의 `redirect-allowlist`에 이 사이트의 `<주소>/sso/callback`과 `<주소>/login`이 **정확히** 있어야 한다(로컬 `http://localhost:5173/sso/callback`, `http://localhost:5173/login`). 없으면 시작할 때 400.
- 서버가 IdP의 MFA를 믿지 않게 설정(`trust-idp-mfa: false`)했다면 회사 계정 로그인 뒤 2단계 인증(코드 입력) 화면이 이어진다.
- 회사 계정으로 처음 만들어진 관리자는 이 사이트의 비밀번호가 없다. "내 계정"은 로그인 방식("회사 계정 (keycloak)")과 안내를 보이고, 비밀번호 바꾸기·2단계 인증은 쓸 수 없다.
- 로컬에서 켜는 법(Keycloak)은 doc/22 4.2.5 — `docker compose --profile sso up -d keycloak`, `backend/config/application-local.yml`에 SSO 블록, `docker compose up -d --build backend`. 시험 계정 `admin@wearly.local` / `WearlyAdmin1!`.
- 구현: `pages/LoginPage.tsx`(`SsoButtons`), `pages/SsoCallbackPage.tsx`, `stores/useAuthStore.ts`(`startSso`·`completeSso`·`failSso`), `api/client.ts`(`ssoProviders`·`ssoStartUrl`·`ssoExchange`·`ssoLogoutUrl`), `api/tokenStore.ts`(SSO 공급자 ID를 `sessionStorage`에 기억).

## 화면

| 메뉴 | 내용 |
|---|---|
| 대시보드 | 회원 수(7일 신규), 아이템 수, 오늘 AI 호출·실패, 공급자별 7일 통계, 지금 쓸 수 있는 공급자 순서 |
| 코드 관리 | 그룹 탭(종류·세부 종류·색상·패턴·계절[초봄·초여름·초가을 포함 7개]·스타일·보유 상태·사용 중지 이유·날씨 지역·연령대·체형·핏 선호), 표(코드·라벨·상위·속성·순서값·사용·사용 수), 위·아래 버튼으로 순서 바꾸기, 사용 스위치(비활성화), 추가·수정 패널, 삭제(사용 중이면 409 → 비활성화 안내) |
| AI 공급자 | 목록(키 출처·사용 가능 여부·오늘 성공/실패), 사용 켜기/끄기, 우선순위 위·아래, 추가·수정(종류·**모델 드롭다운**·서버 설정 키 이름·우선순위·옵션), **API 키 웹 입력**, 삭제, **시험 분석** |
| AI 분석 기록 | 공급자·상태·회원 이메일·기간 필터, 표, 행을 누르면 결과 JSON 전체 |
| 회원 | 이메일·이름 검색, 상태·역할 필터, 정지·해제, 관리자 지정·해제 (자기 자신은 정지·해제 불가, 마지막 관리자는 서버가 막음), **나의 정보** 열(연령대·체형·키·핏 선호 한 줄), **2단계 인증** 열과 "2단계 인증 초기화"(다른 관리자 것만, 확인 대화상자) |
| 로그인 사용자 | 살아 있는 세션 표, 검색·기기 필터, 세션·회원 단위 강제 로그아웃 (doc/16) |
| 관리자 로그인 기록 | `/auth-events`: 회원 ID·종류·기간 필터, 시각·회원·역할·종류(로그인 실패·2단계 인증 성공/실패/잠김, 회사 계정 로그인·거부 배지와 공급자·`amr` 포함)·기기·IP (doc/22 4.1.5, 4.2) |
| 내 계정 | `/me`: 로그인 방식(이메일·비밀번호 / 회사 계정), 비밀번호 바꾸기, 2단계 인증 상태(켜짐/꺼짐·등록일·남은 복구 코드·서버 설정 필수/선택), 등록, 다시 등록(지금 비밀번호 + 코드 또는 복구 코드). 복구 코드가 적게 남으면 안내 |
| 날씨 | doc/17 6장. **스케줄**(켜기·주기·예보 일수·차단 기준, 예상 월 호출 수, 지금 실행, 최근 실행), **공급자**(사용·상태·키 상태·차단 해제·월 사용량·수정·시험 호출, 아래에 **Open-Meteo 보조 공급자 카드**: 평년값·장소 검색의 이번 달 호출 수와 비상업 안내), **호출 이력**(요약·필터·상세, **종류** 칸·필터 = 정기·수동 실행 / 다른 장소 날씨 / 장소 검색 / 평년값을 서버 `callKind`로 거름, 격자 좌표는 "좌표 35.7, 139.7"로 표시, 요약에 보조 공급자 호출 수), **저장된 날씨**(지역별 값·앱 응답 미리보기·원본·이 지역 새로 받기). 순수 함수는 `lib/weather.ts`(`tests/weather.test.mjs`) |
| 앱 설정 | **테마**(doc/12 3·6장): 냥깨비·Closeta 카드(이름·설명·색 견본 18개·작은 앱 화면 미리보기·마스코트 6자세), 현재 적용 중 배지, 마지막 변경 시각·관리자, "이 테마로 바꾸기" → 확인 대화상자 → `PUT /admin/app/theme`. 사용자가 앱을 다시 열거나 앱으로 돌아오면 반영된다. 백엔드가 아직 API를 모르면(404) 안내를 띄우고 미리보기만 보여 준다. **계절 테마**(doc/12 7장, 이슈 #6): 계절 모드(끄기·자동·봄/여름/가을/겨울 고정) 선택 → 확인 대화상자 → `PUT { seasonMode }`, 4계절 × 2테마 미리보기(작은 앱 화면·계절이 바꾸는 색 견본·가장 낮은 글자 대비 WCAG AA)와 지금 앱에 보이는 조합 배지. 서버가 `seasonMode`를 모르면(옛 서버) 안내만 |
| 변경 이력 | 대상(코드·AI 공급자·회원·앱 설정·로그인 세션·날씨)별 목록, 행을 누르면 변경 전·후 JSON. 목록에 모르는 대상 종류가 오면 탭이 따로 생긴다. 테마 변경은 '냥깨비 → Closeta'처럼 보인다 |

### 오류 표시 규칙 (doc/20)

서버 오류 응답에는 문장이 없다(`{ status, code, params?, fieldErrors: [{ field, code, params? }], traceId }`). 화면에 오류를 보일 때는 **`e.message`를 그대로 쓰지 않고** `src/lib/errorText.ts`를 거친다.

| 함수·표 | 쓰임 |
|---|---|
| `errorText(e)` | 한 줄 문구. `SERVER_ERROR_TEXT`/`CLIENT_ERROR_TEXT` 표 → 없으면 상태별 기본 문구(`statusText`). `VALIDATION_FAILED`는 필드 오류를 `field: 문구 / …`로 덧붙인다 |
| `fieldErrorText(f)`·`fieldErrorOf`·`fieldErrorMap`·`fieldErrorsLine` | 입력칸 아래 필드 오류. 필드 오류 코드(`FIELD_ERROR_TEXT`)와 `params`(`min`·`max`·`allowed`·날짜 범위·바이트 등)로 문구를 만든다 |
| `apiKeyReasonText`·`codeUsagesText` | `API_KEY_INVALID.reason`, `CODE_IN_USE.usages`(옷·프로필·세부 종류·예정 코디 N개) |
| `isSystemError`·`systemTraceId`·`traceLabel`·`errorTextWithTrace` | **5xx**만 "오류 번호 ab12cd34"(`traceId` 앞 8자). 토스트(`useToastStore.showError`)와 `ErrorBox`가 보이고 누르면 전체 `traceId`를 복사한다. 글자만 쓰는 곳은 끝에 " (오류 번호 …)"를 붙인다 |

- 토스트는 `const toastError = useToastStore((s) => s.showError)`로 API 오류를 넘기면 문구와 오류 번호를 붙인다.
- 새 서버 오류 코드·필드 오류 코드가 `error-codes.json`에 생기면 `SERVER_ERROR_TEXT`/`FIELD_ERROR_TEXT`에 문구를 더해야 한다. 빠지면 `npm run test:unit`(`tests/errorText.test.mjs`)이 실패한다. 관리자 화면과 무관한 코드는 `null`로 두고 이유를 주석으로 적는다. 순서는 doc/20 8장.

### 코드 속성 폼

- **종류(CATEGORY)**: 코디 칸 `slot` (TOP·BOTTOM·DRESS·OUTER·SHOES·BAG 또는 없음). 비활성화하면 하위 세부 종류도 새로 고를 수 없다는 확인을 띄운다.
- **세부 종류(SUB_TYPE)**: 상위 종류, 볼륨(-1·0·1), 보온(0~3), 일러스트 모양(앱이 아는 목록 또는 직접 입력) + **옷 일러스트 미리보기**. 앱이 모르는 모양 키면 앱과 같은 규칙으로 상위 종류의 기본 모양을 그리고 그 사실을 알려 준다. 미리보기 색은 색 칩으로 바꿀 수 있다(저장 안 됨).
- **색상(COLOR)**: hex 색 선택기 + 직접 입력, 뉴트럴, 밝기 슬라이더(hex로 계산한 제안값 '적용'), 어울리는 색 다중 선택(색 칩). 저장하면 서버가 상대 색에도 양방향으로 반영한다.
- **보유 상태(ITEM_STATUS)**: 코디 추천에 넣기(`recommendable`).
- **체형(BODY_TYPE)**: 짧은 설명(`description`)과 고르기를 돕는 한 줄 특징(`hint`) 입력칸 (이슈 #5). 앱 체형 설명 카드에 쓰인다.
- **시험 분석**: 결과에 색상(1~3) 행이 있다 (이슈 #4, 분석 결과 `colors`).
- 서버 검증 오류(`fieldErrors`의 `attrs.hex`, `attrs.pairs[1]` 같은 경로)는 해당 입력칸 아래에 필드 오류 코드의 문구로 표시한다 (위 "오류 표시 규칙").

### API 키 웹 입력 (doc/07 6장)

공급자 이름(또는 '수정'·'키 관리')을 눌러 패널을 열면 맨 위에 **API 키** 영역이 있다. 지금 쓰는 키의 출처를 보여 준다: 웹 입력(`••••abcd` · 넣은 시각 · 넣은 관리자) / 서버 설정 키(`ANTHROPIC_API_KEY` 등, 백엔드 `application-local.yml`의 `wearly.ai.keys.*` 또는 같은 이름 환경변수) / 없음. **키 입력·교체**를 누르고 키를 붙여 넣은 뒤 내 비밀번호를 다시 입력하면, 서버가 키를 AES-256-GCM으로 암호화해 DB에 저장하고 그때부터 서버 설정 키보다 웹 키를 먼저 쓴다(서버 재시작 불필요). "저장 전 유효성 확인"(기본 켬)은 저장하기 전에 그 키로 각 사 모델 목록을 한 번 조회한다. **웹 키 삭제(서버 설정 키로 되돌리기)**는 비밀번호 확인 후 웹 키를 지운다. 서버에 마스터 키(`wearly.secret-key`)가 없으면 입력 버튼이 꺼지고 서버 설정 키만 쓴다. 보안: 키는 쓰기 전용이라 저장한 뒤 다시 볼 수 없다(마지막 4자만 보임). 화면은 키를 React 상태에 두지 않고, 저장 버튼을 누른 순간 한 번 읽어 JSON 본문으로만 보낸 뒤 성공·실패와 관계없이 입력칸을 바로 비운다. URL·쿼리 문자열·`sessionStorage`·`localStorage`·쿼리 캐시·콘솔에 키를 남기지 않는다(mutation은 `gcTime: 0` + 끝나면 `reset()`). 공용 PC라면 붙여 넣은 뒤 클립보드도 비운다.

### 모델 드롭다운 (doc/07 4장 "모델 목록")

공급자 패널의 모델 칸은 `pages/providers/ModelPicker.tsx`다. 저장된 공급자는 `GET /admin/ai/providers/{id}/models`(`useProviderModels`), 저장 전(추가 중이거나 종류를 바꾼 중)은 `GET /admin/ai/types`의 `models`를 쓴다.

- 검색할 수 있는 목록: 화면 이름 + 작은 ID + "추천" 배지. ↑↓ · Enter · Esc. 맨 아래 "직접 입력…"을 고르면 입력칸이 되고 "목록에서 고르기"로 돌아온다.
- 지금 값이 목록에 없으면 직접 입력 상태로 시작하고 "목록에 없는 모델" 배지와 "새 모델·프리뷰면 그대로 저장할 수 있어요" 안내를 보인다(서버는 형식만 검증). 목록이 비면 직접 입력만 된다.
- 출처 문구: `LIVE` "제공사에서 받은 목록 · 3분 전" + **새로 받기**(`refresh=true`), `CATALOG` "내장 목록(키가 없거나 목록을 받지 못했어요: …)", 저장 전 "내장 목록(저장한 뒤 키가 있으면 제공사 목록을 받을 수 있어요)".
- 키를 저장·삭제하거나 공급자를 저장하면 모델 목록 쿼리(`['ai', 'models']`)를 무효화한다.
- 서버가 404를 주거나 `types`에 `models`가 없으면(옛 서버) 예전처럼 텍스트 입력칸을 쓴다.
- 순수 함수(`lib/modelPicker.ts`: 처음 모드, "목록에 없음" 판단, 검색, 출처 문구, 키보드 이동)는 `tests/modelPicker.test.mjs`로 확인한다.

### 시험 분석

사진 한 장(JPEG·PNG·WebP·HEIC, 10MB 이하)을 올리고 공급자를 여러 개 고르면 `POST /admin/ai/providers/{id}/test`를 **동시에** 보낸다. 결과(성공/실패·오류 코드), 모델, 지연(서버 측정 + 브라우저 왕복), 토큰(입력/출력), 제안값(코드는 라벨로)을 열로 나란히 보여 주고, 공급자마다 다른 값은 '다름'으로 표시한다. 일일 한도에 세지 않고 기록은 `ADMIN_TEST`로 남는다.

## 구조

```
admin/
  eslint.config.js         코드 규칙(아래 "코드 규칙")
  eslint-rules/            로컬 ESLint 규칙: component-order.js(컴포넌트 본문 순서), import-groups.js(import 그룹), rules.test.js(단위 테스트)
  vite.config.ts           포트 5173, '@' → src, 앱 데이터 별칭(fs.allow로 ../mobile/src/data 폴더만 허용):
                           '@garment-shapes' → garmentShapes.ts, '@app-themes' → themes.ts, '@app-mascots' → mascots/index.ts,
                           '@app-mascot-images' → mobile/assets/mascots (마스코트 PNG 폴더, fs.allow에도 추가),
                           '@app-seasons' → seasons.ts (계절 색·계절 계산, 이슈 #6)
  src/
    api/                   관리자 API 클라이언트와 계약 타입 (앱 코드를 import하지 않음)
      client.ts            토큰 갱신 single-flight, 응답 정규화
      tokenStore.ts        sessionStorage (wearly-admin-tokens, wearly-admin-user, wearly-admin-sso[SSO 공급자 ID], wearly-admin-sso-pending[SSO 시작~콜백])
      types.ts · errors.ts(ApiError: code·params·fieldErrors·traceId) · index.ts
    stores/                zustand 스토어: useAuthStore(로그인 사용자·상태, 로그인·2단계 인증 단계[대기 토큰·등록·코드·복구 코드]·회사 계정 로그인[startSso·completeSso·failSso]·로그아웃(SSO면 IdP 로그아웃도), 로그인 잃음 처리), useToastStore(토스트)
    components/            Layout(왼쪽 메뉴), ui(버튼·표·패널 등), Garment(일러스트·색 칩), Toaster(토스트 표시),
                           Mascot(마스코트 PNG), ThemePreview(색 견본·작은 앱 화면), SeasonPreview(4계절 × 2테마 미리보기), Mfa(QR·코드 입력·복구 코드 공용 조각), Sessions
    lib/                   queryClient(React Query 캐시), garment(앱 일러스트 데이터 래퍼), appTheme(앱 테마·마스코트 데이터 래퍼),
                           queries(TanStack Query 키·훅), format, providerTypes, modelPicker(모델 드롭다운의 순수 함수),
                           errorText(오류 코드 → 한국어 문구·오류 번호, 위 "오류 표시 규칙"), weather(날씨 화면 순수 함수: 호출 종류·격자 좌표·보조 공급자),
                           mfa(2단계 인증 화면의 순수 함수), seasonMode(계절 모드 화면 문구·대비 등급), userInfo(회원 나의 정보 한 줄), sessions·userAgent(로그인 세션 라벨·기기)
    pages/                 Dashboard · codes/ · providers/(ApiKeySection = API 키 웹 입력, ModelPicker = 모델 드롭다운) · AiLogs · Users · Sessions · AuthEvents(관리자 로그인 기록) · MyAccount(내 계정) · weather/ · AppTheme(테마·계절 모드) · AuditLogs · Login(2단계 인증·회사 계정 버튼 포함) · SsoCallback(회사 계정 로그인 콜백)
```

### 토큰

- Access·Refresh 토큰은 `sessionStorage`(탭을 닫으면 사라짐).
- `TOKEN_EXPIRED`면 `/auth/refresh`를 한 번만 보내고(동시 요청이 여러 개여도 하나) 원래 요청을 한 번 다시 보낸다.
- 갱신 실패, `TOKEN_INVALID`(정지·탈퇴), 관리자 API의 `403 FORBIDDEN`(역할이 내려감)이면 토큰을 지우고 로그인 화면으로 보낸다.

### 옷 일러스트 데이터

`src/lib/garment.ts`가 앱의 `mobile/src/data/garmentShapes.ts`(순수 TS)를 그대로 가져와 앱 `GarmentArt`와 같은 순서·색 규칙(그림자 → 몸판 그라데이션 → 주름 → 밝은 면 → 세부선 → 단추, `garmentTones()`)으로 SVG를 그린다. 앱 파일이 없으면 `src/lib/garmentShapesFallback.ts`(빈 데이터, 사각형)로 빌드되고 코드 관리 화면에 안내가 뜬다.

### 앱 테마·마스코트 데이터

`src/lib/appTheme.ts`가 앱의 `mobile/src/data/themes.ts`(`THEMES`·`THEME_IDS`)와 `mobile/src/data/mascots/`(`mascotImage(id, pose)`·`MASCOT_POSES` — 자세별 PNG 파일 매핑)를 그대로 가져오고, 마스코트 PNG는 `import.meta.glob('@app-mascot-images/**/*.png')`로 `mobile/assets/mascots/` 폴더에서 읽어 주소를 얻는다(`mascotImageUrl(file)`). `components/Mascot.tsx`(`MascotImage`)가 앱과 같은 규칙(투명 그림은 정사각형에 맞춰, 장면 그림은 가로 폭에 높이는 비율대로·둥근 모서리)으로 `<img>`를 그린다. 이 파일들에는 대체 파일이 없어 없으면 빌드가 실패한다. docker 이미지(`admin/Dockerfile`, 루트 `.dockerignore`)는 `garmentShapes.ts`·`themes.ts`·`mascots/` 폴더와 `mobile/assets/mascots/`를 함께 복사한다.

## 코드 규칙

`npm run lint`(`eslint.config.js`)가 아래 규칙을 강제한다. 어기면 lint가 실패한다.

1. **함수는 화살표 함수로 쓴다.** 함수 선언(`function foo() {}`), 함수 표현식(`function () {}`), 객체 메서드 축약(`{ get() {} }`) 대신 `const foo = () => {}`, `{ get: () => {} }`. React 컴포넌트도 `export const Page = () => { ... };`. TSX의 제네릭 화살표 함수는 `<T,>(x: T) =>`처럼 쉼표를 붙인다.
   - 화살표로 쓸 수 없는 클래스 메서드·생성자(`class ApiError extends Error`), 객체의 getter/setter, 제너레이터는 규칙에서 빠져 있다.
   - 그 밖에 불가피한 곳(`this` 바인딩이 꼭 필요한 함수, 함수 오버로드 등)은 그 줄 위에 `// eslint-disable-next-line no-restricted-syntax -- <불가피한 이유>`를 단다.
   - 화살표 함수(`const`)는 호이스팅되지 않으므로 모듈 최상위에서 바로 부르는 함수는 부르는 곳보다 위에 정의한다.
   - ESLint: `no-restricted-syntax`(`FunctionDeclaration`, 클래스 메서드·getter/setter·제너레이터를 뺀 `FunctionExpression`), `prefer-arrow-callback`.
2. **인터페이스 이름은 `I`로 시작한다**: `IProps`, `IAiProvider`. (`@typescript-eslint/naming-convention`)
3. **타입 별칭 이름은 `T`로 시작한다**: `TKeySource`, `TAuthStatus`. (`@typescript-eslint/naming-convention`)
   - 2·3은 `admin/src`에서 선언한 이름에만 적용한다. 외부 패키지 타입과 앱 파일(`mobile/src/data/garmentShapes.ts`·`themes.ts`·`mascots/`)의 이름은 그대로 쓴다.
4. **상태 공유는 zustand 스토어(`src/stores/`)로 하고 React Context는 쓰지 않는다.** 화면에서는 `useAuthStore((s) => s.user)`처럼 선택자로 구독하고, 컴포넌트 밖(API 클라이언트의 `onAuthLost` 등)에서는 `useAuthStore.getState()`로 부른다. 토큰 자체는 스토어가 아니라 `api/tokenStore.ts`(sessionStorage)에 둔다. 서버 데이터 캐시는 TanStack Query가 맡는다.
   - ESLint: `no-restricted-imports`로 `react`의 `createContext`·`useContext` import 금지(그래서 `import * as React from 'react'`도 막힌다), `no-restricted-syntax`로 `React.createContext`·`React.useContext` 금지.
5. **컴포넌트·커스텀 훅 본문은 ref → state → hooks → effect → memo → event 순서로 쓰고, 그 뒤에 `return`(JSX)을 둔다.** 분류 사이에는 빈 줄 하나, 같은 분류 안은 원래 순서.
   - ref: `useRef` / state: `useState`·`useReducer` / hooks: 그 밖의 모든 훅 호출(커스텀 훅, `useAuthStore((s) => …)` 같은 스토어 선택자, `useQuery`·`useMutation`·`useQueryClient`, `useNavigate`·`useSearchParams`, `useId` 등) / effect: `useEffect`·`useLayoutEffect` / memo: `useMemo`와 렌더마다 계산하는 파생 값(`const visible = items.filter(…)`처럼 함수가 아닌 `const`) / event: 이벤트 핸들러·함수(`const handleSave = () => {}`)와 `useCallback`.
   - 대상: 이름이 대문자(컴포넌트) 또는 `use`(커스텀 훅)로 시작하고 본문이 블록인 화살표 함수(`memo(() => {})`로 감싼 것 포함). `if`·일반 함수 호출·`return`처럼 분류할 수 없는 문장은 순서 검사에서 빠진다(조기 `return`은 훅 규칙대로 훅 호출 뒤에).
   - **예외(TDZ)**: `const`는 선언 전에 읽으면 오류가 나므로, 앞 분류가 뒤 분류의 값을 **렌더 중에 바로** 읽을 때만(state 초깃값이 hooks 결과나 파생 값을 읽음, `useQuery` 인자가 파생 값을 읽음, effect 의존성 배열이 파생 값을 읽음, `useMutation` 옵션에 핸들러를 그대로 넘김 등) 필요한 선언을 최소한으로 앞으로 올리고 바로 위 줄에 `// 순서 예외: <이유>`를 단다. effect·핸들러·mutation 콜백 **본문 안에서만** 읽는 값은 렌더 뒤에 실행되므로 예외가 아니다.
     ```tsx
     // 순서 예외: draft 상태의 초깃값이 URL 쿼리(params)를 읽는다
     const [params, setParams] = useSearchParams();

     const [draft, setDraft] = useState(() => params.get('q') ?? '');
     ```
   - ESLint: 로컬 규칙 `local/component-order`(`eslint-rules/component-order.js`). 순서가 어긋나면 오류(자동 수정 없음 — 주석·TDZ 때문에 옮기는 것은 사람이 한다), 분류 사이 빈 줄이 없으면 오류(`--fix`로 빈 줄을 넣는다). `// 순서 예외:` 주석이 붙은 문장은 건너뛴다.
6. **import는 다음 그룹 순서로 쓰고, 그룹 사이에 빈 줄 하나를 둔다.** 그룹은 import 문자열의 모양이 아니라 **가리키는 위치**로 정한다.
   1. react: `react`, `react-dom`, `react/*`
   2. api: `src/api/**` (`@/api`, `@/api/client` …)
   3. component: `src/components/**`, 화면 컴포넌트 `src/pages/**`, `src/App`
   4. hooks: `src/hooks/**`, zustand 스토어 `src/stores/**`(스토어도 `useXxxStore` 훅)
   5. 3rd party: react를 뺀 외부 패키지(`@tanstack/react-query`, `react-router`, `zustand`, `node:*` …)
   6. local: 위에 안 든 내부 모듈(`src/lib/**`, 타입·유틸 등)과 앱 데이터 별칭 `@garment-shapes`·`@app-themes`·`@app-mascots`
   7. css: `import '@/styles.css'`
   - 그룹 안은 경로 알파벳순, `{ … }` 안의 이름도 알파벳순. 타입은 `import { a, type B }` 대신 **`import type { B }` 문으로 따로** 쓰고, 같은 그룹 안에서 값 import 뒤에 온다.
   - **내부 모듈은 `@/` 별칭으로만 가져온다**(`./client`, `../api` 같은 상대 경로 금지, 같은 폴더도 `@/pages/providers/ProviderForm`처럼). 그래야 경로 문자열만으로 위치를 알 수 있다. `@/`는 `tsconfig.app.json`의 `paths`와 `vite.config.ts`의 `alias`에 있다.
   - ESLint: `simple-import-sort/imports`(그룹 정의는 `eslint-rules/import-groups.js`), 이름 순서를 바꿀 때 빠지는 쉼표 뒤 공백은 `@stylistic/comma-spacing`, 상대 경로는 `no-restricted-imports`(patterns), 인라인 `type`은 `no-restricted-syntax`(`ImportSpecifier[importKind='type']`). 순서는 `npm run lint -- --fix`로 자동 정렬된다.
