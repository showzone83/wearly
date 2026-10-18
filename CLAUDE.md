# Wearly (웨어리) 프로젝트

직장인 출근 코디를 "내 옷장에 있는 옷으로" 추천하는 앱. 슬로건: "오늘 뭐 입지? 이제 고민 끝."

## 폴더 구성

- `Wearly_서비스_기획서_v1.1.pptx` — 원본 기획서 (46장, 부록에 화면 목업)
- `doc/` — 지금까지의 분석·결정·작업 기록. **새 대화를 시작하면 `doc/README.md`부터 읽는다.**
  - `01_기획서_분석.md` — 기획서 요약, 유사 서비스(에이클로젯·Alta·Whering 등), 장단점, 보완 제안
  - `02_기술_결정.md` — 네이티브 앱, React Native(Expo), PostgreSQL, API 키·보안, 소셜 로그인 결정과 근거
  - `03_기획서_반영_목록.md` — 기획서 pptx에 반영할 수정 사항 (아직 pptx에 반영하지 않음)
  - `04_목업_앱.md` — 목업 앱 구조, 실행 방법, 추천 엔진 설명, 검증 내역, 알려진 한계
  - `05_백엔드_API.md` — 백엔드 API 계약: 토큰 로그인, 옷장, AI 사진 분석, 오류 코드, 앱 API 모듈·하네스 규칙
  - `06_코드_관리.md` — 옷 속성 값(종류·색·패턴 등)을 DB 코드 테이블로 관리하는 계약 (초기값은 06_코드_초기값.json)
  - `07_관리자_사이트.md` — 관리자 사이트·관리자 API, AI 공급자 관리(Claude·OpenAI·Gemini), API 키 웹 입력(암호화)
  - `08_백엔드_구조.md` — 백엔드 구조 규칙: 헥사고날 패키지, 입출력 모델(Request→DTO→Response), Google Java Style, yml 설정
  - `09_AI_코디_추천.md` — AI 코디 추천: AI가 점수 기준으로 후보를 고르고 앱 엔진이 채점
  - `10_백엔드_흐름.md` — 백엔드 요청 흐름(클래스 호출 순서): 인증·오류·트랜잭션, 로그인·옷 등록·조회·AI 분석·코디 추천·관리자 흐름, 새 기능 추가 순서
  - `11_백엔드_아키텍처_점검.md` — 백엔드 헥사고날 점검 보고서와 처리 현황
  - `12_디자인_테마.md` — 디자인 테마 두 개(냥깨비·Closeta), 마스코트 자세, 관리자 전환 API
  - `13_옷_사용_중지.md` — 사용하지 않는 옷(이유·기록 보존)과 완전 삭제
  - `14_DB_명령.md` — 로컬 DB 접속·조회·수정·백업 명령
  - `15_터미널_명령.md` — 서버·앱·관리자·하네스 실행 명령과 포트
  - `16_세션_Valkey.md` — Refresh 토큰·세션을 Valkey로, 관리자 강제 로그아웃
  - `17_날씨.md` — 날씨: 서버가 지역별로 주기적으로 받아 저장, 앱 조회 API, 관리자 날씨 메뉴
  - `18_사진_저장.md` — 옷 사진 서버 저장: S3 호환 저장소(로컬 MinIO), 서명 URL, 크기 1600·400·AI 전송 1024
  - `19_장소별_날씨.md` — 장소별 날씨: 예정일 코디에 장소 선택, 평년값, 장소 검색
  - `20_오류_응답.md` — 오류 응답(코드값만, traceId), error-codes.json과 앱·관리자 문구 표, 새 코드 추가 순서
  - `21_예정_코디_저장.md` — 예정 코디 서버 저장(`/plans`), 앱 동기화 규칙
  - `17_날씨.md` — 날씨: 서버가 WeatherAPI.com에서 지역별로 주기적으로 받아 저장, 앱은 저장값만 조회, 관리자 날씨 메뉴
  - `18_사진_저장.md` — 옷 사진 서버 저장: S3 호환 저장소(로컬 MinIO), 서명 URL로 올리고 받기, EXIF 제거·축소, 앱 흐름, AWS S3·R2·NCP 전환
  - `19_장소별_날씨.md` — 장소별 날씨(구현함): 예정일 코디에 장소 선택, 예보 밖 날짜는 평년값, 17개 시·도 밖 장소의 요청 시점 조회·격자 캐시·회원 한도, 한글 장소 검색, 예보가 나오면 다시 확인
