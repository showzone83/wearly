import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';

import type { ProxyActionDto } from '@/api/types';
import { Button, Card, Header, Screen, TraceLabel } from '@/components/ui';
import { api, errorMessage } from '@/lib/api';
import { systemTraceId } from '@/lib/errorText';
import { ACTION_LABEL } from '@/lib/pairingRules';
import { makeStyles, useColors } from '@/theme';

// 옷장 도우미 작업 기록 (doc/26 4단계): 돕는 사람이 주인 옷장에서 한 일 (최근 순 최대 100건).
// 주인·돕는 사람 모두 볼 수 있고, 주인이 옷을 완전 삭제해도 기록은 그때 이름으로 남는다.
export default function HelperActions() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const [rows, setRows] = useState<ProxyActionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  const s = useStyles();
  const c = useColors();

  const load = useCallback(async () => {
    if (!id) {
      setError('연결을 찾을 수 없어요.');
      return;
    }
    setError(null);
    setTrace(undefined);
    try {
      setRows(await api.pairing.actions(id));
    } catch (e) {
      setError(errorMessage(e, '작업 기록을 불러오지 못했어요.'));
      setTrace(systemTraceId(e));
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  return (
    <Screen header={<Header title="작업 기록" />}>
      <Text style={s.desc}>돕는 사람이 옷장에서 한 일이에요. 최근 100건까지 보여요.</Text>
      {error ? (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
          <Button title="다시 시도" variant="outline" small onPress={() => void load()} style={{ marginTop: 8 }} />
        </View>
      ) : !rows ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} />
      ) : rows.length === 0 ? (
        <Text style={s.empty}>아직 한 일이 없어요.</Text>
      ) : (
        <Card style={s.list}>
          {rows.map((r, i) => (
            <View key={r.id} style={[s.row, i === rows.length - 1 && { borderBottomWidth: 0 }]}>
              <View style={s.main}>
                <Text style={s.name} numberOfLines={1}>
                  {r.itemName?.trim() || '이름 없는 옷'}
                </Text>
                <Text style={s.when}>{formatWhen(r.createdAt)}</Text>
              </View>
              <Text style={s.action}>{ACTION_LABEL[r.action] ?? r.action}</Text>
            </View>
          ))}
        </Card>
      )}
    </Screen>
  );
}

/** "10월 25일 14:30" (기기 시간대) */
const formatWhen = (iso: string): string => {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${hh}:${mm}`;
};

const useStyles = makeStyles((t) => ({
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21, marginBottom: 16 },
  empty: { color: t.colors.textMuted, fontSize: 13 },
  list: { paddingVertical: 4 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  main: { flex: 1, gap: 2 },
  name: { fontSize: 14, fontWeight: '700', color: t.colors.text },
  when: { fontSize: 12, color: t.colors.textMuted },
  action: { fontSize: 13, fontWeight: '800', color: t.colors.primary },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
