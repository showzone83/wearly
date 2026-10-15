import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { StylePrefs, WorkPattern } from '@/components/StyleForm';
import { Button, Kicker, Screen, Serif } from '@/components/ui';
import { DEFAULT_PROFILE } from '@/data/seed';
import type { Profile } from '@/data/types';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

// 목업 ① 첫 실행·온보딩 (p.39). 소셜 로그인은 API 키 발급 후 연동 예정이라 이 목업에서는 생략한다.
export default function Onboarding() {
  const completeOnboarding = useStore((s) => s.completeOnboarding);
  const [step, setStep] = useState(0);
  const [withSample, setWithSample] = useState(true);
  const [profile, setProfile] = useState<Profile>(DEFAULT_PROFILE);
  const insets = useSafeAreaInsets();

  const finish = () => {
    completeOnboarding(profile, withSample);
    router.replace('/home');
  };

  if (step === 0) {
    return (
      <View style={[s.splash, { paddingTop: insets.top + 60, paddingBottom: Math.max(insets.bottom, 24) }]}>
        <View style={{ flex: 1 }}>
          <Text style={s.issue}>THE DAILY STYLIST</Text>
          <Serif style={s.logo}>WEARLY</Serif>
          <Serif style={s.slogan}>
            오늘 뭐 입지?{'\n'}
            <Text style={{ color: colors.beige }}>이제 고민 끝.</Text>
          </Serif>
          <Text style={s.sub}>내 옷으로 완성하는 오늘의 코디.</Text>
        </View>
        <View style={{ gap: 10 }}>
          <Button
            title="샘플 옷장으로 둘러보기"
            variant="green"
            onPress={() => {
              setWithSample(true);
              setStep(1);
            }}
          />
          <Button
            title="내 옷으로 시작하기"
            variant="outline"
            onPress={() => {
              setWithSample(false);
              setStep(1);
            }}
          />
          <Text style={s.note}>목업 버전 · 소셜 로그인(카카오·구글·애플)은 연동 예정</Text>
        </View>
      </View>
    );
  }

  return (
    <Screen
      footer={
        <View style={{ flexDirection: 'row', gap: 10 }}>
          <Button title="건너뛰기" variant="outline" onPress={finish} style={{ flex: 1 }} />
          <Button title={step === 1 ? '다음' : '시작하기'} onPress={() => (step === 1 ? setStep(2) : finish())} style={{ flex: 2 }} />
        </View>
      }
    >
      <View style={{ paddingTop: 28 }}>
        <Kicker>STEP {step} / 2</Kicker>
        <Serif style={s.title}>{step === 1 ? '어떤 스타일을\n좋아하세요?' : '언제 출근하세요?'}</Serif>
        <Text style={s.desc}>
          {step === 1
            ? '취향은 나중에 마이 > 스타일 설정에서 바꿀 수 있어요. 건너뛰어도 괜찮아요.'
            : '근무 요일을 알려주시면 근무일엔 출근 코디, 쉬는 날엔 편한 코디를 추천해요.'}
        </Text>
        <View style={{ marginTop: 28 }}>
          {step === 1 ? <StylePrefs value={profile} onChange={setProfile} /> : <WorkPattern value={profile} onChange={setProfile} />}
        </View>
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  splash: { flex: 1, backgroundColor: colors.ink, paddingHorizontal: 28 },
  issue: { color: colors.beige, letterSpacing: 3, fontSize: 12, fontWeight: '700' },
  logo: { color: '#fff', fontSize: 44, letterSpacing: 6, marginTop: 14 },
  slogan: { color: '#fff', fontSize: 30, lineHeight: 44, marginTop: 56 },
  sub: { color: '#C9CCD1', fontSize: 15, marginTop: 14 },
  note: { color: '#8C9097', fontSize: 12, textAlign: 'center', marginTop: 6 },
  title: { fontSize: 28, lineHeight: 40, marginTop: 10 },
  desc: { color: colors.sub, fontSize: 14, lineHeight: 21, marginTop: 10 },
});
