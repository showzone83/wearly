import * as Clipboard from 'expo-clipboard';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Platform, Pressable, Share, Text, TextInput, View } from 'react-native';

import { isApiError } from '@/api/errors';
import type { PairingCodeDto, PairingPermission, PairingRole } from '@/api/types';
import { Icon } from '@/components/Icon';
import { Button, Card, Header, notify, Screen, TraceLabel } from '@/components/ui';
import { api, errorMessage } from '@/lib/api';
import { fieldErrorText, systemTraceId } from '@/lib/errorText';
import {
  expiresInText,
  formatPairingCode,
  isCompleteCode,
  PERMISSION_DESC,
  PERMISSION_LABEL,
  PERMISSIONS,
} from '@/lib/pairingRules';
import { inputBase, makeStyles, useColors } from '@/theme';

type Mode = 'make' | 'enter';

// 옷장 도우미 코드 (doc/26 4단계): 코드 만들기(내 자리를 골라 코드를 받아 상대에게 보낸다) / 받은 코드 입력하기.
// 주인 코드를 돕는 사람이 넣으면 주인 승인을 기다리고, 돕는 사람 코드를 주인이 넣으면 고른 권한으로 바로 연결된다.
export default function HelperCode() {
  const params = useLocalSearchParams<{ mode?: string }>();
  const [mode, setMode] = useState<Mode>(params.mode === 'enter' ? 'enter' : 'make');
  const s = useStyles();

  return (
    <Screen header={<Header title="옷장 도우미 코드" />}>
      <View style={s.tabs}>
        {(
          [
            ['make', '코드 만들기'],
            ['enter', '코드 입력하기'],
          ] as const
        ).map(([m, label]) => (
          <Pressable
            key={m}
            onPress={() => setMode(m)}
            accessibilityRole="tab"
            accessibilityState={{ selected: mode === m }}
            style={[s.tab, mode === m && s.tabOn]}
          >
            <Text style={[s.tabText, mode === m && { color: '#fff' }]}>{label}</Text>
          </Pressable>
        ))}
      </View>
      {mode === 'make' ? <MakeCode /> : <EnterCode />}
    </Screen>
  );
}