- `mobile/` — React Native(Expo SDK 57) 목업 앱. 백엔드 없이 기기 안에 데이터 저장. Expo 작업 규칙은 `mobile/AGENTS.md`를 따른다.

git 저장소는 `wearly/` 루트 하나다 (원격: `github.com/showzone83/wearly`). `mobile/`은 별도 저장소가 아니다.

## 확정된 기술 결정 (기획서 원안에서 변경)

| 항목 | 원안 | 결정 |
|---|---|---|
| 출시 형태 | PWA | iOS·Android 네이티브 앱 + 웹 체험판 |
| 앱 프레임워크 | React(웹) | React Native (Expo), TypeScript |
| 백엔드 | Spring Boot | Spring Boot (유지) |
| DB | MySQL | PostgreSQL + pgvector, 테이블명은 소문자 snake_case |
| 로그인 | 이메일·카카오 | 카카오·애플·구글 소셜 로그인 (네이버는 P1) |
| 비밀 키 | — | 서버 전용. 앱은 자체 API만 호출, 토큰은 Keychain/Keystore |

## 목업 앱 실행

처음 한 번만:

```bash
cd mobile && npm install
```

실행 (휴대폰은 Expo Go로 QR 스캔, 브라우저는 터미널에서 `w`):

```bash
cd mobile && npx expo start
```

검사:

```bash
cd mobile && npm run typecheck && npx expo-doctor && npm run check:errors
```

