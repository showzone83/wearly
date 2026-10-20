import { useEffect, useState } from 'react';
import { ActivityIndicator, AppState, Platform, Text, View } from 'react-native';

import { Button, Title } from '@/components/ui';
import { router, Stack, usePathname } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { bootstrapSession } from '@/lib/account';
import { API_BASE_URL } from '@/lib/api';
import { syncAppConfig } from '@/lib/appConfig';
import { onAppActive, startPhotoMaintenance } from '@/lib/photoSync';
import { recheckPlans } from '@/lib/placeWeather';
import { onNotifyAppActive, startPlanNotifications } from '@/lib/planNotify';
import { syncPlans } from '@/lib/planSync';
import { syncWeather } from '@/lib/weatherSync';
import { bootstrapCodes, useCodes } from '@/store/useCodes';
import { useNotifyStore } from '@/store/useNotifyStore';
import { usePlaceStore } from '@/store/usePlaceStore';
import { useStore } from '@/store/useStore';
import { useThemeStore } from '@/store/useThemeStore';
import { useWeatherStore } from '@/store/useWeatherStore';
import { makeStyles, useColors } from '@/theme';

type PersistApi = { hasHydrated: () => boolean; onFinishHydration: (fn: () => void) => () => void };

function useHydrated(persistApi: PersistApi) {
  const [hydrated, setHydrated] = useState(persistApi.hasHydrated());
  useEffect(() => {
    const unsub = persistApi.onFinishHydration(() => setHydrated(true));
    setHydrated(persistApi.hasHydrated());
    return unsub;
  }, [persistApi]);
  return hydrated;
}

type CodesPhase = 'loading' | 'ready' | 'failed';

/**
 * 옷 속성 코드 준비 (doc/06 5장). 저장된 코드가 있으면 바로 쓰고 뒤에서 갱신한다.
 * 기본 실행에서 저장된 코드도 없고 서버도 안 되면 'failed' → 재시도 화면.
 */
function useCodesReady(hydrated: boolean) {
  const [phase, setPhase] = useState<CodesPhase>('loading');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!hydrated) return;
    let alive = true;
    setPhase('loading');
    bootstrapCodes()
      .then((ok) => alive && setPhase(ok ? 'ready' : 'failed'))
      .catch(() => alive && setPhase('failed'));
    return () => {
      alive = false;
    };
  }, [hydrated, attempt]);
  return { phase, retry: () => setAttempt((n) => n + 1) };
}

function CodesRetry({ onRetry }: { onRetry: () => void }) {
  const styles = useStyles();
  return (
    <View style={styles.retry}>
      <Title style={styles.retryTitle}>옷 정보를 불러오지 못했어요</Title>
      <Text style={styles.retryText}>
        처음 실행할 때는 서버에서 옷 종류·색상 목록을 받아야 해요.{'\n'}
        서버({API_BASE_URL})가 켜져 있는지, 휴대폰과 Mac이 같은 Wi-Fi인지 확인한 뒤 다시 시도해 주세요.
      </Text>
      <Button title="다시 시도" onPress={onRetry} style={{ alignSelf: 'stretch', marginTop: 24 }} />
    </View>
  );
}

/** 저장소 복원이 끝나면 세션 복구(토큰 확인·/users/me)를 한 번 한다 */
function useSession(hydrated: boolean) {
  const [checked, setChecked] = useState(false);
  useEffect(() => {
    if (!hydrated || checked) return;
    bootstrapSession()
      .catch((e) => console.warn('[layout] 세션 확인 실패', e))
      .finally(() => setChecked(true));
  }, [hydrated, checked]);
  return checked;
}

/**
 * 서버 앱 설정(테마·계절 모드) 동기화 (doc/12 3장, 이슈 #6): 테마 저장값을 복원한 뒤 한 번, 앱이 다시 활성이 될 때마다 한 번.
 * 활성이 될 때 날짜로 계절도 다시 계산한다(AUTO에서 달이 바뀌었을 수 있다, 목업 실행 포함). 실패해도 화면을 막지 않는다.
 */
const useAppConfigSync = (themeHydrated: boolean) => {
  useEffect(() => {
    if (!themeHydrated) return;
    void syncAppConfig();
    const sub = AppState.addEventListener('change', (next) => {
      if (next !== 'active') return;
      useThemeStore.getState().refreshSeason();
      void syncAppConfig();
    });
    return () => sub.remove();
  }, [themeHydrated]);
};

/**
 * 옷 사진 관리 (계약 4장): 세션 확인이 끝나면 한 번(기기 사진을 앱 폴더로·정리·서버로 옮기기),
 * 앱이 다시 활성이 될 때마다 계정 모드면 사진 주소 갱신과 남은 기기 사진 옮기기. 화면을 막지 않는다.
 */
const usePhotoMaintenance = (sessionChecked: boolean) => {
  useEffect(() => {
    if (!sessionChecked) return;
    void startPhotoMaintenance();
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active') void onAppActive();
    });
    return () => sub.remove();
  }, [sessionChecked]);
};

/**
 * 서버 날씨 동기화 (doc/17 5장): 저장한 지역·날씨를 복원한 뒤 한 번, 앱이 다시 활성이 될 때마다 한 번.
 * 서버는 저장된 값만 주므로 자주 불러도 공급자 호출은 늘지 않는다. 실패하면 저장값·목업 날씨로 계속한다.
 */
