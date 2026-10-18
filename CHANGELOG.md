# 변경 이력 (CHANGELOG)

Wearly의 눈에 띄는 변경을 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 형식으로 기록한다. 아직 배포한 버전이 없어 모든 변경은 `## [Unreleased]` 아래에 날짜별로 둔다.

- 새 작업을 `dev`(또는 `main`)에 합칠 때 해당 날짜 소제목 아래에 항목을 추가한다. 항목은 한 줄, 끝에 커밋 해시와 관련 문서(`doc/NN_…md`)·이슈 번호(`#4`)를 적는다.
- 배포할 때 `[Unreleased]` 내용을 `## [버전] - 날짜` 절로 옮기고 `[Unreleased]`는 비운다.
- 분류: **추가**(새 기능) / **변경**(기존 동작·구조 바뀜) / **수정**(버그) / **보안** / **제거** / **문서**.
- 작성자 표시가 없는 항목은 박병진(ByungJin Park)과 Claude의 작업이다. 팀원 작업은 `(작성: kang6123)`처럼 적는다.

## [Unreleased]

### 2026-10-15

#### 추가
- Wearly 프로젝트 시작: 서비스 기획서 v1.1(pptx), 기획서 분석·기술 결정 문서([doc/01](doc/01_기획서_분석.md)~[03](doc/03_기획서_반영_목록.md)), React Native(Expo) 목업 앱. 기기 안에 데이터를 저장하고 추천·진단 엔진을 갖췄다 ([doc/04](doc/04_목업_앱.md)) (93dae4c)

#### 수정
- 목업 앱: 앨범 사진 선택 오류 수정, iOS 시뮬레이터 검증 기록 (6bd70ec)

#### 문서
- 깃허브 저장소 연결·소스트리 사용 메모 (1635c0e, e20a5d0, 6e1fe7f)

### 2026-10-17

#### 추가
- 백엔드 시작: Spring Boot 4.1.1, Flyway 초기 스키마, PostgreSQL 로컬 구성. API 계약 문서 ([doc/05](doc/05_백엔드_API.md)) (3f1c5d7, 138a1b3)
- 토큰 로그인(access/refresh), 프로필, 옷장, AI 사진 분석 API와 통합·단위 테스트 (fed3d69, bd06906, bff393b)
- 앱이 백엔드와 연동: 이메일 로그인·토큰·옷장 동기화·서버 AI 분석. 앱 API 모듈로 실제 백엔드 시나리오를 돌리는 하네스(23개, 이후 29개) (cf5ac31, 99c23d3, fb5642c)
- 옷 속성 값(종류·색·패턴 등)을 DB 코드 테이블로 관리, 앱은 `/codes`로 읽음. 원피스 코디 지원 ([doc/06](doc/06_코드_관리.md)) (cea8f2d, 9deb29d, f34c19c)
- 관리자 사이트(React + Vite): 대시보드·코드 관리·AI 공급자·분석 기록·회원·변경 이력, 관리자 API ([doc/07](doc/07_관리자_사이트.md)) (50f4837, cce81a9, 982727f)
- AI 공급자를 여러 개(Claude·OpenAI·Gemini)로 두고 관리자에서 고르게 함. 기본값 claude, 목업은 `bootRunMock`으로 분리 (9deb29d, 3489394)
- AI 공급자 API 키를 관리자 웹에서 입력: 쓰기 전용, 비밀번호 재확인, 유효성 확인, AES-256-GCM 암호화 저장 (41acc14, 2333629, e9a7126)
- AI 공급자 모델 선택 드롭다운 (8ddb9a5, f24740d)
- AI 코디 추천 API `POST /api/v1/outfits/recommendations`: AI가 점수 기준에 맞춰 후보를 고르고 앱 엔진이 채점. 앱 AI 코디 탭이 서버 추천을 쓰고 실패하면 규칙 추천 버튼 ([doc/09](doc/09_AI_코디_추천.md)) (69ce5eb, bac708d, d0543f5)
- 디자인 테마 두 개(냥깨비·Closeta): 마스코트 6자세 그림, 앱 런타임 전환 테마 시스템, 관리자 앱 설정 > 테마 화면, 앱 테마 설정 API ([doc/12](doc/12_디자인_테마.md)) (66724c6, 9a006bb, dbe4ed2, 6bb7ea3, 188ed90, 77dd84f)
- 마스코트 Closeta 자세 4개를 패널 그림으로 교체 (136a8c6, 작성: kang6123)
- 사용하지 않는 옷(이유·기록 보존)과 완전 삭제 ([doc/13](doc/13_옷_사용_중지.md)) (906900d, 25559ee, 77dd84f)
- 로그인 세션·Refresh 토큰을 Valkey로 옮기고, 관리자 '로그인 사용자' 화면과 강제 로그아웃 추가. 앱은 `X-Wearly-Client` 헤더를 보냄 ([doc/16](doc/16_세션_Valkey.md)) (5bc1e60, 680d6ce, 4c8e642)
- 백엔드 실시간 로그 스크립트 (3489394)
- 백엔드 응답 동일성 스냅샷 테스트, 애플리케이션 서비스 단위 테스트, ArchUnit 구조 규칙 (7ce39fb, ea0f42b, 4deeb83)

