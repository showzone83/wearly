/**
 * 실행 모드.
 *
 * - 기본(`npm start`): 실제 백엔드만 쓴다. 로그인 없이 쓰는 게스트 모드와 기기 안 목업 AI 분석이 없고,
 *   서버 호출이 실패하면 목업으로 대신하지 않고 오류를 그대로 보여 준다.
 * - 목업(`npm run start:mock`): 백엔드 없이도 돌 수 있게 게스트 모드와 기기 목업 분석을 켠다.
 *
 * EXPO_PUBLIC_ 변수는 번들할 때 코드에 박히므로, 모드를 바꾸면 개발 서버를 다시 띄워야 한다.
 */
export const MOCK_MODE = process.env.EXPO_PUBLIC_WEARLY_MOCK === '1';