- `CI=1`로 실행하지 않는다. Metro가 파일 감시를 끄므로 코드 수정이 반영되지 않는다.
- iOS 시뮬레이터: Xcode 27이 설치되어 있지만 `xcode-select`가 Command Line Tools를 가리킨다. `sudo xcode-select -s /Applications/Xcode.app`을 한 번 해 두거나, 명령 앞에 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`를 붙인다. 실행은 `npx expo start --ios` (Expo Go를 시뮬레이터에 자동 설치). 자세한 방법은 `doc/04_목업_앱.md` 2장.
- 시뮬레이터 자동 조작(탭·스와이프·스크린샷)은 `idb`(`brew install facebook/fb/idb-companion` + `pip3 install --user fb-idb`)와 `xcrun simctl io booted screenshot`을 쓴다.
- 패키지 추가는 `npx expo install <패키지>`로 한다 (SDK 호환 버전 자동 선택).

## 백엔드·관리자 사이트 실행 (docker compose)

DB·Valkey(로그인 세션)·MinIO(옷 사진)·백엔드·관리자 사이트는 저장소 루트의 `docker-compose.yml`로 한 번에 띄운다. 앱(Expo)은 넣지 않는다 — 시뮬레이터·휴대폰이 Mac의 Metro에 붙어야 하므로 `cd mobile && npx expo start`를 따로 띄운다.

```bash
./dev.sh                                  # compose 실행 + 백엔드 로그·DB 실시간 로그·Expo 창을 한 번에 (--no-build · --no-expo · --ios · logs · stop, doc/15)
docker compose up -d --build              # 전체 빌드·실행: db(5433) · valkey(6380) · minio(9000·9001) · backend(8080) · admin(5173)
docker compose logs -f backend            # 로그 보기 (admin, db, valkey, minio도 같은 방식)
docker compose up -d --build backend      # 백엔드만 다시 빌드·재시작 (코드 수정 후)
docker compose down                       # 중지 (DB 데이터는 wearly-pgdata, 세션은 wearly-valkeydata, 사진은 wearly-miniodata 볼륨에 남음)
```

- 백엔드 비밀 값(AI 키·관리자 계정·마스터 키)은 `backend/config/application-local.yml`(git 제외)에 둔다. 견본은 `backend/config/application-local.example.yml`. `.env`는 쓰지 않는다 (`doc/08_백엔드_구조.md` 6장). compose는 `backend/config`를 컨테이너에 읽기 전용으로 마운트하고, 이미지에는 비밀 값을 넣지 않는다.
- 옷 사진은 MinIO(`pgsty/minio` 포크, 콘솔 http://localhost:9001, 로컬 전용 계정 `wearly` / `wearly-minio-local`)에 두고 앱은 서명 URL로 올리고 받는다 (`doc/18_사진_저장.md`).
- 관리자 사이트를 `npm run dev`(vite, 5173)로 띄울 때는 `admin` 서비스를 빼고 `docker compose up -d --build db valkey minio minio-init backend`.
- 백엔드를 Mac에서 직접 돌릴 때는 `docker compose up -d db valkey minio minio-init` 후 `cd backend && ./gradlew bootRun` (자세한 내용은 `backend/README.md`).

## 목업 앱 핵심 코드

- 추천 엔진: `mobile/src/lib/recommend.ts` — 기획서 p.24 점수 모델 (색상 30, 상황 25, 날씨 20, 취향 15, 다양성 10)
- 진단·처방: `mobile/src/lib/diagnose.ts` — 5개 항목, 강점·개선점, 내 옷장 교체 처방
- 상태 저장: `mobile/src/store/useStore.ts` — zustand + AsyncStorage (`wearly-store-v1`)
- 화면: `mobile/src/app/` (expo-router). 탭은 홈·내 옷장·AI 코디·캘린더·마이
- 목업 처리: 날씨(서버 날씨가 없을 때만 가짜 예보, doc/17), AI 사진 분석(가짜 제안값), 푸시·바로 진단(안내 문구만), 소셜 로그인(제외)

## 관리자 사이트 코드 규칙 (`admin/`, ESLint로 강제)

- 함수는 화살표 함수(`const f = () => {}`, 컴포넌트 포함). 클래스 메서드·getter/setter·제너레이터만 예외, 그 밖에 불가피하면 `eslint-disable-next-line no-restricted-syntax -- 이유`.
- 인터페이스 이름은 `I`(`IProps`), 타입 별칭 이름은 `T`(`TKeySource`)로 시작한다.
- 상태 공유는 zustand 스토어(`admin/src/stores/`), React Context 금지.
- 컴포넌트·커스텀 훅 본문은 ref → state → hooks → effect → memo → event 순서(분류 사이 빈 줄). TDZ 때문에 앞에 둬야 하는 선언만 바로 위에 `// 순서 예외: <이유>` (로컬 규칙 `local/component-order`).
- import 그룹은 위치 기준 react → api → component(`components`·`pages`) → hooks(`hooks`·`stores`) → 3rd party → local(`lib` 등) → css, 그룹 사이 빈 줄·안은 알파벳순·`import type`은 따로 값 뒤. 내부 모듈은 `@/` 별칭만(상대 경로 금지). `npm run lint -- --fix`로 정렬.
- 포맷은 루트 `.prettierrc.json`(작은따옴표, 120자)을 따른다. VS Code는 저장할 때 Prettier와 ESLint 자동 수정을 적용한다(`.vscode/settings.json`).
- 자세한 내용은 `admin/README.md`의 "코드 규칙".

## 현재 상태 (2026-10-15)

- 기획서·문서·목업 앱을 루트 저장소 `main` 브랜치에 커밋하고 GitHub에 올렸다.
- 브라우저(375×812)와 iOS 시뮬레이터(iPhone 16 Pro, iOS 18.5, Expo Go)에서 주요 흐름을 검증했다. 실제 휴대폰에서는 아직 확인하지 않았다.

## 다음 할 일

1. Expo Go로 실제 휴대폰에서 확인
2. `doc/03_기획서_반영_목록.md` 내용을 기획서 pptx에 반영
3. 백엔드(Spring Boot + PostgreSQL)와 소셜 로그인 구현 — 각 사 API 키 발급 필요, Expo Dev Build 필요