#### 변경
- 백엔드를 헥사고날 구조로 리팩터링(JPA 쓰기 + MyBatis 읽기). 패키지 상세 구조와 입출력 모델(Request → 입력 DTO → 유스케이스 → 출력 DTO → Response) 규칙 적용 ([doc/08](doc/08_백엔드_구조.md)) (97509d4, 8d9acab, bdc446a)
- 아키텍처 점검 21개 항목(높음 2·중간 9·낮음 10) 처리: 코드 사용처 SPI, 도메인 불변식, 프롬프트·검증을 도메인으로, 공급자 재시도 `AiCallRunner`, `Patch<T>`, AI 일일 한도 예약 방식, 공유 커널 분리 등 ([doc/11](doc/11_백엔드_아키텍처_점검.md)) (d6dda2e~9821b86, 5410789)
- 백엔드 코드 스타일을 Google Java Style로(Spotless + Checkstyle), 관리자 사이트는 화살표 함수·`I`/`T` 접두사·zustand·본문/import 순서를 ESLint로 강제, Prettier·VS Code 저장 시 자동 정리 (4aa3d05, 212deb9, 0e10e13, 5f2749a, 784343d, 9581e0e, 7b7e5ec, ad0c2f4)
- 백엔드 설정을 yml로 옮기고 `.env` 폐지. `docker compose`로 DB·백엔드·관리자 사이트를 한 번에 실행 (57ea59f, fff87c1)

#### 문서
- 백엔드 요청 흐름([doc/10](doc/10_백엔드_흐름.md)), 점검 보고서([doc/11](doc/11_백엔드_아키텍처_점검.md)), DB 명령([doc/14](doc/14_DB_명령.md)), 터미널 명령([doc/15](doc/15_터미널_명령.md)) (1d56f95, 3f1ca44, c4b8d70, 540014e)

### 2026-10-18

