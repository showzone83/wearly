import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View, type TextInputProps } from 'react-native';

import { isApiError } from '@/api/errors';
import { Mascot } from '@/components/Mascot';
import { Button, Header, Kicker, Screen, Title, TraceLabel } from '@/components/ui';
import { continueAsGuest, enterAccount } from '@/lib/account';
import { MOCK_MODE } from '@/lib/mode';
import { api, errorMessage } from '@/lib/api';
import { fieldErrorText, systemTraceId } from '@/lib/errorText';
import { useStore } from '@/store/useStore';
import { inputBase, makeStyles, useColors } from '@/theme';

type Mode = 'login' | 'signup';
type Field = 'email' | 'password' | 'name';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** 계약 5장: 비밀번호 8~64자, 영문과 숫자를 모두 포함 */
function passwordProblem(pw: string): string | null {
  if (pw.length < 8 || pw.length > 64) return '비밀번호는 8~64자로 입력해 주세요.';
  if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return '영문과 숫자를 모두 넣어 주세요.';
  return null;
}

function validate(mode: Mode, email: string, password: string, name: string): Partial<Record<Field, string>> {
  const errs: Partial<Record<Field, string>> = {};
  if (!EMAIL_RE.test(email)) errs.email = '이메일 형식을 확인해 주세요.';
  if (mode === 'signup') {
    const pw = passwordProblem(password);
    if (pw) errs.password = pw;
    const n = name.trim();
    if (n.length < 1 || n.length > 20) errs.name = '이름은 1~20자로 입력해 주세요.';
  } else if (!password) {
    errs.password = '비밀번호를 입력해 주세요.';
  }
  return errs;
}

