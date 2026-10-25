import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, Switch, Text, View } from 'react-native';

import type { PairingDto, PairingListDto, PairingPermission } from '@/api/types';
import { Badge, Button, Card, confirm, Header, notify, Screen, TraceLabel } from '@/components/ui';
import { api, errorAlertMessage, errorMessage } from '@/lib/api';
import { systemTraceId } from '@/lib/errorText';
import { endConfirmText, pairingStatusText, PERMISSION_DESC, PERMISSION_LABEL, PERMISSIONS } from '@/lib/pairingRules';
import { makeStyles, useColors } from '@/theme';

// 옷장 도우미 (doc/26 4단계): 내 옷장을 도와주는 사람, 내가 돕는 옷장. 코드 만들기·입력은 /helper/code.
// 승인·거절·권한·알림 변경은 주인만, 해제는 양쪽 모두 (서버가 확인한다).
export default function Helper() {
  const [list, setList] = useState<PairingListDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  /** 처리 중인 연결 (연타로 두 번 보내지 않게) */
  const [busy, setBusy] = useState<string | null>(null);
  /** 승인할 때 고르는 권한 (대기 요청마다) */
  const [picked, setPicked] = useState<Record<string, PairingPermission>>({});
  const s = useStyles();
  const c = useColors();

  const load = useCallback(async () => {
    setError(null);
    setTrace(undefined);
    try {
      setList(await api.pairing.list());
    } catch (e) {
      setError(errorMessage(e, '옷장 도우미를 불러오지 못했어요.'));
      setTrace(systemTraceId(e));
    }
  }, []);

  // 알림을 누르거나 다른 화면에서 돌아올 때마다 새로 받는다
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  /** 서버에 한 가지를 요청하고 목록을 다시 받는다. 실패하면 안내만 하고 목록은 그대로 */
  const act = async (id: string, task: () => Promise<unknown>, failTitle: string) => {
    if (busy) return;
    setBusy(id);
    try {
      await task();
      await load();
    } catch (e) {
      notify(failTitle, errorAlertMessage(e, '잠시 후 다시 시도해 주세요.'));
      // 이미 끝난 연결·바뀐 권한일 수 있으니 최신으로 맞춘다
      await load();
    } finally {
      setBusy(null);
    }
  };

  const end = (p: PairingDto) => {
    const t = endConfirmText(p);
    confirm(t.title, t.message, () => void act(p.id, () => api.pairing.end(p.id), '연결을 끊지 못했어요'), t.ok);
  };

  const owners = list?.asOwner ?? [];
  const helpers = list?.asHelper ?? [];

  return (
    <Screen header={<Header title="옷장 도우미" />}>
      <Text style={s.desc}>
        가까운 사람이 내 옷을 대신 등록하거나, 내가 가까운 사람의 옷장을 도울 수 있어요. 연결하려면 코드를 만들어 상대에게
        보내거나, 받은 코드를 입력해 주세요.
      </Text>

      <View style={s.actions}>
        <Button title="코드 만들기" onPress={() => router.push('/helper/code?mode=make')} />
        <Button title="받은 코드 입력하기" variant="outline" onPress={() => router.push('/helper/code?mode=enter')} />
      </View>

      {error ? (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
          <Button title="다시 시도" variant="outline" small onPress={() => void load()} style={{ marginTop: 8 }} />
        </View>
      ) : !list ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} />
      ) : (
        <>
          <Text style={s.section}>내 옷장을 도와주는 사람</Text>
          {owners.length === 0 ? (
            <Text style={s.empty}>아직 없어요. 코드를 만들어 도와줄 사람에게 보내 보세요.</Text>
          ) : (
            owners.map((p) => (
              <Card key={p.id} style={s.card}>
                <View style={s.head}>
                  <Text style={s.name}>{p.partnerName}</Text>
                  <Badge tone={p.status === 'ACTIVE' ? 'green' : 'outline'}>{pairingStatusText(p)}</Badge>
                </View>

                {p.status === 'PENDING' ? (
                  <>
                    <Text style={s.sub}>{p.partnerName}님이 내 옷장을 돕고 싶어 해요. 줄 권한을 골라 주세요.</Text>
                    <PermissionPicker
                      value={picked[p.id] ?? 'EDIT'}
                      onChange={(v) => setPicked((m) => ({ ...m, [p.id]: v }))}
                      disabled={busy === p.id}
                    />
                    <View style={s.row}>
                      <Button
                        title="승인"
                        small
                        disabled={busy === p.id}
                        onPress={() =>
                          void act(p.id, () => api.pairing.approve(p.id, picked[p.id] ?? 'EDIT'), '승인하지 못했어요')
                        }
                        style={s.flex}
                      />
                      <Button
                        title="거절"
                        variant="outline"
                        small
                        disabled={busy === p.id}
                        onPress={() => void act(p.id, () => api.pairing.reject(p.id), '거절하지 못했어요')}
                        style={s.flex}
                      />
                    </View>
                  </>
                ) : (
                  <>
                    <Text style={s.sub}>{p.permission ? PERMISSION_DESC[p.permission] : ''}</Text>
                    <PermissionPicker
                      value={p.permission ?? 'VIEW'}
                      onChange={(v) =>
                        v !== p.permission &&
                        void act(p.id, () => api.pairing.update(p.id, { permission: v }), '권한을 바꾸지 못했어요')
                      }
                      disabled={busy === p.id}
                    />
                    <View style={s.switchRow}>
                      <Text style={s.switchLabel}>대신 정리하면 알려 주기</Text>
                      <Switch
                        value={p.notifyOwner}
                        disabled={busy === p.id}
                        onValueChange={(v) =>
                          void act(p.id, () => api.pairing.update(p.id, { notifyOwner: v }), '알림 설정을 바꾸지 못했어요')
                        }
                        trackColor={{ true: c.primary }}
                      />
                    </View>
                    <View style={s.row}>
                      <Button
                        title="작업 기록"
                        variant="soft"
                        small
                        onPress={() => router.push(`/helper/actions?id=${encodeURIComponent(p.id)}`)}
                        style={s.flex}
                      />
                      <Button
                        title="연결 끊기"
                        variant="danger"
                        small
                        disabled={busy === p.id}
                        onPress={() => end(p)}
                        style={s.flex}
                      />
                    </View>
                  </>
                )}
              </Card>
            ))
          )}

          <Text style={s.section}>내가 돕는 옷장</Text>
          {helpers.length === 0 ? (
            <Text style={s.empty}>아직 없어요. 옷장 주인에게 받은 코드를 입력해 보세요.</Text>
          ) : (
            helpers.map((p) => (
              <Card key={p.id} style={s.card}>
                <View style={s.head}>
                  <Text style={s.name}>{p.partnerName}님의 옷장</Text>
                  <Badge tone={p.status === 'ACTIVE' ? 'green' : 'outline'}>{pairingStatusText(p)}</Badge>
                </View>
                <Text style={s.sub}>
                  {p.status === 'PENDING'
                    ? `${p.partnerName}님이 승인하면 옷을 도울 수 있어요.`
                    : p.permission
                      ? PERMISSION_DESC[p.permission]
                      : ''}
                </Text>
                {p.status === 'ACTIVE' && (
                  <Button
                    title={`${p.partnerName}님 옷장 열기`}
                    small
                    onPress={() =>
                      router.push(`/helper/closet?ownerId=${encodeURIComponent(p.partnerId)}&name=${encodeURIComponent(p.partnerName)}`)
                    }
                  />
                )}
                <View style={s.row}>
                  {p.status === 'ACTIVE' && (
                    <Button
                      title="작업 기록"
                      variant="soft"
                      small
                      onPress={() => router.push(`/helper/actions?id=${encodeURIComponent(p.id)}`)}
                      style={s.flex}
                    />
                  )}
                  <Button
                    title={p.status === 'PENDING' ? '요청 취소' : '연결 끊기'}
                    variant="danger"
                    small
                    disabled={busy === p.id}
                    onPress={() => end(p)}
                    style={s.flex}
                  />
                </View>
              </Card>
            ))
          )}
        </>
      )}
    </Screen>
  );
}

