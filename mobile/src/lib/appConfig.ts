// 앱 설정 동기화 (doc/12 3장): 서버가 정한 테마를 읽어 useThemeStore에 넣는다.
// 테마는 화면 꾸밈이라 실패(서버 없음·404·형식 오류)해도 조용히 기기 저장값으로 계속한다.
import { api } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { useThemeStore } from '@/store/useThemeStore';

let inflight: Promise<void> | null = null;

/** GET /app/config → 테마 반영. 동시에 여러 번 불러도 요청은 하나만 보낸다 */
export const syncAppConfig = (): Promise<void> => {
  // 목업 실행은 서버 대신 마이 탭의 개발용 전환 버튼으로 테마를 바꾼다
  if (MOCK_MODE) return Promise.resolve();
  if (!inflight) {
    inflight = (async () => {
      try {
        const res = await api.app.config(useThemeStore.getState().configEtag);
        if (res.changed) useThemeStore.getState().applyServerTheme(res.config.themeId, res.etag);
      } catch (e) {
        // 백엔드에 아직 API가 없거나(404) 서버에 닿지 못함: 저장된 테마를 그대로 쓴다
        if (__DEV__) console.log('[appConfig] 서버 설정을 받지 못해 저장된 테마를 써요', (e as Error)?.message);
      }
    })().finally(() => {
      inflight = null;
    });
  }
  return inflight;
};