// 이메일 로그인·가입 (임시 수단: 소셜 로그인 키를 받기 전까지 토큰 흐름을 먼저 쓴다. 계약 1장)
export default function Auth() {
  const params = useLocalSearchParams<{ reason?: string; mode?: string }>();
  // expired = 로그인이 풀려 들어온 경우. elsewhere는 다른 곳에서 로그아웃됨(강제 로그아웃 등, doc/16 6장)
  const elsewhere = params.reason === 'elsewhere';
  const expired = params.reason === 'expired' || elsewhere;
  const lastEmail = useStore((s) => (s.authMode === 'account' ? s.user?.email : null));
  const guestHasData = useStore((s) => s.authMode === 'guest' && s.onboarded);

  const [mode, setMode] = useState<Mode>(params.mode === 'signup' ? 'signup' : 'login');
  const [email, setEmail] = useState(lastEmail ?? '');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [banner, setBanner] = useState<string | null>(null);
  /** 배너 오류가 시스템 오류면 그 traceId (오류 번호) */
  const [bannerTrace, setBannerTrace] = useState<string | undefined>();
  const [busy, setBusy] = useState(false);
  const s = useStyles();
  const colors = useColors();

  const switchMode = (m: Mode) => {
    setMode(m);
    setErrors({});
    setBanner(null);
    setBannerTrace(undefined);
  };

  const submit = async () => {
    const mail = email.trim().toLowerCase();
    const errs = validate(mode, mail, password, name);
    setErrors(errs);
    setBanner(null);
    setBannerTrace(undefined);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      const res =
        mode === 'signup'
          ? await api.auth.signup({ email: mail, password, name: name.trim() })
          : await api.auth.login({ email: mail, password });
      const dest = await enterAccount(res.user);
      // 스플래시·마이에서 들어온 경우 쌓인 화면을 정리하고 이동한다
      if (router.canDismiss()) router.dismissAll();
      router.replace(dest === 'home' ? '/home' : '/onboarding');
    } catch (e) {
      if (isApiError(e) && e.code === 'EMAIL_TAKEN') {
        setErrors({ email: '이미 가입된 이메일이에요. 로그인해 주세요.' });
      } else if (isApiError(e) && e.code === 'VALIDATION_FAILED' && e.fieldErrors.length) {
        const next: Partial<Record<Field, string>> = {};
        for (const f of e.fieldErrors) if (f.field === 'email' || f.field === 'password' || f.field === 'name') next[f.field] ??= fieldErrorText(f);
        setErrors(next);
        if (!Object.keys(next).length) setBanner(errorMessage(e));
      } else if (isApiError(e) && e.code === 'INVALID_CREDENTIALS') {
        setBanner('이메일 또는 비밀번호가 맞지 않아요.');
      } else {
        setBanner(errorMessage(e, '로그인하지 못했어요. 잠시 후 다시 시도해 주세요.'));
        setBannerTrace(systemTraceId(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen header={expired ? <View style={{ height: 24 }} /> : <Header title={mode === 'login' ? '로그인' : '회원가입'} />}>
      <View style={[s.head, { paddingTop: expired ? 20 : 4 }]}>
        <View style={{ flex: 1 }}>
          <Kicker color={colors.primary}>WEARLY ACCOUNT</Kicker>
          <Title style={s.title}>{mode === 'login' ? '다시 만나서\n반가워요' : '내 옷장을\n계정에 담아요'}</Title>
        </View>
        <Mascot pose="hello" size={104} />
      </View>
      <View>
        <Text style={s.desc}>
          {mode === 'login'
            ? '로그인하면 옷장과 취향 설정이 서버에 저장돼요.'
            : '옷장·취향 설정이 계정에 저장돼요. 옷 사진은 이 기기에만 보관해요.'}
        </Text>
      </View>

      {expired && (
        <View style={[s.notice, s.noticeWarn]}>
          <Text style={s.noticeTitle}>
            {elsewhere ? '다른 곳에서 로그아웃되었어요. 다시 로그인해 주세요.' : '로그인이 만료됐어요'}
          </Text>
          <Text style={s.noticeText}>다시 로그인하면 이 기기의 코디·기록을 이어서 쓸 수 있어요.</Text>
        </View>
      )}
      {!expired && MOCK_MODE && guestHasData && (
        <View style={s.notice}>
          <Text style={s.noticeText}>로그인하면 지금 기기에 있는 체험용 옷장·기록 대신 계정 데이터를 써요.</Text>
        </View>
      )}

      <View style={s.tabs}>
        {(['login', 'signup'] as Mode[]).map((m) => (
          <Pressable
            key={m}
            onPress={() => switchMode(m)}
            style={[s.tab, mode === m && s.tabOn]}
            accessibilityRole="button"
            accessibilityState={{ selected: mode === m }}
          >
            <Text style={[s.tabText, mode === m && { color: colors.onPrimary, fontWeight: '800' }]}>
              {m === 'login' ? '로그인' : '회원가입'}
            </Text>
          </Pressable>
        ))}
      </View>

      <View style={{ gap: 18 }}>
        {mode === 'signup' && (
          <Input label="이름" value={name} onChangeText={setName} error={errors.name} placeholder="1~20자" maxLength={20} autoComplete="name" textContentType="name" />
        )}
        <Input
          label="이메일"
          value={email}
          onChangeText={setEmail}
          error={errors.email}
          placeholder="you@example.com"
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="email"
          textContentType="emailAddress"
        />
        <Input
          label="비밀번호"
          value={password}
          onChangeText={setPassword}
          error={errors.password}
          hint={mode === 'signup' ? '8~64자, 영문과 숫자를 모두 넣어 주세요.' : undefined}
          placeholder={mode === 'signup' ? '영문 + 숫자 8자 이상' : '비밀번호'}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
          textContentType={mode === 'signup' ? 'newPassword' : 'password'}
          onSubmitEditing={submit}
          returnKeyType="go"
        />
      </View>

      {banner && (
        <View style={[s.notice, s.noticeWarn, { marginTop: 18 }]}>
          <Text style={s.noticeText}>{banner}</Text>
          <TraceLabel traceId={bannerTrace} />
        </View>
      )}

      <Button
        title={busy ? '' : mode === 'login' ? '로그인' : '가입하고 시작하기'}
        icon={busy ? <ActivityIndicator color="#fff" /> : undefined}
        onPress={submit}
        disabled={busy}
        style={{ marginTop: 24 }}
      />

      {/* 만료 후 게스트로 계속 쓰기는 목업 실행에서만 */}
      {expired && MOCK_MODE && (
        <Pressable
          style={{ marginTop: 20, alignItems: 'center' }}
          onPress={() => {
            continueAsGuest();
            router.replace('/');
          }}
        >
          <Text style={s.link}>로그인 없이 이 기기 데이터로 계속하기</Text>
        </Pressable>
      )}
      <Text style={s.foot}>카카오·애플·구글 로그인은 연동 예정이에요.</Text>
    </Screen>
  );
}

function Input({ label, error, hint, ...props }: TextInputProps & { label: string; error?: string; hint?: string }) {
  const s = useStyles();
  const colors = useColors();
  return (
    <View>
      <Text style={s.label}>{label}</Text>
      <TextInput {...props} style={[s.input, !!error && { borderColor: colors.danger }]} placeholderTextColor={colors.textMuted} />
      {error ? <Text style={s.error}>{error}</Text> : hint ? <Text style={s.hint}>{hint}</Text> : null}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  head: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  title: { fontSize: 27, lineHeight: 37, marginTop: 8 },
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21, marginTop: 10 },
  tabs: {
    flexDirection: 'row',
    backgroundColor: t.colors.surfaceAlt,
    borderRadius: t.radius.pill,
    padding: 4,
    marginTop: 24,
    marginBottom: 22,
  },
  tab: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: t.radius.pill },
  tabOn: { backgroundColor: t.colors.primary },
  tabText: { fontSize: 15, color: t.colors.textSub, fontWeight: '600' },
  label: { fontSize: 14, color: t.colors.text, fontWeight: '700', marginBottom: 8 },
  input: inputBase(t),
  error: { color: t.colors.danger, fontSize: 12, marginTop: 6 },
  hint: { color: t.colors.textMuted, fontSize: 12, marginTop: 6 },
  notice: { backgroundColor: t.colors.surfaceAlt, borderRadius: t.radius.md, padding: 14, marginTop: 20, gap: 4 },
  noticeWarn: { backgroundColor: t.colors.accentSoft },
  noticeTitle: { fontSize: 14, fontWeight: '800', color: t.colors.text },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
  link: { color: t.colors.primary, fontWeight: '700', textDecorationLine: 'underline' },
  foot: { color: t.colors.textMuted, fontSize: 12, textAlign: 'center', marginTop: 26 },
}));
