import * as Clipboard from 'expo-clipboard';
import * as SMS from 'expo-sms';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Share, Text, View } from 'react-native';

import type { InviteChannel, InviteDto, InvitedFriendDto } from '@/api/types';
import { Icon } from '@/components/Icon';
import { Button, Card, Header, notify, Screen, TraceLabel } from '@/components/ui';
import { api, errorMessage } from '@/lib/api';
import { systemTraceId } from '@/lib/errorText';
import { formatInviteCode, inviteMessage } from '@/lib/invite';
import { makeStyles, useColors } from '@/theme';

// 친구 초대 (doc/25): 내 초대 코드를 OS 공유 시트(카카오톡·메시지 등)·문자·복사로 보내고, 초대로 가입한 친구를 본다.
// 보낼 때마다 초대한 이력을 서버에 남긴다 (실패해도 화면은 막지 않는다).
export default function Invite() {
  const [invite, setInvite] = useState<InviteDto | null>(null);
  const [friends, setFriends] = useState<InvitedFriendDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  const [smsReady, setSmsReady] = useState(false);
  const [copied, setCopied] = useState(false);
  /** 공유 시트·문자 앱이 열려 있는 동안 (연타로 두 번 뜨지 않게, ref는 같은 순간의 두 번째 탭까지 막는다) */
  const [opening, setOpening] = useState(false);
  const openingRef = useRef(false);
  const s = useStyles();
  const c = useColors();

  const load = async () => {
    setError(null);
    setTrace(undefined);
    try {
      const [mine, list] = await Promise.all([api.invite.get(), api.invite.friends()]);
      setInvite(mine);
      setFriends(list);
    } catch (e) {
      setError(errorMessage(e, '초대 코드를 불러오지 못했어요.'));
      setTrace(systemTraceId(e));
    }
  };

  useEffect(() => {
    void load();
    // 웹·시뮬레이터는 문자를 보낼 수 없어 버튼을 숨긴다
    SMS.isAvailableAsync()
      .then(setSmsReady)
      .catch(() => setSmsReady(false));
  }, []);

  const record = (channel: InviteChannel) => {
    api.invite.recordShare(channel).catch((e) => console.warn('[invite] 초대 기록 실패', e));
  };

  /** 공유 시트·문자 앱을 하나만 연다. 이미 열려 있으면 무시한다 */
  const openOnce = async (task: () => Promise<void>) => {
    if (openingRef.current) return;
    openingRef.current = true;
    setOpening(true);
    try {
      await task();
    } finally {
      openingRef.current = false;
      setOpening(false);
    }
  };

  const share = () =>
    openOnce(async () => {
      if (!invite) return;
      try {
        const res = await Share.share({ message: inviteMessage(invite.inviteCode) });
        // iOS는 닫으면 dismissedAction. Android는 보냈는지 알 수 없어 시트를 연 것으로 기록한다
        if (res.action !== Share.dismissedAction) record('SHARE');
      } catch (e) {
        console.warn('[invite] 공유 실패', e);
        notify('공유하지 못했어요', '코드를 복사해서 직접 보내 주세요.');
      }
    });

  const sms = () =>
    openOnce(async () => {
      if (!invite) return;
      try {
        const res = await SMS.sendSMSAsync([], inviteMessage(invite.inviteCode));
        if (res.result !== 'cancelled') record('SMS');
      } catch (e) {
        console.warn('[invite] 문자 실패', e);
        notify('문자를 열지 못했어요', '코드를 복사해서 직접 보내 주세요.');
      }
    });

  const copy = async () => {
    if (!invite) return;
    await Clipboard.setStringAsync(formatInviteCode(invite.inviteCode));
    // 복사는 매번 하지만 기록은 이 화면에서 처음 한 번만 (서버도 같은 방법은 60초에 한 번만 남긴다, doc/25 6장)
    if (!copied) record('COPY');
    setCopied(true);
  };

  return (
    <Screen header={<Header title="친구 초대" />}>
      <Text style={s.desc}>
        친구에게 웨어리를 알려 주세요. 친구가 가입할 때 내 초대 코드를 넣으면 초대한 친구로 기록돼요.
      </Text>

      {error ? (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
          <Button title="다시 시도" variant="outline" small onPress={() => void load()} style={{ marginTop: 8 }} />
        </View>
      ) : !invite ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} />
      ) : (
        <>
          <Card style={s.codeCard}>
            <Text style={s.codeLabel}>내 초대 코드</Text>
            <Text style={s.code} selectable accessibilityLabel={`초대 코드 ${invite.inviteCode.split('').join(' ')}`}>
              {formatInviteCode(invite.inviteCode)}
            </Text>
            <Button
              title={copied ? '복사했어요' : '코드 복사'}
              variant="soft"
              small
              icon={<Icon name={copied ? 'check' : 'clipboard'} size={16} color={c.primary} />}
              onPress={() => void copy()}
            />
          </Card>

          <View style={s.actions}>
            {Platform.OS !== 'web' && (
              <Button
                title="카카오톡·다른 앱으로 공유"
                icon={<Icon name="share" size={18} color={c.onPrimary} />}
                onPress={() => void share()}
                disabled={opening}
              />
            )}
            {smsReady && (
              <Button title="문자로 보내기" variant="outline" onPress={() => void sms()} disabled={opening} />
            )}
          </View>

          <Text style={s.section}>초대로 가입한 친구 {invite.invitedCount}명</Text>
          {friends.length === 0 ? (
            <Text style={s.empty}>아직 없어요. 코드를 보내 보세요.</Text>
          ) : (
            <Card style={s.list}>
              {friends.map((f, i) => (
                <View key={`${f.joinedAt}-${i}`} style={[s.row, i === friends.length - 1 && { borderBottomWidth: 0 }]}>
                  <Text style={s.name}>{f.name}</Text>
                  <Text style={s.date}>{f.joinedAt.slice(0, 10)} 가입</Text>
                </View>
              ))}
            </Card>
          )}
        </>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21, marginBottom: 18 },
  codeCard: { alignItems: 'center', gap: 10, paddingVertical: 22 },
  codeLabel: { fontSize: 13, color: t.colors.textSub, fontWeight: '700' },
  code: { fontSize: 30, fontWeight: '800', letterSpacing: 3, color: t.colors.text },
  actions: { gap: 10, marginTop: 18 },
  section: { fontSize: 15, fontWeight: '800', color: t.colors.text, marginTop: 28, marginBottom: 10 },
  empty: { color: t.colors.textMuted, fontSize: 13 },
  list: { paddingVertical: 4 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  name: { fontSize: 14, fontWeight: '700', color: t.colors.text },
  date: { fontSize: 12, color: t.colors.textMuted },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
