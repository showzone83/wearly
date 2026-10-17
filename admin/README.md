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

백엔드(`backend/`)가 `http://localhost:8080`에서 돌고 있어야 한다. 다른 주소면 `admin/.env.local`에 적는다 (`.env.example` 참고).

```bash
VITE_API_URL=http://localhost:8080
```

검사:

```bash
npm run build        # tsc -b + vite build → dist/
npm run lint
```

## 관리자 계정 만들기

가입 API로는 관리자를 만들 수 없다. 백엔드를 시작할 때 환경변수를 주면 그 계정을 만들거나 기존 계정을 `ADMIN`으로 올린다 (doc/07 2장).

```bash
# backend/.env (git 제외)
WEARLY_ADMIN_EMAIL=admin@example.com
WEARLY_ADMIN_PASSWORD=admin1234pw    # 8~64자, 영문과 숫자 포함
WEARLY_ADMIN_NAME=관리자              # 선택, 기본 "관리자"
```

값을 넣고 백엔드를 다시 시작한 뒤 그 이메일·비밀번호로 로그인한다. 관리자 계정이 아니면 로그인 화면에서 "관리자 계정이 아닙니다"로 막는다. 관리자를 더 늘리려면 **회원** 화면에서 '관리자 지정'을 누른다.

## 화면

| 메뉴 | 내용 |
|---|---|
| 대시보드 | 회원 수(7일 신규), 아이템 수, 오늘 AI 호출·실패, 공급자별 7일 통계, 지금 쓸 수 있는 공급자 순서 |
| 코드 관리 | 그룹 탭(종류·세부 종류·색상·패턴·계절·스타일·보유 상태), 표(코드·라벨·상위·속성·순서값·사용·사용 수), 위·아래 버튼으로 순서 바꾸기, 사용 스위치(비활성화), 추가·수정 패널, 삭제(사용 중이면 409 → 비활성화 안내) |
| AI 공급자 | 목록(키 설정 여부·사용 가능 여부·오늘 성공/실패), 사용 켜기/끄기, 우선순위 위·아래, 추가·수정(종류·모델·키 환경변수 이름·우선순위·옵션), 삭제, **시험 분석** |
| AI 분석 기록 | 공급자·상태·회원 이메일·기간 필터, 표, 행을 누르면 결과 JSON 전체 |
| 회원 | 이메일·이름 검색, 상태·역할 필터, 정지·해제, 관리자 지정·해제 (자기 자신은 정지·해제 불가) |
| 변경 이력 | 대상(코드·AI 공급자·회원)별 목록, 행을 누르면 변경 전·후 JSON |

### 코드 속성 폼

- **종류(CATEGORY)**: 코디 칸 `slot` (TOP·BOTTOM·DRESS·OUTER·SHOES·BAG 또는 없음). 비활성화하면 하위 세부 종류도 새로 고를 수 없다는 확인을 띄운다.
- **세부 종류(SUB_TYPE)**: 상위 종류, 볼륨(-1·0·1), 보온(0~3), 일러스트 모양(앱이 아는 목록 또는 직접 입력) + **옷 일러스트 미리보기**. 앱이 모르는 모양 키면 앱과 같은 규칙으로 상위 종류의 기본 모양을 그리고 그 사실을 알려 준다. 미리보기 색은 색 칩으로 바꿀 수 있다(저장 안 됨).
- **색상(COLOR)**: hex 색 선택기 + 직접 입력, 뉴트럴, 밝기 슬라이더(hex로 계산한 제안값 '적용'), 어울리는 색 다중 선택(색 칩). 저장하면 서버가 상대 색에도 양방향으로 반영한다.
- **보유 상태(ITEM_STATUS)**: 코디 추천에 넣기(`recommendable`).
- 서버 검증 오류(`fieldErrors`의 `attrs.hex`, `attrs.pairs[1]` 같은 경로)는 해당 입력칸 아래에 표시한다.

### 시험 분석

사진 한 장(JPEG·PNG·WebP·HEIC, 10MB 이하)을 올리고 공급자를 여러 개 고르면 `POST /admin/ai/providers/{id}/test`를 **동시에** 보낸다. 결과(성공/실패·오류 코드), 모델, 지연(서버 측정 + 브라우저 왕복), 토큰(입력/출력), 제안값(코드는 라벨로)을 열로 나란히 보여 주고, 공급자마다 다른 값은 '다름'으로 표시한다. 일일 한도에 세지 않고 기록은 `ADMIN_TEST`로 남는다.

## 구조

```
admin/
  vite.config.ts           포트 5173, '@' → src, '@garment-shapes' → ../mobile/src/data/garmentShapes.ts (fs.allow로 그 폴더만 허용)
  src/
    api/                   관리자 API 클라이언트와 계약 타입 (앱 코드를 import하지 않음)
      client.ts            토큰 갱신 single-flight, 응답 정규화
      tokenStore.ts        sessionStorage (wearly-admin-tokens, wearly-admin-user)
      types.ts · errors.ts · index.ts
    auth/AuthContext.tsx   로그인 상태, 로그인 잃음 처리
    components/            Layout(왼쪽 메뉴), ui(버튼·표·패널 등), Garment(일러스트·색 칩), toast
    lib/                   garment(앱 일러스트 데이터 래퍼), queries(TanStack Query 키·훅), format, providerTypes
    pages/                 Dashboard · codes/ · providers/ · AiLogs · Users · AuditLogs · Login
```

### 토큰

- Access·Refresh 토큰은 `sessionStorage`(탭을 닫으면 사라짐).
- `TOKEN_EXPIRED`면 `/auth/refresh`를 한 번만 보내고(동시 요청이 여러 개여도 하나) 원래 요청을 한 번 다시 보낸다.
- 갱신 실패, `TOKEN_INVALID`(정지·탈퇴), 관리자 API의 `403 FORBIDDEN`(역할이 내려감)이면 토큰을 지우고 로그인 화면으로 보낸다.

### 옷 일러스트 데이터

`src/lib/garment.ts`가 앱의 `mobile/src/data/garmentShapes.ts`(순수 TS)를 그대로 가져와 앱 `GarmentArt`와 같은 순서(몸판 → 밝은 면 → 세부선 → 단추)로 SVG를 그린다. 앱 파일이 없으면 `src/lib/garmentShapesFallback.ts`(빈 데이터, 사각형)로 빌드되고 코드 관리 화면에 안내가 뜬다.