/** 보기만 · 등록·수정 중 하나 고르기 */
function PermissionPicker({
  value,
  onChange,
  disabled,
}: {
  value: PairingPermission;
  onChange: (v: PairingPermission) => void;
  disabled?: boolean;
}) {
  const s = useStyles();
  return (
    <View style={s.picker}>
      {PERMISSIONS.map((p) => (
        <Pressable
          key={p}
          disabled={disabled}
          onPress={() => onChange(p)}
          accessibilityRole="radio"
          accessibilityState={{ selected: value === p, disabled: !!disabled }}
          style={[s.pick, value === p && s.pickOn, disabled && { opacity: 0.5 }]}
        >
          <Text style={[s.pickText, value === p && s.pickTextOn]}>{PERMISSION_LABEL[p]}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21, marginBottom: 16 },
  actions: { gap: 10 },
  section: { fontSize: 15, fontWeight: '800', color: t.colors.text, marginTop: 28, marginBottom: 10 },
  empty: { color: t.colors.textMuted, fontSize: 13, lineHeight: 19 },
  card: { gap: 10, marginBottom: 10 },
  head: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  name: { flex: 1, fontSize: 15, fontWeight: '800', color: t.colors.text },
  sub: { fontSize: 13, color: t.colors.textSub, lineHeight: 19 },
  row: { flexDirection: 'row', gap: 8 },
  flex: { flex: 1 },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  switchLabel: { fontSize: 14, color: t.colors.text, fontWeight: '600' },
  picker: { flexDirection: 'row', backgroundColor: t.colors.surfaceAlt, borderRadius: t.radius.pill, padding: 4 },
  pick: { flex: 1, alignItems: 'center', paddingVertical: 8, borderRadius: t.radius.pill },
  pickOn: { backgroundColor: t.colors.primary },
  pickText: { fontSize: 14, color: t.colors.textSub, fontWeight: '600' },
  pickTextOn: { color: t.colors.onPrimary },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4, marginTop: 20 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