/** 내 자리를 골라 코드를 만들고 보낸다 */
function MakeCode() {
  const [role, setRole] = useState<PairingRole>('HELPER');
  const [issued, setIssued] = useState<PairingCodeDto | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  const [copied, setCopied] = useState(false);
  const [now, setNow] = useState(Date.now());
  const s = useStyles();
  const c = useColors();

  // 남은 시간을 1분마다 갱신
  useEffect(() => {
    if (!issued) return;
    const id = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(id);
  }, [issued]);

  const make = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    setTrace(undefined);
    try {
      const code = await api.pairing.issueCode(role);
      setIssued(code);
      setCopied(false);
      setNow(Date.now());
    } catch (e) {
      setError(errorMessage(e, '코드를 만들지 못했어요.'));
      setTrace(systemTraceId(e));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const message = (code: string) =>
    role === 'OWNER'
      ? `웨어리 옷장 도우미 코드예요. 내 옷장을 도와주세요.\n웨어리 > 마이 > 옷장 도우미 > 받은 코드 입력하기에서 ${formatPairingCode(code)} 를 넣어 주세요. (하루 안에)`
      : `웨어리 옷장 도우미 코드예요. 제가 ${'당신'}의 옷장을 도울게요.\n웨어리 > 마이 > 옷장 도우미 > 받은 코드 입력하기에서 ${formatPairingCode(code)} 를 넣고 권한을 골라 주세요. (하루 안에)`;

  const share = async () => {
    if (!issued) return;
    try {
      await Share.share({ message: message(issued.code) });
    } catch (e) {
      console.warn('[helper] 공유 실패', e);
      notify('공유하지 못했어요', '코드를 복사해서 직접 보내 주세요.');
    }
  };

  const copy = async () => {
    if (!issued) return;
    await Clipboard.setStringAsync(formatPairingCode(issued.code));
    setCopied(true);
  };

  if (issued) {
    return (
      <>
        <Card style={s.codeCard}>
          <Text style={s.codeLabel}>{issued.role === 'OWNER' ? '내 옷장을 맡기는 코드' : '내가 돕겠다는 코드'}</Text>
          <Text style={s.code} selectable accessibilityLabel={`코드 ${issued.code.split('').join(' ')}`}>
            {formatPairingCode(issued.code)}
          </Text>
          <Text style={s.expire}>{expiresInText(issued.expiresAt, now)} · 한 번만 쓸 수 있어요</Text>
          <Button
            title={copied ? '복사했어요' : '코드 복사'}
            variant="soft"
            small
            icon={<Icon name={copied ? 'check' : 'clipboard'} size={16} color={c.primary} />}
            onPress={() => void copy()}
          />
        </Card>
        <Text style={s.hint}>
          {issued.role === 'OWNER'
            ? '상대가 코드를 입력하면 승인 요청이 알림으로 와요. 승인하면 연결돼요.'
            : '주인이 코드를 입력하고 권한을 고르면 바로 연결돼요.'}
        </Text>
        <View style={s.actions}>
          {Platform.OS !== 'web' && (
            <Button
              title="카카오톡·다른 앱으로 보내기"
              icon={<Icon name="share" size={18} color={c.onPrimary} />}
              onPress={() => void share()}
            />
          )}
          <Button title="새 코드 만들기" variant="outline" onPress={() => setIssued(null)} />
          <Button title="옷장 도우미로 돌아가기" variant="ghost" onPress={() => router.back()} />
        </View>
      </>
    );
  }

  return (
    <>
      <Text style={s.label}>어느 쪽인가요?</Text>
      <RoleOption
        selected={role === 'HELPER'}
        title="내가 옷장을 도와줄게요"
        desc="가까운 사람의 옷을 내가 대신 등록해요. 주인이 코드를 입력하고 권한을 골라요."
        onPress={() => setRole('HELPER')}
      />
      <RoleOption
        selected={role === 'OWNER'}
        title="내 옷장을 도와줄 사람을 찾아요"
        desc="상대가 코드를 입력하면 내가 승인해요."
        onPress={() => setRole('OWNER')}
      />
      {error && (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
        </View>
      )}
      <View style={s.actions}>
        <Button title="코드 만들기" onPress={() => void make()} disabled={busy} />
      </View>
      <Text style={s.foot}>코드는 24시간 동안, 한 번만 쓸 수 있어요. 새로 만들면 이전 코드는 사라져요.</Text>
    </>
  );
}

/** 받은 코드를 넣는다 */
function EnterCode() {
  const [code, setCode] = useState('');
  /** 이 코드를 만든 쪽: 주인이면 내가 돕는 사람, 돕는 사람이면 내가 주인(권한을 정한다) */
  const [from, setFrom] = useState<PairingRole>('OWNER');
  const [permission, setPermission] = useState<PairingPermission>('EDIT');
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  const s = useStyles();

  const submit = async () => {
    if (busyRef.current || !isCompleteCode(code)) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    setTrace(undefined);
    try {
      const joined = await api.pairing.join(code, from === 'HELPER' ? permission : undefined);
      notify(
        joined.status === 'PENDING' ? '요청을 보냈어요' : '연결됐어요',
        joined.status === 'PENDING'
          ? `${joined.partnerName}님이 승인하면 옷장을 도울 수 있어요.`
          : `${joined.partnerName}님과 연결됐어요.`,
      );
      router.back();
    } catch (e) {
      // 돕는 사람이 만든 코드인데 "주인 코드"로 골랐으면 권한이 없다고 돌아온다
      const needsPermission =
        isApiError(e) && e.code === 'VALIDATION_FAILED' && e.fieldErrors.some((f) => f.field === 'permission');
      setError(
        needsPermission
          ? '돕는 사람이 만든 코드예요. 아래에서 "돕는 사람이 만든 코드"를 골라 주세요.'
          : isApiError(e) && e.fieldErrors.length > 0
            ? e.fieldErrors.map((f) => fieldErrorText(f)).join('\n')
            : errorMessage(e, '코드를 입력하지 못했어요.'),
      );
      setTrace(systemTraceId(e));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  return (
    <>
      <Text style={s.label}>받은 코드</Text>
      <TextInput
        value={formatPairingCode(code)}
        onChangeText={(v) => setCode(formatPairingCode(v))}
        placeholder="ABCD-EFGH"
        autoCapitalize="characters"
        autoCorrect={false}
        maxLength={9}
        style={s.input}
        accessibilityLabel="받은 코드"
        placeholderTextColor="#9AA0A6"
      />

      <Text style={[s.label, { marginTop: 20 }]}>이 코드는 누가 만들었나요?</Text>
      <RoleOption
        selected={from === 'OWNER'}
        title="옷장 주인이 만든 코드"
        desc="내가 그 사람의 옷장을 도와요. 주인이 승인하면 연결돼요."
        onPress={() => setFrom('OWNER')}
      />
      <RoleOption
        selected={from === 'HELPER'}
        title="돕는 사람이 만든 코드"
        desc="내가 옷장 주인이에요. 줄 권한을 골라 바로 연결해요."
        onPress={() => setFrom('HELPER')}
      />
      {from === 'HELPER' && (
        <>
          <Text style={[s.label, { marginTop: 8 }]}>줄 권한</Text>
          <View style={s.picker}>
            {PERMISSIONS.map((p) => (
              <Pressable
                key={p}
                onPress={() => setPermission(p)}
                accessibilityRole="radio"
                accessibilityState={{ selected: permission === p }}
                style={[s.pick, permission === p && s.pickOn]}
              >
                <Text style={[s.pickText, permission === p && { color: '#fff' }]}>{PERMISSION_LABEL[p]}</Text>
              </Pressable>
            ))}
          </View>
          <Text style={s.hint}>{PERMISSION_DESC[permission]}</Text>
        </>
      )}

      {error && (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
        </View>
      )}
      <View style={s.actions}>
        <Button title="입력하기" onPress={() => void submit()} disabled={busy || !isCompleteCode(code)} />
      </View>
    </>
  );
}

function RoleOption({
  selected,
  title,
  desc,
  onPress,
}: {
  selected: boolean;
  title: string;
  desc: string;
  onPress: () => void;
}) {
  const s = useStyles();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      style={[s.option, selected && s.optionOn]}
    >
      <Text style={s.optionTitle}>{title}</Text>
      <Text style={s.optionDesc}>{desc}</Text>
    </Pressable>
  );
}

const useStyles = makeStyles((t) => ({
  tabs: {
    flexDirection: 'row',
    backgroundColor: t.colors.surfaceAlt,
    borderRadius: t.radius.pill,
    padding: 4,
    marginBottom: 22,
  },
  tab: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: t.radius.pill },
  tabOn: { backgroundColor: t.colors.primary },
  tabText: { fontSize: 15, color: t.colors.textSub, fontWeight: '600' },
  label: { fontSize: 14, color: t.colors.text, fontWeight: '700', marginBottom: 8 },
  input: { ...inputBase(t), fontSize: 22, letterSpacing: 3, fontWeight: '700', textAlign: 'center' },
  option: {
    borderWidth: 1.5,
    borderColor: t.colors.border,
    borderRadius: t.radius.md,
    padding: 14,
    gap: 4,
    marginBottom: 10,
    backgroundColor: t.colors.surface,
  },
  optionOn: { borderColor: t.colors.primary, backgroundColor: t.colors.primarySoft },
  optionTitle: { fontSize: 15, fontWeight: '800', color: t.colors.text },
  optionDesc: { fontSize: 13, color: t.colors.textSub, lineHeight: 19 },
  picker: { flexDirection: 'row', backgroundColor: t.colors.surfaceAlt, borderRadius: t.radius.pill, padding: 4 },
  pick: { flex: 1, alignItems: 'center', paddingVertical: 8, borderRadius: t.radius.pill },
  pickOn: { backgroundColor: t.colors.primary },
  pickText: { fontSize: 14, color: t.colors.textSub, fontWeight: '600' },
  codeCard: { alignItems: 'center', gap: 10, paddingVertical: 22 },
  codeLabel: { fontSize: 13, color: t.colors.textSub, fontWeight: '700' },
  code: { fontSize: 30, fontWeight: '800', letterSpacing: 3, color: t.colors.text },
  expire: { fontSize: 12, color: t.colors.textMuted },
  hint: { color: t.colors.textSub, fontSize: 13, lineHeight: 19, marginTop: 14 },
  actions: { gap: 10, marginTop: 20 },
  foot: { color: t.colors.textMuted, fontSize: 12, textAlign: 'center', marginTop: 20 },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4, marginTop: 16 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
