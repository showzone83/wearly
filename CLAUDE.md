# Wearly (웨어리) 프로젝트

직장인 출근 코디를 "내 옷장에 있는 옷으로" 추천하는 앱. 슬로건: "오늘 뭐 입지? 이제 고민 끝."

## 폴더 구성

- `Wearly_서비스_기획서_v1.1.pptx` — 원본 기획서 (46장, 부록에 화면 목업)
- `doc/` — 지금까지의 분석·결정·작업 기록. **새 대화를 시작하면 `doc/README.md`부터 읽는다.**
  - `01_기획서_분석.md` — 기획서 요약, 유사 서비스(에이클로젯·Alta·Whering 등), 장단점, 보완 제안
  - `02_기술_결정.md` — 네이티브 앱, React Native(Expo), PostgreSQL, API 키·보안, 소셜 로그인 결정과 근거
  - `03_기획서_반영_목록.md` — 기획서 pptx에 반영할 수정 사항 (아직 pptx에 반영하지 않음)
  - `04_목업_앱.md` — 목업 앱 구조, 실행 방법, 추천 엔진 설명, 검증 내역, 알려진 한계
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
cd mobile && npm run typecheck && npx expo-doctor
```

- `CI=1`로 실행하지 않는다. Metro가 파일 감시를 끄므로 코드 수정이 반영되지 않는다.
- 이 Mac에는 Xcode가 없어 iOS 시뮬레이터를 쓸 수 없다 (Command Line Tools만 설치됨).
- 패키지 추가는 `npx expo install <패키지>`로 한다 (SDK 호환 버전 자동 선택).

## 목업 앱 핵심 코드

- 추천 엔진: `mobile/src/lib/recommend.ts` — 기획서 p.24 점수 모델 (색상 30, 상황 25, 날씨 20, 취향 15, 다양성 10)
- 진단·처방: `mobile/src/lib/diagnose.ts` — 5개 항목, 강점·개선점, 내 옷장 교체 처방
- 상태 저장: `mobile/src/store/useStore.ts` — zustand + AsyncStorage (`wearly-store-v1`)
- 화면: `mobile/src/app/` (expo-router). 탭은 홈·내 옷장·AI 코디·캘린더·마이
- 목업 처리: 날씨(가짜 예보), AI 사진 분석(가짜 제안값), 푸시·바로 진단(안내 문구만), 소셜 로그인(제외)

## 현재 상태 (2026-10-15)

- 기획서·문서·목업 앱을 루트 저장소 `main` 브랜치에 커밋하고 GitHub에 올렸다.
- 브라우저(375×812)에서 주요 흐름을 검증했다. 실제 휴대폰과 iOS 시뮬레이터에서는 아직 확인하지 않았다.

## 다음 할 일

1. Expo Go로 실제 휴대폰에서 확인
2. `doc/03_기획서_반영_목록.md` 내용을 기획서 pptx에 반영
3. 백엔드(Spring Boot + PostgreSQL)와 소셜 로그인 구현 — 각 사 API 키 발급 필요, Expo Dev Build 필요
