# 연동 하네스 (`harness/`)

앱의 API 모듈(`mobile/src/api/`)과 백엔드(`backend/`)를 **실제로 연결해** 계약(`doc/05_백엔드_API.md`)대로 동작하는지 확인하는 도구다.

## 왜 따로 두나

- 앱과 백엔드를 서로 다른 곳에서 만들고 있어서, 각자의 단위 테스트가 통과해도 둘이 맞물리는지는 알 수 없다.
- 하네스는 앱이 쓰는 코드(`mobile/src/api/client.ts`의 `createApiClient`, `tokenStore.ts`의 `createMemoryTokenStore`)를 **그대로 import해** Node에서 서버를 부른다. 그러니 PASS는 "앱이 이 서버와 이렇게 동작한다"는 뜻이다.
- 오류 본문 형식, 401 코드처럼 앱 모듈이 감춰 버리는 부분은 원시 `fetch`로 따로 확인한다.
- 토큰 만료·자동 갱신·AI 한도는 평소 설정(30분, 하루 10회)으로는 확인하기 어렵다. 그래서 Access 수명 3초, AI 한도 3회로 백엔드를 따로 띄운다 (계약 9장).

## 실행

```bash
cd harness && npm install     # 처음 한 번
./run.sh
```

`./run.sh`가 하는 일:

1. DB 컨테이너(`wearly-postgres`)가 꺼져 있으면 `docker compose up -d`로 켜고 healthy가 될 때까지 기다린다.
2. 하네스 전용 DB `wearly_harness`를 지우고 새로 만든다. 개발용 `wearly` DB는 건드리지 않는다.
3. `../backend`를 `./gradlew bootRun`으로 포트 **18080**에 띄운다. 로그는 `harness/.logs/backend.log`.
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/wearly_harness`, `SERVER_PORT=18080`
   - `WEARLY_ACCESS_TTL=PT3S`, `WEARLY_AI_DAILY_LIMIT=3`, `WEARLY_AI_PROVIDER=mock`
   - `JAVA_HOME`은 JDK 21. `WEARLY_JWT_SECRET`이 없으면 하네스용 값을 넣고, `ANTHROPIC_API_KEY`는 비운다.
4. `/actuator/health`가 UP이 될 때까지 최대 180초 기다린다. 그 안에 뜨지 않거나 프로세스가 죽으면 로그 끝부분을 보여 주고 실패로 끝난다.
5. 시나리오를 실행하고 결과 표를 출력한다.
6. 끝나면(성공·실패·Ctrl+C 모두) 백엔드 프로세스 그룹과 18080 포트를 쓰는 프로세스를 끈다.

`backend/`가 아직 없으면 안내 문구를 보여 주고 끝난다.

### 옵션

| 옵션 | 설명 |
|---|---|
| `--keep` | 끝나도 백엔드를 남긴다. 끄는 명령을 출력해 준다. |
| `--external <url>` | DB 리셋·백엔드 기동 없이 이미 떠 있는 서버로 시나리오만 돌린다. Access 수명 3초에 기대는 시나리오 3개는 건너뛴다(SKIP). AI 한도 시나리오는 응답의 `remainingToday`를 따라가므로 그대로 돈다. |
| `--only <키워드,...>` | 분류나 이름에 키워드가 들어간 시나리오만 실행한다. 예: `--only 토큰,AI` |
| `--list` | 시나리오 목록만 출력한다. |
| `--db-only` | `wearly_harness` DB만 새로 만들고 끝낸다. |

| 환경변수 | 기본값 |
|---|---|
| `HARNESS_JAVA_HOME` | `/Users/showzone/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home` (없으면 `/usr/libexec/java_home -v 21`) |
| `HARNESS_BOOT_TIMEOUT` | `180` (초). 첫 실행에서 Gradle 내려받기가 길면 늘린다. |
| `HARNESS_BACKEND_DIR` | 저장소 루트의 `backend/` |

자주 쓰는 조합:

```bash
./run.sh --keep                               # 돌리고 서버는 남겨 두기
./run.sh --external http://localhost:18080    # 남겨 둔 서버로 다시 (빠름)
./run.sh --external http://localhost:8080     # 개발 중인 서버(평소 설정)에 대고 확인
npm run typecheck                             # 하네스 + mobile/src/api 타입 검사
```

## 시나리오

시나리오마다 무작위 이메일(`h-<태그>-<uuid>@harness.wearly.test`)로 새 사용자를 만들어 서로 영향을 주지 않는다.

| # | 분류 | 확인하는 것 |
|---|---|---|
| 1 | 기본 | `/actuator/health` → `UP` |
| 2 | 기본 | 토큰 없이 보호 API → 401 `UNAUTHORIZED`, 오류 JSON 형식(`status`·`code`·`message`·`fieldErrors[]`), 형식이 틀린 토큰 → `TOKEN_INVALID` |
| 3 | 기본 | CORS: Expo 웹 출처 3곳은 허용, 다른 출처는 거부 |
| 4 | 인증 | 회원가입 → 저장소에 토큰 저장, `users.me()` 일치, 이메일 소문자 저장, `AuthResponse` 모양(만료 초, Refresh는 base64url 43자) |
| 5 | 인증 | 같은 이메일을 대소문자만 바꿔 재가입 → 409 `EMAIL_TAKEN`, 저장소 비어 있음 |
| 6 | 인증 | 비밀번호(숫자 없음·영문 없음·8자 미만·64자 초과), 이름(빈 값·20자 초과), 이메일 형식 → 400 `VALIDATION_FAILED` + 해당 `fieldErrors` |
| 7 | 인증 | 로그인 성공(이메일 대소문자 무관), 틀린 비밀번호·없는 이메일 → 401 `INVALID_CREDENTIALS` |
| 8 | 프로필 | 처음 GET → 앱 `DEFAULT_PROFILE`과 같은 기본값 + `onboarded: false`, PUT 응답·GET 일치, `name`이 `users.me()`에 반영, 빈 이름 → 400 |
| 9 | 옷장 | create → get → PATCH 일부 필드(나머지 유지, `brand: null`로 지우기) → remove → get 404 |
| 10 | 옷장 | 목록 필터 `category`·`status`·`color`·`q`(이름·브랜드 부분 일치)·조합, 정렬 `recent`(기본)·`worn`·`name`, 없는 category 값 → 400 |
| 11 | 옷장 | 잘못된 category·subType 짝 → 400 (등록, PATCH로 category만 바꾸기), formality 4·빈 이름 → 400 |
| 12 | 옷장 | `bulkCreate` 응답이 요청 순서대로, 101개 → 400, 중간에 틀린 항목이 있으면 하나도 저장 안 됨 |
| 13 | 옷장 | `stats`: total, byCategory(0 포함), byStatus, byColor·byStyle(많은 순), wornLast30Days, utilization30d(소수 둘째 자리), sleeping, 삭제·추가 뒤 다시 계산 |
| 14 | 옷장 | 다른 사용자의 아이템 get·PATCH·DELETE → 404, 목록·통계에 안 보임 |
| 15 | AI | 작은 PNG를 코드로 만들어 분석 → provider `mock`, 앱 허용 값 안의 제안(짝 포함), 같은 이미지 = 같은 결과, `remainingToday` 1씩 감소, 사용자별 한도 분리, 파트 이름 `image` |
| 16 | AI | 한도(3회)까지 쓴 뒤 → 429 `AI_LIMIT_EXCEEDED` |
| 17 | AI | 10MB 넘는 이미지 → 413 `IMAGE_TOO_LARGE`, 실패한 호출은 한도에서 빼지 않음 |
| 18 | 토큰 | refresh 회전(새 토큰 발급), 이미 회전된 토큰 재사용 → 401 `REFRESH_REUSED`, 그 뒤 최신 토큰도 무효(family 폐기), 없는 토큰 → `REFRESH_INVALID` |
| 19 | 토큰 | 4.5초 기다려 Access 만료 → 서버는 `TOKEN_EXPIRED`, 앱 모듈은 자동 갱신 후 성공, 저장소의 access·refresh가 모두 바뀜, refresh 1번 |
| 20 | 토큰 | 만료 상태에서 요청 5개를 동시에 → 모두 성공, `/auth/refresh`는 **1번**, `tokenStore.set`도 1번 (single-flight) |
| 21 | 토큰 | 재사용 감지로 family가 폐기된 뒤 만료 → 앱 모듈이 저장소를 비우고 `onAuthLost`를 1번 부르고 원래 오류(`TOKEN_EXPIRED`)를 던짐 |
| 22 | 계정 | logout → 저장소 비움, 그 refresh로 갱신 불가, 없는 토큰 logout도 204, 다른 세션은 영향 없음 |
| 23 | 계정 | 회원 탈퇴 → 기존 access 401, refresh 401, 로그인 `INVALID_CREDENTIALS`, 같은 이메일로 재가입하면 빈 옷장·기본 프로필 |

19~21은 Access 수명 3초가 필요해서 `--external`에서는 건너뛴다.

refresh 횟수는 `createApiClient`의 `fetch` 주입 옵션으로 세션마다 요청을 기록해 센다. `tokenStore`도 감싸서 `set`·`clear` 횟수를 센다.

### 계약에 정해지지 않아 하네스가 고른 기대값

계약 문서에 명시되지 않은 부분은 다음처럼 기대한다. 백엔드와 다르면 둘 중 하나를 고치고 계약 문서에도 적는다.

- `sort=worn`은 `wornCount` 많은 순이다 (앱 옷장 화면의 '많이 입은 순'과 같음).
- PATCH에서 `brand: null`은 브랜드를 지운다는 뜻이다 (필드를 빼면 유지).
- 일괄 등록은 전부 저장되거나 하나도 저장되지 않는다.
- 저장 전 프로필의 `name`은 가입 이름이나 `'웨어리 사용자'` 둘 다 허용한다. 실제 값은 '참고'에 남긴다.
- family가 폐기된 뒤 최신 refresh 토큰의 오류 코드는 `REFRESH_INVALID`, `REFRESH_REUSED` 둘 다 허용한다. 실제 값은 '참고'에 남긴다.

## 결과 읽는 법

```
#  | 결과 | 분류 | 시나리오                                  | 시간
---+------+------+-------------------------------------------+-----
 1 | PASS | 기본 | 헬스 체크 /actuator/health → UP           |  2ms