#### 추가
- 날씨: 서버가 WeatherAPI.com에서 지역별로 주기 수집·저장, 앱 조회 API와 지역 고르기, 관리자 날씨 메뉴(스케줄·공급자·호출 이력·저장된 날씨) ([doc/17](doc/17_날씨.md)) (feaf9fa, 376b1c1, 98d7257, 31ed54b) (작성: kang6123)
- 옷 사진을 서버에 저장: S3 호환 저장소(로컬 MinIO), 서명 URL로 올리기·받기, EXIF 제거·축소 ([doc/18](doc/18_사진_저장.md)) (6b3bcbd, 1a88131)
- 장소별 날씨: 예정일 코디에 장소를 골라 날씨 표시. 예보 밖 날짜는 평년값(Open-Meteo) ([doc/19](doc/19_장소별_날씨.md)) (9c11f3c, d00a4e5)
- 지명 한글 검색(Open-Meteo 지오코딩, 시·도 우선, 국내 지명 보정) (222d2cc, 10e60a3)
- 전국 시·군·구 268곳 장소 검색: 해운대·분당·부산 중구처럼 구·시·군 이름이 바로 나온다. 자료 `backend/src/main/resources/data/korea-districts.json`(근사 좌표 25곳)과 생성 스크립트 `backend/scripts/build-districts.py`, 이름 그대로·줄기 이름·앞부분 맞추기, 겹치는 이름은 "부산 중구"처럼 구분, 결과는 `regionCode` null + 상위 시·도 `parentRegionCode`. 이름이 맞으면 지오코딩을 부르지 않아 회원 하루 검색 한도를 쓰지 않는다 ([doc/19](doc/19_장소별_날씨.md) 4장) (4d65280, 808a3c2, 04da1a3)
- 오류 응답을 코드값으로 통일(`status`·`code`·`params`·`fieldErrors`·`traceId`)하고 오류 분류·`traceId`로 로그 찾기. 앱 API 모듈·하네스가 이 형식을 받도록 수정 ([doc/20](doc/20_오류_응답.md)) (ba2ec77, 6956af1(클라우드 PR #7 병합), 9cfe640)
- 오류 코드를 한국어 문구로 보여주는 표: 앱(서버 코드 35·필드 21·앱 5)과 관리자, 시스템 오류(5xx)에는 오류 번호 표시, `error-codes.json`과 문구 표 일치 검사 (837fe16, 362a0fb)
- 예정 코디 서버 저장 `/plans`(목록·만들기·bulk·PATCH·삭제, 500개 한도 `PLAN_LIMIT`)와 앱 동기화(`clientId`·합치기·대기열) ([doc/21](doc/21_예정_코디_저장.md)) (0fdc1d7, 2f90f2b, 15eac69)
- 관리자: 날씨 호출 이력에 종류(정기·다른 장소·검색·평년값)와 필터, Open-Meteo 이번 달 호출 수 (362a0fb, 2f90f2b)
- AI 호출 프롬프트 캐싱(사진 분석·코디 추천): 고정 지시문(지시·허용 값·점수 기준)을 system으로 분리하고 Claude는 끝에 `cache_control`(5분)을 붙이며, 호출 성공 로그에 `AI {feature} 성공 … input= output= cacheRead= cacheWrite=`를 남기고 DB의 입력 토큰은 캐시 포함 입력 전체로 기록한다. 실측(2026-10-18, claude-haiku-5-5 사진 분석 2회): 1번째 `cacheWrite` 2,654 → 2번째 `cacheRead` 2,654, 입력 비용 약 85% 절감, 응답 3.8초 → 1.1초. 설정 `wearly.ai.prompt-cache.enabled`(기본 켬). 캐시 토큰을 담는 DB 칸은 아직 없다([진행 중](#진행-중-아직-dev에-없음)) ([doc/18](doc/18_사진_저장.md) 13장, [doc/09](doc/09_AI_코디_추천.md)) (5c457da, 93b7209)
- 예정 코디 D-1 알림: 앱 로컬 알림(`expo-notifications`, 예정 전날 설정 시각 기본 20:00·30분 단위, iOS 60개 상한, 권한 처음 한 번, 마이 화면 설정, 누르면 그 예정의 PLAN 모드)과 서버 `notify` 컨텍스트(`PUT·DELETE /me/push-token`, `V17` `tb_push_token`·`tb_push_send_log`, 매일 19:30 KST D-1 작업이 장소 날씨를 다시 확인해 기온 3°C·비 여부가 바뀌면 "예보가 바뀌었어요", Expo Push 어댑터, 설정 `wearly.notify.*`, 기본 꺼짐·provider `log`). Expo Go에서는 로컬 알림만 되고 원격 푸시는 안 된다 ([doc/23](doc/23_D-1_알림.md), [doc/21](doc/21_예정_코디_저장.md) 9장) (a4aad23, d28e382)
- 계절 테마: 테마 위에 계절 색 10개를 덮어쓴다. 서버 `seasonMode`(`OFF` 기본·`AUTO`·`SPRING`~`WINTER`, `GET /app/config`·`PUT /admin/app/theme`, 마이그레이션 없음), 앱은 한국 날짜로 계절 계산·글자 대비 WCAG AA 4.5:1 검사·홈 계절 인사·마이 화면에서 끄기, 관리자는 계절 모드 선택(확인 대화상자)과 4계절×2테마 미리보기 ([doc/12](doc/12_디자인_테마.md) 7장, 이슈 #6) (68b6795, ee8eeb3)
- 나의 정보: 연령대·체형(스트레이트·웨이브·내추럴)·키·핏 선호. 코드 그룹 `AGE_RANGE`·`BODY_TYPE`·`FIT`와 프로필 칸(`V21`), 프로필 API 필드·검증, 앱 더보기 > 나의 정보 화면·온보딩 STEP 4(건너뛸 수 있음)·체형 설명 카드, 관리자 회원 표 조회와 BODY_TYPE 설명 입력, AI 코디 추천의 사용자 메시지에 "회원 정보" 한 줄 ([doc/06](doc/06_코드_관리.md), [doc/05](doc/05_백엔드_API.md), [doc/09](doc/09_AI_코디_추천.md), 이슈 #5) (6693a29, e41d387)
- `dev.sh`: compose 실행, 백엔드·DB 실시간 로그 창, Expo 창을 한 번에 ([doc/15](doc/15_터미널_명령.md)) (0cb0b3b)
- 실시간 로그에 날씨 수집 결과·실패한 공급자 호출·저장된 사진 표시 (e8b85b9)
- 관리자 2단계 인증(TOTP): 관리자 사이트 로그인이 5분짜리 대기 토큰을 주고 인증 앱 등록(QR)·6자리 코드 확인·복구 코드 10개(처음 한 번)를 거친다. 5번 틀리면 15분 잠금, 코드 재사용 거부, 비밀은 AES-256-GCM 암호화(`V15`), 확인하지 않은 세션은 관리자 API 403 `MFA_REQUIRED`. `POST /auth/mfa/setup·verify`, `/admin/me/mfa/*`, `DELETE /admin/users/{id}/mfa`. 설정 `wearly.admin.mfa.required`(기본 켬)·`issuer` ([doc/22](doc/22_관리자_계정_관리.md) 4.1.4) (3b84994, f3fbfae)
- 관리자 계정 보완: 비밀번호 변경(`POST /admin/me/password`, 다른 세션 끊기), 관리자 지정·해제(마지막 관리자 해제는 409 `LAST_ADMIN`), 관리자 로그인 기록 `GET /admin/auth-events`와 화면(`V16`: `LOGIN_FAILED`·`MFA_SUCCESS`·`MFA_FAILED`·`MFA_LOCKED`), 관리자 사이트 "내 계정"·회원 표의 역할 지정·2단계 인증 초기화, 처음 로그인 안내와 휴대폰을 잃었을 때 대처 ([doc/22](doc/22_관리자_계정_관리.md) 4.1.5~4.1.7, [doc/07](doc/07_관리자_사이트.md)) (3b84994, f3fbfae)
- 관리자 사이트 회사 계정 로그인(SSO, 범용 OIDC) 2단계: 서버 주도 Authorization Code + PKCE(S256)로 `GET /auth/sso/providers`·`/{p}/start`·`/{p}/callback`·`POST /auth/sso/exchange`·`/{p}/logout`. state·nonce·verifier는 Valkey 5분 + state 쿠키, 교환 코드는 1분·1회라 토큰을 URL에 싣지 않고, ID 토큰은 JWKS 서명(RS·PS·ES)·iss·aud/azp·exp·iat·nonce·sub를 확인한다. IdP 계정은 발급자+sub로 `tb_user_sso`(`V22`)에 연결하고, 같은 이메일의 일반 회원(고객)은 올리지 않고 비밀번호 없는 관리자를 따로 만든다. 로그인마다 IdP 그룹(`admin-groups`·`admin-emails`)으로 역할을 맞추고(아니면 403 `SSO_NOT_ADMIN`, 연결된 관리자는 `USER`로 내리고 세션 끊기), `trust-idp-mfa`(기본 true)면 앱 TOTP 없이 들어온다. 로그인 이력 `SSO_LOGIN`·`SSO_DENIED`(+`sso_provider`·`sso_amr`), 변경 이력 `SSO_ACCOUNT_CREATE`·`SSO_ACCOUNT_LINK`. 기본은 꺼짐이고 비상 계정(비밀번호 + TOTP)은 그대로. 로컬 Keycloak(`docker compose --profile sso up -d keycloak`, 8180, realm `infra/keycloak/wearly-realm.json`), 관리자 로그인 화면 회사 계정 버튼·`/sso/callback`·IdP 로그아웃, Entra ID·Google Workspace 설정 견본 ([doc/22](doc/22_관리자_계정_관리.md) 4.2, [doc/10](doc/10_백엔드_흐름.md) 3.15) (302aaca)
- 옷 색 복수(1~3개, 첫 번째가 주 색상)·구입일·계절 코드 3개: 아이템 `colors`(예전 `color`도 받음)와 `purchasedAt`·`purchasedAtEstimated`(비우면 등록일), 계절 `EARLY_SPRING`·`EARLY_SUMMER`·`EARLY_FALL`(`V20`), AI 사진 분석 결과 `colors`, 앱 색 칩 ①②③·구입일 입력, 관리자 시험 분석 색상 행 ([doc/05](doc/05_백엔드_API.md), [doc/06](doc/06_코드_관리.md), [doc/18](doc/18_사진_저장.md) 7.5, 이슈 #4) (78f9e62, 859ae6a)
- 옷 상세에서 사진 바꾸기·빼기와 AI 다시 분석, 작은 사진 안내(긴 변 600 미만, 등록 2·3단계에도): 사진 시트(다시 찍기·앨범·빼기) → 1600 JPEG 업로드 → PATCH `imageId`(빼기는 `null`), 새 사진 뒤 누를 때만 분석해 바뀌는 항목을 "이전 → 새 값"으로 미리 보고 적용, 게스트 기기 사진은 `photos/{itemId}-{버전}.jpg`로 교체. 앱 단위 테스트 13개 추가(합계 24) ([doc/18](doc/18_사진_저장.md) 9.5장, [doc/04](doc/04_목업_앱.md)) (f541fa0, 이슈 #8)

#### 변경
- 사진 크기 줄이기: 보관 1600px·품질 0.8, AI 전송 1024px. 2048 대비 이미지 토큰 약 74% 감소(4,082 → 1,048) ([doc/18](doc/18_사진_저장.md)) (837fe16, 0fdc1d7)
- 원격 `dev`(날씨 기능)와 사진 저장 병합 (e4ab5b8)
- 시작 때 관리자 계정은 **관리자가 한 명도 없을 때만** 만든다. 설정 이메일로 먼저 가입한 일반 회원을 관리자로 올리던 동작을 없앴고(`ADMIN_BOOTSTRAP_PROMOTE` 삭제), 설정 비밀번호는 첫 생성에만 쓴다 ([doc/22](doc/22_관리자_계정_관리.md) 4.1.1) (3b84994)
- 오류 코드 36개 → 44개 (관리자 2단계 인증·`LAST_ADMIN` 8개), 앱·관리자 문구 표와 일치 검사도 함께 ([doc/20](doc/20_오류_응답.md) 12장) (3b84994)
- 추천 엔진: 보조 색 점수(보조 색 짝 +0.05, 색 반복 +0.05, 보조 포인트 과다 −0.1, 선호 색 +0.05)와 기온별 계절 표에 초봄·초여름·초가을, AI 코디 추천 프롬프트에 보조 색 ([doc/09](doc/09_AI_코디_추천.md) 2.1·2.2장) (78f9e62)
- 아이템 목록 `color` 필터는 보조 색까지, 통계 `byColor`는 주 색상 기준, 코드 사용 수(`CODE_IN_USE`)는 보조 색 포함 (78f9e62)
- 오류 코드 44개 → 51개 (회사 계정 로그인 `SSO_*` 7개), 앱·관리자 문구 표와 일치 검사도 함께 ([doc/20](doc/20_오류_응답.md) 13장) (302aaca)
- 하네스 시나리오 57개로 늘림(2단계 인증 45번, 푸시 토큰 57번, 나의 정보 9번, 계절 모드 34번). `harness/run.sh`는 `WEARLY_ADMIN_MFA_REQUIRED=false`와 고정 마스터 키로 백엔드를 띄운다 (3b84994)
- 하네스 시나리오 58개로 늘림(SSO 꺼짐 기본 동작 46번이 끼어 이후 번호가 하나씩 밀림). `harness/run.sh`는 `WEARLY_SSO_ENABLED=false`로 띄운다 (302aaca)
- 검사 현황(2026-10-18 SSO 반영 뒤): 백엔드 634개, 하네스 58개, 관리자 단위 43개, 앱 `check:errors` 서버 51·필드 21·앱 5. compose 재빌드 뒤 Flyway `V22` 적용, `/auth/sso/providers`는 `{ enabled: false, providers: [] }`(로컬 설정에서 아직 꺼 둠). 로컬 Keycloak은 Python으로 로그인 폼을 POST해 전체 흐름 성공·교환 코드 재사용 401·`viewer@wearly.local` `SSO_NOT_ADMIN`·로그아웃 302, docker 프로필 그대로도 성공(iss 불일치 없음), 헤드리스 Chrome으로 관리자 화면 로그인 성공 ([doc/22](doc/22_관리자_계정_관리.md) 4.2.9)
- 검사 현황(2026-10-18 20:12 compose 재빌드 뒤): 백엔드 602개, 하네스 57개, 관리자 단위 43개, 앱 단위 11개. Flyway V15·V16·V17·V20·V21 적용 확인

#### 수정
- Flyway V12 마이그레이션을 적용된 원본으로 되돌림(주석만 바꿔도 체크섬이 달라져 서버가 뜨지 않음) (e7ed166)
- Docker 빌드에서 `gradlew` 줄바꿈을 LF로 맞춤(Windows autocrlf 대응) (9ffb102) (작성: kang6123)
- 불필요한 `baseUrl`·`SuppressWarnings` 제거 (869417d) (작성: kang6123)

#### 보안
- 날씨 API 키가 견본 파일(`backend/config/application-local.example.yml`)에 실제 값으로 커밋된 것(079eb7c)을 견본에서 지우고 git 제외 파일로 옮김 (e4ab5b8). 키는 git 기록에 남아 있으므로 weatherapi.com에서 재발급 권장
- 입력 검증 실패·요청 형식 오류 로그에 입력한 값(비밀번호 등)이 남던 문제 수정: 필드 이름·코드와 예외 종류만 기록, 테스트로 확인 (a8db2a2)
- 공개 가입 API로 관리자 이메일을 선점해 다음 시작 때 관리자가 되는 구멍을 막음: 기존 일반 회원은 관리자로 올리지 않고 경고 로그만 남긴다(이메일은 앞 2글자만) ([doc/22](doc/22_관리자_계정_관리.md) 2장 a) (3b84994)
- 관리자 사이트 회사 계정 로그인(SSO)의 보안 규칙: 토큰을 URL에 싣지 않고(교환 코드 1분·1회), PKCE S256·state·nonce와 state 쿠키로 로그인 CSRF·코드 가로채기를 막고, 인가 응답 `iss`(RFC 9207)와 ID 토큰 `iss`를 글자 그대로 비교하며, 서명은 비대칭 알고리즘만 받고, 돌아갈 주소는 허용 목록의 정확히 같은 주소만(열린 리디렉트 방지). 같은 이메일의 고객 계정을 관리자로 올리지 않고(직원·고객 분리), 관리자 그룹에서 빠진 연결 관리자는 다음 로그인 때 내리고 세션을 끊는다. 클라이언트 비밀은 로그에 나오지 않는다 ([doc/22](doc/22_관리자_계정_관리.md) 4.2) (302aaca)
- 관리자 사이트에 2단계 인증(TOTP)과 로그인 기록을 더함: 비밀번호만으로 관리자 API에 들어가지 못하고, 코드는 5번 틀리면 15분 잠기며, 같은 코드는 재사용할 수 없다. TOTP 비밀·복구 코드는 암호화/해시로만 저장하고 로그·응답·감사 이력에 남기지 않는다 ([doc/22](doc/22_관리자_계정_관리.md) 4.1.4) (3b84994)

#### 문서
- 날씨([doc/17](doc/17_날씨.md)), 사진 저장([doc/18](doc/18_사진_저장.md)), 장소별 날씨([doc/19](doc/19_장소별_날씨.md)), 오류 응답([doc/20](doc/20_오류_응답.md)), 예정 코디 저장([doc/21](doc/21_예정_코디_저장.md)), 관리자 계정 관리 결정([doc/22](doc/22_관리자_계정_관리.md)) (feaf9fa, 80aecc5, cc80431, f46b7c5, 0a13fe5, 39e8782)
- 관리자 계정 관리([doc/22](doc/22_관리자_계정_관리.md)) 4.2를 "구현함"으로: 로그인 흐름·API·콜백 확인 순서·계정 연결 규칙·설정 표·로컬 Keycloak·Entra/Google 전환·오류 코드·확인 내역·한계, 5.1에 실제 IdP 연결 체크리스트. 오류 응답([doc/20](doc/20_오류_응답.md)) 13장, 요청 흐름([doc/10](doc/10_백엔드_흐름.md)) 3.15, API([doc/05](doc/05_백엔드_API.md))·관리자 사이트([doc/07](doc/07_관리자_사이트.md))·백엔드 구조([doc/08](doc/08_백엔드_구조.md))·DB 명령([doc/14](doc/14_DB_명령.md))·터미널 명령([doc/15](doc/15_터미널_명령.md))·각 README(하네스 시나리오 번호 정리 포함) 반영 (302aaca)
- D-1 알림([doc/23](doc/23_D-1_알림.md)) 새로. 관리자 계정 관리([doc/22](doc/22_관리자_계정_관리.md)) 1단계를 "구현함"으로 고치고 구현 상세·처음 로그인 안내·휴대폰 분실 대처 추가. 디자인 테마([doc/12](doc/12_디자인_테마.md))에 계절 테마 7장. API([doc/05](doc/05_백엔드_API.md))·코드 관리([doc/06](doc/06_코드_관리.md))·관리자 사이트([doc/07](doc/07_관리자_사이트.md))·구조([doc/08](doc/08_백엔드_구조.md))·AI 추천([doc/09](doc/09_AI_코디_추천.md))·흐름([doc/10](doc/10_백엔드_흐름.md) 3.13·3.14)·DB 명령([doc/14](doc/14_DB_명령.md))·터미널 명령([doc/15](doc/15_터미널_명령.md))·사진 저장([doc/18](doc/18_사진_저장.md) 13장 프롬프트 캐싱)·장소별 날씨([doc/19](doc/19_장소별_날씨.md))·오류 응답([doc/20](doc/20_오류_응답.md) 12장)·예정 코디([doc/21](doc/21_예정_코디_저장.md) 9장)·목업 앱([doc/04](doc/04_목업_앱.md) 5.1장)과 backend·admin·harness README를 이번 작업에 맞춰 갱신

## 진행 중 (아직 `dev`에 없음)

- AI 호출 기록의 캐시 토큰 DB 칸(`tb_ai_analysis_log`에 캐시 읽기·쓰기 토큰, `V19` 예정): 지금은 로그 줄에만 있다 ([doc/18](doc/18_사진_저장.md) 13.4)
- 원격 푸시 실기기 확인(EAS 프로젝트·APNs·FCM 준비 필요): 지금은 앱 로컬 알림만 쓴다 ([doc/23](doc/23_D-1_알림.md) 9장)
