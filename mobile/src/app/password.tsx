import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, TextInput, View, type TextInputProps } from 'react-native';

import type { PasswordStatusDto } from '@/api/types';
import { isApiError } from '@/api/errors';
import { Button, Header, Notice, notify, Screen } from '@/components/ui';
import { api, errorAlertMessage } from '@/lib/api';
import { fieldErrorText } from '@/lib/errorText';
import { postponePassword } from '@/lib/passwordReminder';
import { changedText, changePasswordProblems, dueDateText, dueNoticeText, postponeLabel, type TChangeField } from '@/lib/passwordRules';
import { inputBase, makeStyles, useColors } from '@/theme';

// 비밀번호 변경 (doc/28 3단계): 지금 비밀번호 · 새 비밀번호 · 확인. 바꾸면 다른 기기는 로그아웃되고 주기를 다시 센다.
// 기한이 지났으면(알림·창에서 들어오면 due=1) 위에 안내와 "N일 연장"을 둔다.
export default function Password() {
  const params = useLocalSearchParams<{ due?: string }>();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [again, setAgain] = useState('');
  const [errors, setErrors] = useState<Partial<Record<TChangeField, string>>>({});
  const [status, setStatus] = useState<PasswordStatusDto | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const s = useStyles();

  useEffect(() => {
    let alive = true;
    api.password
      .status()
      .then((st) => alive && setStatus(st))
      .catch(() => undefined); // 예전 서버·오류: 안내 없이 변경만
    return () => {
      alive = false;
    };
  }, []);

  const due = status ? status.due : params.due === '1';

  const lock = (on: boolean) => {
    busyRef.current = on;
    setBusy(on);
  };

  const submit = async () => {
    if (busyRef.current) return;
    const errs = changePasswordProblems(current, next, again);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    lock(true);
    try {
      await api.password.change({ currentPassword: current, newPassword: next });
      const t = changedText();
      if (router.canGoBack()) router.back();
      else router.replace('/my');
      notify(t.title, t.message);
    } catch (e) {
      lock(false);
      if (isApiError(e) && e.code === 'REAUTH_FAILED') {
        setErrors({ currentPassword: '지금 비밀번호가 맞지 않아요.' });
      } else if (isApiError(e) && e.code === 'VALIDATION_FAILED' && e.fieldErrors.length) {
        const out: Partial<Record<TChangeField, string>> = {};
        for (const f of e.fieldErrors) {
          if (f.field === 'currentPassword' || f.field === 'newPassword') out[f.field] ??= fieldErrorText(f);
        }
        setErrors(Object.keys(out).length ? out : { newPassword: fieldErrorText(e.fieldErrors[0]) });
      } else {
        notify('비밀번호를 바꾸지 못했어요', errorAlertMessage(e));
      }
    }
  };

  const postpone = async () => {
    if (busyRef.current) return;
    lock(true);
    const ok = await postponePassword();
    lock(false);
    if (ok) {
      if (router.canGoBack()) router.back();
      else router.replace('/my');
    }
  };

  return (
    <Screen
      header={<Header title="비밀번호 변경" />}
      footer={
        <Button
          title={busy ? '' : '비밀번호 바꾸기'}
          icon={busy ? <ActivityIndicator color="#fff" /> : undefined}
          disabled={busy}
          onPress={() => void submit()}
        />
      }
    >
      {due ? (
        <Notice tone="beige" icon="help" style={{ marginTop: 8 }}>
          <Text style={s.noticeTitle}>비밀번호를 바꿀 때가 됐어요</Text>
          <Text style={s.noticeText}>{dueNoticeText(status?.intervalDays)}</Text>
          <Button
            title={postponeLabel(status?.intervalDays)}
            variant="outline"
            small
            disabled={busy}
            onPress={() => void postpone()}
            style={{ alignSelf: 'flex-start', marginTop: 10 }}
          />
        </Notice>
      ) : (
        <Notice tone="plain" icon="help" style={{ marginTop: 8 }}>
          <Text style={s.noticeText}>
            {`바꾸면 다른 기기에서는 로그아웃돼요.${status?.dueAt ? `\n다음에 바꿀 날: ${dueDateText(status.dueAt)}` : ''}`}
          </Text>
        </Notice>
      )}

      <View style={{ gap: 18, marginTop: 20 }}>
        <Field
          label="지금 비밀번호"
          value={current}
          onChangeText={setCurrent}
          error={errors.currentPassword}
          autoComplete="current-password"
          textContentType="password"
          editable={!busy}
        />
        <Field
          label="새 비밀번호"
          value={next}
          onChangeText={setNext}
          error={errors.newPassword}
          hint="8~64자, 영문과 숫자를 모두 넣어 주세요."
          autoComplete="new-password"
          textContentType="newPassword"
          editable={!busy}
        />
        <Field
          label="새 비밀번호 확인"
          value={again}
          onChangeText={setAgain}
          error={errors.confirm}
          autoComplete="new-password"
          textContentType="newPassword"
          editable={!busy}
          onSubmitEditing={() => void submit()}
          returnKeyType="go"
        />
      </View>
    </Screen>
  );
}

const Field = ({ label, error, hint, ...props }: TextInputProps & { label: string; error?: string; hint?: string }) => {
  const s = useStyles();
  const c = useColors();

  return (
    <View>
      <Text style={s.label}>{label}</Text>
      <TextInput
        {...props}
        secureTextEntry
        autoCapitalize="none"
        autoCorrect={false}
        style={[s.input, !!error && { borderColor: c.danger }]}
        placeholderTextColor={c.textMuted}
        accessibilityLabel={label}
      />
      {error ? <Text style={s.error}>{error}</Text> : hint ? <Text style={s.hint}>{hint}</Text> : null}
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  noticeTitle: { fontSize: 14, fontWeight: '800', color: t.colors.text, marginBottom: 6 },
  noticeText: { fontSize: 13, lineHeight: 21, color: t.colors.textSub },
  label: { fontSize: 14, fontWeight: '700', color: t.colors.text, marginBottom: 8 },
  input: { ...inputBase(t) },
  error: { fontSize: 12, color: t.colors.danger, marginTop: 6 },
  hint: { fontSize: 12, color: t.colors.textMuted, marginTop: 6 },
}));
