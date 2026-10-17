import { NotoSerifKR_700Bold } from '@expo-google-fonts/noto-serif-kr/700Bold';
import { useFonts } from 'expo-font';
import { router, Stack, usePathname } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Platform, StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { bootstrapSession } from '@/lib/account';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

function useHydrated() {
  const [hydrated, setHydrated] = useState(useStore.persist.hasHydrated());
  useEffect(() => {
    const unsub = useStore.persist.onFinishHydration(() => setHydrated(true));
    setHydrated(useStore.persist.hasHydrated());
    return unsub;
  }, []);
  return hydrated;
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
  const hydrated = useHydrated();
  const sessionChecked = useSession(hydrated);
  const ready = (fontsLoaded || !!fontError) && hydrated && sessionChecked;

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
});
