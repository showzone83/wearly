import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, useWindowDimensions, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { Mascot, useMascotIsScene } from '@/components/Mascot';
import { StylePrefs, WorkPattern } from '@/components/StyleForm';
import { Button, Header, Kicker, notify, Screen, StepDots, Title } from '@/components/ui';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { DEFAULT_PROFILE, SAMPLE_ITEM_COUNT } from '@/data/seed';
import type { Profile } from '@/data/types';
import { finishAccountOnboarding } from '@/lib/account';
import { errorMessage } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { useStore } from '@/store/useStore';
import { makeStyles, useColors } from '@/theme';

/** 0 = 스플래시, 1 = 옷장 시작 방법, 2 = 취향, 3 = 근무 패턴 */
type Step = 0 | 1 | 2 | 3;

const STEP_TEXT: Record<1 | 2 | 3, { title: string; desc: string }> = {
  1: {
    title: '잊고 있던 옷이\n새로운 스타일로!',
    desc: `지금, 장롱 속 옷을 꺼내 AI가 추천하는 코디를 만나보세요. 샘플 옷장(옷 ${SAMPLE_ITEM_COUNT}벌 · 지난 기록 2건 · 예정 2건)으로 먼저 둘러봐도 괜찮아요.`,
  },
  2: { title: '어떤 스타일을\n좋아하세요?', desc: '취향을 반영할수록 더 나에게 딱 맞는 코디를 추천해드려요. 나중에 더보기 > 스타일 설정에서 바꿀 수 있어요.' },
  3: { title: '언제\n출근하세요?', desc: '근무 요일을 알려주시면 근무일엔 출근 코디, 쉬는 날엔 편한 코디를 추천해요.' },
};

