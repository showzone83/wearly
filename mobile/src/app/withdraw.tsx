import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';

import type { WithdrawalReason } from '@/api/types';
import { Icon } from '@/components/Icon';
import { Button, confirm, Header, Notice, notify, Screen } from '@/components/ui';
import { deleteAccount } from '@/lib/account';
import { errorAlertMessage } from '@/lib/api';
import {
  GRACE_DAYS,
  REASON_TEXT_MAX,
  WITHDRAW_REASONS,
  withdrawConfirmText,
  withdrawDoneText,
  withdrawRequest,
} from '@/lib/withdrawRules';
import { inputBase, makeStyles, useColors } from '@/theme';

// 회원 탈퇴 (doc/27 3단계): 안내 → 사유(꼭 하나) · 하고 싶은 말(선택) → 확인 창 → 탈퇴 신청.
// 바로 지우지 않고 30일 동안 탈퇴 예정으로 두며, 그 전에 같은 계정으로 로그인하면 취소할 수 있다. 기기 데이터는 지금 지운다.
export default function Withdraw() {
  const [reason, setReason] = useState<WithdrawalReason | null>(null);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const s = useStyles();
  const c = useColors();

  const req = withdrawRequest(reason, text);

  const send = async () => {
    if (!req || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      const out = await deleteAccount(req);
      const done = withdrawDoneText(out.purgeAt);
      if (router.canDismiss()) router.dismissAll();
      router.replace('/onboarding');
      notify(done.title, done.message);
    } catch (e) {
      notify('탈퇴하지 못했어요', errorAlertMessage(e));
      busyRef.current = false;
      setBusy(false);
    }
  };

  const ask = () => {
    if (!req) return;
    const t = withdrawConfirmText();
    confirm(t.title, t.message, () => void send(), t.ok);
  };

  return (
    <Screen
      header={<Header title="회원 탈퇴" />}
      footer={
        <Button
          title={busy ? '' : '탈퇴하기'}
          icon={busy ? <ActivityIndicator color={c.danger} /> : undefined}
          variant="danger"
          disabled={busy || !req}
          onPress={ask}
        />
      }
    >
      <Notice tone="beige" icon="help" style={{ marginTop: 8 }}>
        <Text style={s.noticeTitle}>탈퇴하면 이렇게 돼요</Text>
        <Text style={s.noticeText}>
          {`· 지금 모든 기기에서 로그아웃되고 이 기기의 데이터가 지워져요.\n· ${GRACE_DAYS}일 뒤 계정과 옷장·사진·코디·설정이 모두 삭제돼요.\n· 그 전에 같은 계정으로 로그인하면 탈퇴를 취소할 수 있어요.\n· 옷장 도우미 연결은 바로 끊기고, 취소해도 다시 연결해야 해요.`}
        </Text>
      </Notice>

      <Text style={s.label}>탈퇴하는 이유를 알려 주세요</Text>
      <View accessibilityRole="radiogroup" style={{ gap: 8 }}>
        {WITHDRAW_REASONS.map((r) => {
          const on = reason === r.value;
          return (
            <Pressable
              key={r.value}
              onPress={() => setReason(r.value)}
              disabled={busy}
              accessibilityRole="radio"
              accessibilityState={{ selected: on, disabled: busy }}
              style={[s.option, on && s.optionOn]}
            >
              <View style={[s.radio, on && { borderColor: c.primary }]}>{on && <View style={s.radioDot} />}</View>
              <Text style={[s.optionText, on && s.optionTextOn]}>{r.label}</Text>
              {on && <Icon name="check" size={16} color={c.primary} />}
            </Pressable>
          );
        })}
      </View>

      <Text style={[s.label, { marginTop: 24 }]}>하고 싶은 말 (선택)</Text>
      <TextInput
        value={text}
        onChangeText={(v) => setText(v.slice(0, REASON_TEXT_MAX))}
        placeholder="불편했던 점이나 바라는 점을 적어 주시면 서비스를 고치는 데 쓸게요."
        placeholderTextColor={c.textMuted}
        multiline
        maxLength={REASON_TEXT_MAX}
        editable={!busy}
        style={s.input}
        accessibilityLabel="하고 싶은 말"
      />
      <Text style={s.count}>
        {text.length} / {REASON_TEXT_MAX}
      </Text>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  noticeTitle: { fontSize: 14, fontWeight: '800', color: t.colors.text, marginBottom: 6 },
  noticeText: { fontSize: 13, lineHeight: 21, color: t.colors.textSub },
  label: { fontSize: 15, fontWeight: '800', color: t.colors.text, marginTop: 24, marginBottom: 10 },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1.5,
    borderColor: t.colors.border,
    borderRadius: t.radius.md,
    paddingHorizontal: 14,
    paddingVertical: 13,
    backgroundColor: t.colors.surface,
  },
  optionOn: { borderColor: t.colors.primary, backgroundColor: t.colors.primarySoft },
  radio: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: t.colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: t.colors.primary },
  optionText: { flex: 1, fontSize: 15, color: t.colors.text },
  optionTextOn: { fontWeight: '700' },
  input: { ...inputBase(t), minHeight: 96, textAlignVertical: 'top', paddingTop: 12 },
  count: { fontSize: 12, color: t.colors.textMuted, textAlign: 'right', marginTop: 6 },
}));