13 | FAIL | 옷장 | stats 수치 (...)                          | 35ms
19 | SKIP | 토큰 | Access 만료(4.5초 대기) → 자동 갱신 ...   |    -

실패 상세
✗ #13 [옷장] stats 수치 (...)
  검사: utilization30d = 2/6 → 0.33
  기대: 0.33
  실제: 0.3333333333333333

통과 21 · 실패 1 · 건너뜀 1 · 총 14.2s
```

- **PASS / FAIL / SKIP**: 시나리오 하나 안의 검사가 모두 통과하면 PASS다. 하나라도 틀리면 거기서 멈추고 FAIL이 된다.
- **실패 상세**: 어떤 검사가 틀렸는지(`검사`)와 기대값·실제값. 앱 모듈이 던진 오류는 `ApiError 401 TOKEN_EXPIRED: …` 형식으로 보인다. 예상하지 못한 예외는 스택 일부를 함께 보여 준다.
- **건너뜀**: 왜 건너뛰었는지. 서버에 연결할 수 없으면 모든 시나리오를 건너뛰고 실패로 끝난다.
- **참고**: 계약이 여러 값을 허용하는 곳에서 서버가 실제로 준 값.
- **종료 코드**: 실패가 있거나 서버에 닿지 못하면 1, 아니면 0.
- 백엔드 쪽 원인은 `harness/.logs/backend.log`에서 찾는다.

## 파일

| 파일 | 내용 |
|---|---|
| `run.sh` | DB 준비 → 백엔드 기동·종료 → 러너 실행 |
| `src/main.ts` | 러너. 인자 해석, 시나리오 실행, 결과 표 |
| `src/scenarios/*.ts` | 분류별 시나리오 (`auth`·`profile`·`wardrobe`·`ai`·`tokens`·`account`) |
| `src/lib/session.ts` | 앱 모듈로 클라이언트 만들기, 요청·토큰 저장·`onAuthLost` 기록 |
| `src/lib/http.ts` | 원시 요청과 오류 본문 형식 검사 |
| `src/lib/assert.ts` | 검사 함수, 기대값·실제값을 담는 실패 |
| `src/lib/fixtures.ts` | 아이템 입력, 기본 프로필 사본, 서울 기준 날짜 |
| `src/lib/png.ts` | 단색 PNG 생성 |

`tsconfig.json`은 `../mobile/src/api`도 함께 타입 검사한다. 앱 API 모듈은 계약 8장에 따라 `react-native`·`expo-*`·`@/` 없이 순수 TypeScript여야 한다. 이 규칙이 깨지면 `npm run typecheck`가 먼저 실패한다.

앱 `DEFAULT_PROFILE`(`mobile/src/data/seed.ts`)은 `@/` 별칭을 써서 Node에서 바로 가져올 수 없다. 그래서 `src/lib/fixtures.ts`에 사본을 두었다. 앱 기본값을 바꾸면 이 사본도 같이 고친다.