const useWeatherSync = (weatherHydrated: boolean) => {
  useEffect(() => {
    if (!weatherHydrated) return;
    void syncWeather();
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active') void syncWeather();
    });
    return () => sub.remove();
  }, [weatherHydrated]);
};

/**
 * 예정일 날씨 다시 확인 (doc/19): 세션 확인(좌표 장소는 로그인 필요)과 장소·날씨 저장값 복원이 끝나면 한 번,
 * 앱이 다시 활성이 될 때마다 한 번. 같은 예정은 하루 한 번만 서버에 묻고, 서버에 API가 없으면 아무것도 하지 않는다.
 */
const usePlanWeatherRecheck = (ready: boolean) => {
  useEffect(() => {
    if (!ready) return;
    void recheckPlans();
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active') void recheckPlans();
    });
    return () => sub.remove();
  }, [ready]);
};

/**
 * 예정 코디 서버 동기화 (계정 모드): 세션 확인이 끝나면 한 번, 앱이 다시 활성이 될 때마다(30초 간격 이상) 한 번.
 * 게스트·목업 실행·서버에 API가 없으면 아무것도 하지 않는다. 화면을 막지 않는다.
 */
const usePlanSync = (sessionChecked: boolean) => {
  useEffect(() => {
    if (!sessionChecked) return;
    void syncPlans();
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active') void syncPlans();
    });
    return () => sub.remove();
  }, [sessionChecked]);
};

/**
 * 예정 D-1 알림 (lib/planNotify.ts): 화면이 준비되면(알림을 눌러 예정을 열 수 있게) 시작하고, 앱이 다시 활성이 될 때마다
 * 예약을 예정 목록과 맞춘다(날짜가 바뀌었으면 전날 날씨 다시 확인, 기기 설정에서 바꾼 권한 반영). 웹 미리보기는 아무것도 하지 않는다.
 */
const usePlanNotifications = (ready: boolean) => {
  useEffect(() => {
    if (!ready) return;
    const stop = startPlanNotifications();
    const sub = AppState.addEventListener('change', (next) => {
      if (next === 'active') onNotifyAppActive();
    });
    return () => {
      sub.remove();
      stop();
    };
  }, [ready]);
};

/** 계정 모드에서 로그인이 풀리면(Refresh 실패) 로그인 화면으로 보낸다 */
function AuthWatcher() {
  const lost = useStore((s) => s.authMode === 'account' && s.authLost);
  const reason = useStore((s) => s.authLostReason ?? 'expired');
  const pathname = usePathname();
  useEffect(() => {
    if (lost && pathname !== '/auth') router.replace({ pathname: '/auth', params: { reason } });
  }, [lost, pathname, reason]);
  return null;
}

export default function RootLayout() {
  const styles = useStyles();
  const colors = useColors();
  const hydrated = useHydrated(useStore.persist);
  const codesHydrated = useHydrated(useCodes.persist);
  const themeHydrated = useHydrated(useThemeStore.persist);
  const weatherHydrated = useHydrated(useWeatherStore.persist);
  const placeHydrated = useHydrated(usePlaceStore.persist);
  const notifyHydrated = useHydrated(useNotifyStore.persist);
  const sessionChecked = useSession(hydrated);
  const codes = useCodesReady(codesHydrated);
  useAppConfigSync(themeHydrated);
  usePhotoMaintenance(sessionChecked);
  usePlanSync(sessionChecked);
  useWeatherSync(weatherHydrated);
  usePlanWeatherRecheck(sessionChecked && weatherHydrated && placeHydrated && codes.phase === 'ready');
  // 저장된 테마로 첫 화면을 그리도록 테마 복원을 기다린다 (제목은 시스템 산세리프라 글꼴 로딩은 없다)
  const ready = themeHydrated && hydrated && sessionChecked && codes.phase === 'ready';
  const codesFailed = codes.phase === 'failed';
  usePlanNotifications(ready && notifyHydrated && weatherHydrated && placeHydrated);

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      {/* 웹 미리보기에서는 휴대폰 폭으로 가운데 정렬 */}
      <View style={styles.outer}>
        <View style={styles.app}>
          {ready ? (
            <>
              <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }}>
                <Stack.Screen name="(tabs)" />
                <Stack.Screen name="onboarding" options={{ gestureEnabled: false }} />
                <Stack.Screen name="auth" />
                <Stack.Screen name="item/new" options={{ presentation: 'modal' }} />
                {/* 쇼핑몰 캡처(doc/24): 옷 등록 모달 위에도 뜨고 웹 화면을 넓게 쓰도록 전체 화면 */}
                <Stack.Screen name="item/mall" options={{ presentation: 'fullScreenModal' }} />
              </Stack>
              <AuthWatcher />
            </>
          ) : codesFailed ? (
            <CodesRetry onRetry={codes.retry} />
          ) : (
            <View style={styles.loading}>
              <ActivityIndicator color={colors.primary} />
            </View>
          )}
        </View>
      </View>
    </SafeAreaProvider>
  );
}

const useStyles = makeStyles((t) => ({
  outer: { flex: 1, backgroundColor: Platform.OS === 'web' ? '#DADCE0' : t.colors.bg, alignItems: 'center' },
  app: { flex: 1, width: '100%', maxWidth: Platform.OS === 'web' ? 480 : undefined, backgroundColor: t.colors.bg },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  retry: { flex: 1, justifyContent: 'center', paddingHorizontal: 28 },
  retryTitle: { fontSize: 22 },
  retryText: { fontSize: 14, color: t.colors.textSub, lineHeight: 21, marginTop: 12 },
}));