// 목업 ① 첫 실행·온보딩 (p.39). 이메일 계정(임시) 또는 로그인 없이 체험. 소셜 로그인은 API 키 발급 후 연동 예정.
// 모양은 디자인 보드(doc/12 5장): 스플래시 → 옷장 시작(STEP 1) → 취향(STEP 2) → 근무 패턴(STEP 3)
export default function Onboarding() {
  const completeOnboarding = useStore((s) => s.completeOnboarding);
  const isAccount = useStore((s) => s.authMode === 'account');
  const initialProfile = useStore((s) => s.profile);
  // 로그인 후 이어서 하는 온보딩은 스플래시를 건너뛴다
  const [step, setStep] = useState<Step>(isAccount ? 1 : 0);
  const [withSample, setWithSample] = useState(true);
  const [profile, setProfile] = useState<Profile>(isAccount ? initialProfile : DEFAULT_PROFILE);
  const [busy, setBusy] = useState(false);
  const s = useStyles();
  const c = useColors();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  // 냥깨비의 옷장 그림은 배경 있는 장면이라 둘레 상자 없이 화면 폭 카드로 보인다
  const closetIsScene = useMascotIsScene('closet');

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
    const art = Math.min(width, 480) * 0.74;
    return (
      <View style={[s.splash, { paddingTop: insets.top + 48, paddingBottom: Math.max(insets.bottom, 20) }]}>
        <View style={{ alignItems: 'center' }}>
          <View style={s.logoRow}>
            <Text style={s.logo}>Wearly</Text>
            <Icon name="sparkle" size={22} color={c.accent} filled />
          </View>
          <Text style={s.slogan}>
            오늘 뭐 입지?{'\n'}
            <Text style={{ color: c.primary }}>이제 고민 끝.</Text>
          </Text>
          <Text style={s.sub}>내 옷장 속 옷으로 AI가 추천하는 오늘의 코디</Text>
        </View>
        <View style={s.splashArt}>
          <Mascot pose="splash" size={art} />
        </View>
        <View style={{ gap: 10 }}>
          <Button
            title="시작하기"
            trailing={<Icon name="arrowRight" size={18} color={c.onPrimary} strokeWidth={2} />}
            onPress={() => router.push({ pathname: '/auth', params: { mode: 'signup' } })}
          />
          {/* 로그인 없이 쓰는 게스트 모드는 목업 실행(npm run start:mock)에서만 연다 */}
          {MOCK_MODE && <Button title="로그인 없이 둘러보기 (목업)" variant="outline" onPress={() => setStep(1)} />}
          <Pressable onPress={() => router.push('/auth')} hitSlop={8} style={s.loginLink} accessibilityRole="button">
            <Text style={s.loginText}>로그인</Text>
          </Pressable>
          <Text style={s.note}>
            {MOCK_MODE
              ? '목업 실행 중 · 로그인 없이 쓰면 데이터는 이 기기에만 저장돼요'
              : '소셜 로그인(카카오·애플·구글)은 연동 예정'}
          </Text>
        </View>
      </View>
    );
  }

  const text = STEP_TEXT[step];
  const next = () => (step === 3 ? finish() : setStep((step + 1) as Step));
  const canBack = step > 1 || !isAccount;
  const back = () => setStep((step - 1) as Step);
  const header = (
    <Header
      hideBack={!canBack}
      onBack={back}
      center={<StepDots total={3} current={step} />}
      right={
        step === 1 && !isAccount ? null : (
          <Pressable onPress={() => void finish()} disabled={busy} hitSlop={8} accessibilityRole="button">
            <Text style={s.skip}>건너뛰기</Text>
          </Pressable>
        )
      }
    />
  );

  // STEP 1: 보드의 "잊고 있던 옷이 새로운 스타일로!" 화면. 버튼으로 옷장 시작 방법을 고르고 바로 다음 단계로
  if (step === 1) {
    const choose = (sample: boolean) => {
      setWithSample(sample);
      setStep(2);
    };
    return (
      <Screen
        header={header}
        footer={
          <View style={{ gap: 10 }}>
            <Button title="내 옷장 등록하기" onPress={() => choose(false)} />
            <Button title="샘플 옷장으로 둘러보기" variant="outline" onPress={() => choose(true)} />
          </View>
        }
      >
        <View style={closetIsScene ? s.heroScene : s.hero}>
          <Mascot pose="closet" size={Math.min(width, 480) * 0.62} sceneWidth={Math.min(width, 480) - 40} />
        </View>
        <Kicker>STEP 1 / 3</Kicker>
        <Title style={s.title}>{text.title}</Title>
        <Text style={s.desc}>{text.desc}</Text>
        {isAccount && <Text style={s.hint}>샘플 옷도 계정 옷장에 저장돼요. 나중에 하나씩 지울 수 있어요.</Text>}
      </Screen>
    );
  }

  return (
    <Screen
      header={header}
      footer={
        <Button
          title={busy ? '' : step === 3 ? '완료하기' : '다음'}
          icon={busy ? <ActivityIndicator color={c.onPrimary} /> : undefined}
          onPress={next}
          disabled={busy}
        />
      }
    >
      <View style={s.stepHead}>
        <View style={{ flex: 1 }}>
          <Kicker>STEP {step} / 3</Kicker>
          <Title style={s.title}>{text.title}</Title>
        </View>
        <Mascot pose={step === 2 ? 'style' : 'cheer'} size={112} />
      </View>
      <Text style={s.desc}>{text.desc}</Text>
      <View style={{ marginTop: 28 }}>
        {step === 2 && <StylePrefs value={profile} onChange={setProfile} />}
        {step === 3 && <WorkPattern value={profile} onChange={setProfile} />}
      </View>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  splash: { flex: 1, backgroundColor: t.colors.bg, paddingHorizontal: 28 },
  logoRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 2 },
  logo: { color: t.colors.primary, fontSize: 54, fontWeight: '900', letterSpacing: -1.5 },
  slogan: { color: t.colors.text, fontSize: 20, lineHeight: 30, fontWeight: '800', textAlign: 'center', marginTop: 14 },
  sub: { color: t.colors.textSub, fontSize: 14, marginTop: 10, textAlign: 'center' },
  splashArt: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  loginLink: { alignSelf: 'center', paddingVertical: 8 },
  loginText: { color: t.colors.text, fontSize: 15, fontWeight: '700' },
  note: { color: t.colors.textMuted, fontSize: 12, textAlign: 'center', lineHeight: 18 },
  skip: { color: t.colors.textSub, fontSize: 14, fontWeight: '600' },
  heroScene: { alignItems: 'center', marginTop: 6, marginBottom: 24 },
  hero: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.colors.surfaceAlt,
    borderRadius: t.radius.xl,
    paddingVertical: 18,
    marginTop: 6,
    marginBottom: 24,
  },
  stepHead: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingTop: 12 },
  title: { fontSize: 28, lineHeight: 38, marginTop: 8 },
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21, marginTop: 10 },
  hint: { fontSize: 12, color: t.colors.textSub, lineHeight: 18, marginTop: 12 },
}));
