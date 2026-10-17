import { NotoSerifKR_700Bold } from '@expo-google-fonts/noto-serif-kr/700Bold';
import { useFonts } from 'expo-font';
import { router, Stack, usePathname } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Platform, StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { Button, Serif } from '@/components/ui';
import { bootstrapSession } from '@/lib/account';
import { API_BASE_URL } from '@/lib/api';
import { bootstrapCodes, useCodes } from '@/store/useCodes';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

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
  return (
    <View style={styles.retry}>
      <Serif style={styles.retryTitle}>옷 정보를 불러오지 못했어요</Serif>
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

/** 계정 모드에서 로그인이 풀리면(Refresh 실패) 로그인 화면으로 보낸다 */
function AuthWatcher() {
  const lost = useStore((s) => s.authMode === 'account' && s.authLost);
  const pathname = usePathname();
  useEffect(() => {
    if (lost && pathname !== '/auth') router.replace({ pathname: '/auth', params: { reason: 'expired' } });
  }, [lost, pathname]);
  return null;
}

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({ NotoSerifKR_700Bold });
  const hydrated = useHydrated(useStore.persist);
  const codesHydrated = useHydrated(useCodes.persist);
  const sessionChecked = useSession(hydrated);
  const codes = useCodesReady(codesHydrated);
  const fontsReady = fontsLoaded || !!fontError;
  const ready = fontsReady && hydrated && sessionChecked && codes.phase === 'ready';
  const codesFailed = fontsReady && codes.phase === 'failed';

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
              </Stack>
              <AuthWatcher />
            </>
          ) : codesFailed ? (
            <CodesRetry onRetry={codes.retry} />
          ) : (
            <View style={styles.loading}>
              <ActivityIndicator color={colors.green} />
            </View>
          )}
        </View>
      </View>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  outer: { flex: 1, backgroundColor: Platform.OS === 'web' ? '#DADCE0' : colors.bg, alignItems: 'center' },
  app: { flex: 1, width: '100%', maxWidth: Platform.OS === 'web' ? 480 : undefined, backgroundColor: colors.bg },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  retry: { flex: 1, justifyContent: 'center', paddingHorizontal: 28 },
  retryTitle: { fontSize: 22, color: colors.ink },
  retryText: { fontSize: 14, color: colors.sub, lineHeight: 21, marginTop: 12 },
});
