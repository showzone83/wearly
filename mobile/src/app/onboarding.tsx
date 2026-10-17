import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/Icon';
import { StylePrefs, WorkPattern } from '@/components/StyleForm';
import { Button, Kicker, notify, Screen, Serif } from '@/components/ui';
import { DEFAULT_PROFILE } from '@/data/seed';
import type { Profile } from '@/data/types';
import { finishAccountOnboarding } from '@/lib/account';
import { errorMessage } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

/** 0 = 스플래시, 1 = 옷장 시작 방법, 2 = 취향, 3 = 근무 패턴 */
type Step = 0 | 1 | 2 | 3;

const STEP_TEXT: Record<1 | 2 | 3, { title: string; desc: string }> = {
  1: { title: '옷장을 어떻게\n시작할까요?', desc: '샘플 옷장으로 먼저 둘러보고, 내 옷은 나중에 하나씩 등록해도 괜찮아요.' },
  2: { title: '어떤 스타일을\n좋아하세요?', desc: '취향은 나중에 마이 > 스타일 설정에서 바꿀 수 있어요. 건너뛰어도 괜찮아요.' },
  3: { title: '언제 출근하세요?', desc: '근무 요일을 알려주시면 근무일엔 출근 코디, 쉬는 날엔 편한 코디를 추천해요.' },
};

// 목업 ① 첫 실행·온보딩 (p.39). 이메일 계정(임시) 또는 로그인 없이 체험. 소셜 로그인은 API 키 발급 후 연동 예정.
export default function Onboarding() {
  const completeOnboarding = useStore((s) => s.completeOnboarding);
  const isAccount = useStore((s) => s.authMode === 'account');
  const initialProfile = useStore((s) => s.profile);
  // 로그인 후 이어서 하는 온보딩은 스플래시를 건너뛴다
  const [step, setStep] = useState<Step>(isAccount ? 1 : 0);
  const [withSample, setWithSample] = useState(true);
  const [profile, setProfile] = useState<Profile>(isAccount ? initialProfile : DEFAULT_PROFILE);
  const [busy, setBusy] = useState(false);
  const insets = useSafeAreaInsets();

  // 스플래시에서 로그인하고 돌아온 경우: 서버 프로필(가입 이름 포함)로 이어서 진행한다
  useEffect(() => {
    if (!isAccount) return;
    setProfile(useStore.getState().profile);
    setStep((s) => (s === 0 ? 1 : s));
  }, [isAccount]);

  const finish = async () => {
    if (!isAccount) {
      completeOnboarding(profile, withSample);
      router.replace('/home');
      return;
    }
    setBusy(true);
    try {
      await finishAccountOnboarding(profile, withSample);
      router.replace('/home');
    } catch (e) {
      notify('설정을 저장하지 못했어요', errorMessage(e));
    } finally {
      setBusy(false);
    }
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
          <Button title="이메일로 시작하기" variant="green" onPress={() => router.push('/auth')} />
          {/* 로그인 없이 쓰는 게스트 모드는 목업 실행(npm run start:mock)에서만 연다 */}
          {MOCK_MODE && <Button title="로그인 없이 둘러보기 (목업)" variant="outline" onPress={() => setStep(1)} />}
          <Text style={s.note}>
            {MOCK_MODE ? '목업 실행 중 · 로그인 없이 쓰면 데이터는 이 기기에만 저장돼요' : '소셜 로그인(카카오·애플·구글)은 연동 예정'}
          </Text>
        </View>
      </View>
    );
  }

  const text = STEP_TEXT[step];
  const next = () => (step === 3 ? finish() : setStep((step + 1) as Step));

  return (
    <Screen
      footer={
        <View style={{ flexDirection: 'row', gap: 10 }}>
          {step === 1 && !isAccount ? (
            <Button title="처음으로" variant="outline" onPress={() => setStep(0)} style={{ flex: 1 }} />
          ) : (
            <Button title="건너뛰기" variant="outline" onPress={finish} disabled={busy} style={{ flex: 1 }} />
          )}
          <Button
            title={busy ? '' : step === 3 ? '시작하기' : '다음'}
            icon={busy ? <ActivityIndicator color="#fff" /> : undefined}
            onPress={next}
            disabled={busy}
            style={{ flex: 2 }}
          />
        </View>
      }
    >
      <View style={{ paddingTop: 28 }}>
        <Kicker>STEP {step} / 3</Kicker>
        <Serif style={s.title}>{text.title}</Serif>
        <Text style={s.desc}>{text.desc}</Text>
        <View style={{ marginTop: 28 }}>
          {step === 1 && (
            <View style={{ gap: 12 }}>
              <StartOption
                icon="sparkle"
                title="샘플 옷장으로 시작"
                sub="옷 19벌과 지난 기록 2건, 다가오는 일정 2건이 들어 있어요"
                selected={withSample}
                onPress={() => setWithSample(true)}
              />
              <StartOption
                icon="hanger"
                title="빈 옷장으로 시작"
                sub="상의·하의를 1벌씩 등록하면 바로 추천을 받아요"
                selected={!withSample}
                onPress={() => setWithSample(false)}
              />
              {isAccount && <Text style={s.hint}>샘플 옷도 계정 옷장에 저장돼요. 나중에 하나씩 지울 수 있어요.</Text>}
            </View>
          )}
          {step === 2 && <StylePrefs value={profile} onChange={setProfile} />}
          {step === 3 && <WorkPattern value={profile} onChange={setProfile} />}
        </View>
      </View>
    </Screen>
  );
}

function StartOption({ icon, title, sub, selected, onPress }: { icon: IconName; title: string; sub: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      style={[s.option, selected && { borderColor: colors.green, backgroundColor: colors.greenSoft }]}
    >
      <Icon name={icon} size={28} color={selected ? colors.green : colors.ink} />
      <View style={{ flex: 1 }}>
        <Text style={s.optTitle}>{title}</Text>
        <Text style={s.optSub}>{sub}</Text>
      </View>
      <Icon name="check" size={18} color={selected ? colors.green : colors.line} />
    </Pressable>
  );
}

const s = StyleSheet.create({
  splash: { flex: 1, backgroundColor: colors.ink, paddingHorizontal: 28 },
  issue: { color: colors.beige, letterSpacing: 3, fontSize: 12, fontWeight: '700' },
  logo: { color: '#fff', fontSize: 44, letterSpacing: 6, marginTop: 14 },
  slogan: { color: '#fff', fontSize: 30, lineHeight: 44, marginTop: 56 },
  sub: { color: '#C9CCD1', fontSize: 15, marginTop: 14 },
  note: { color: '#8C9097', fontSize: 12, textAlign: 'center', marginTop: 6, lineHeight: 18 },
  title: { fontSize: 28, lineHeight: 40, marginTop: 10 },
  desc: { color: colors.sub, fontSize: 14, lineHeight: 21, marginTop: 10 },
  option: { flexDirection: 'row', alignItems: 'center', gap: 16, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 18 },
  optTitle: { fontSize: 16, fontWeight: '800', color: colors.ink },
  optSub: { fontSize: 12, color: colors.sub, marginTop: 3, lineHeight: 17 },
  hint: { fontSize: 12, color: colors.sub, lineHeight: 18 },
});
