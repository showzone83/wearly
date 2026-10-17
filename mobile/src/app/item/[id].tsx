import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { ItemForm, type ItemDraft } from '@/components/ItemForm';
import { ItemThumb } from '@/components/ItemThumb';
import { Button, confirm, Header, notify, Row, Screen } from '@/components/ui';
import { removeItem, saveItem } from '@/lib/account';
import { errorMessage } from '@/lib/api';
import { diffDays, todayKey } from '@/lib/date';
import { josa } from '@/lib/josa';
import { useStore } from '@/store/useStore';
import { cardBase, makeStyles, useColors } from '@/theme';

export default function ItemDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const item = useStore((s) => s.items.find((i) => i.id === id));
  const [draft, setDraft] = useState<ItemDraft | null>(item ?? null);
  const [busy, setBusy] = useState(false);
  const s = useStyles();
  const colors = useColors();

  if (!item || !draft) {
    return (
      <Screen header={<Header title="아이템" />}>
        <Text style={{ color: colors.textSub, marginTop: 40, textAlign: 'center' }}>삭제되었거나 없는 아이템이에요.</Text>
      </Screen>
    );
  }

  const since = item.lastWornAt ? diffDays(todayKey(), item.lastWornAt) : null;
  const dirty = JSON.stringify(draft) !== JSON.stringify({ ...item, ...draft });

  // 계정 모드는 서버에 먼저 반영하고, 성공하면 기기 상태를 바꾼다
  const onDelete = async () => {
    setBusy(true);
    try {
      await removeItem(item.id);
      router.back();
    } catch (e) {
      notify('삭제하지 못했어요', errorMessage(e));
      setBusy(false);
    }
  };

  const onSave = async () => {
    setBusy(true);
    try {
      const exists = await saveItem(item.id, draft);
      if (!exists) notify('이미 삭제된 옷이에요', '다른 기기에서 지운 옷이라 이 기기에서도 지웠어요.');
      router.back();
    } catch (e) {
      notify('저장하지 못했어요', errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <Screen
      header={
        <Header
          title="아이템 상세"
          right={
            <Pressable
              hitSlop={10}
              accessibilityLabel="삭제"
              disabled={busy}
              onPress={() =>
                confirm('옷을 삭제할까요?', `${josa(item.name, '을', '를')} 옷장에서 지워요. 저장한 코디에서도 빠져요.`, () => void onDelete(), '삭제')
              }
            >
              <Icon name="trash" color={colors.danger} />
            </Pressable>
          }
        />
      }
      footer={
        <Button
          title={busy ? '' : dirty ? '수정 내용 저장' : '저장됨'}
          icon={busy ? <ActivityIndicator color={colors.onPrimary} /> : undefined}
          disabled={!dirty || busy}
          onPress={onSave}
        />
      }
    >
      <View style={{ alignItems: 'center' }}>
        <ItemThumb item={{ ...item, ...draft }} size={200} radius={28} />
      </View>
      <Row style={s.stats}>
        <View style={s.stat}>
          <Text style={s.statNum}>{item.wornCount}회</Text>
          <Text style={s.statLabel}>착용</Text>
        </View>
        <View style={s.stat}>
          <Text style={s.statNum}>{since === null ? '-' : since === 0 ? '오늘' : `${since}일 전`}</Text>
          <Text style={s.statLabel}>마지막 착용</Text>
        </View>
        <View style={s.stat}>
          <Text style={[s.statNum, { color: since === null || since >= 30 ? colors.danger : colors.primary }]}>
            {since === null || since >= 30 ? '잠자는 옷' : '활발'}
          </Text>
          <Text style={s.statLabel}>활용 상태</Text>
        </View>
      </Row>
      <ItemForm value={draft} onChange={setDraft} />
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  stats: { ...cardBase(t), marginVertical: 22 },
  stat: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  statNum: { fontSize: 16, fontWeight: '800', color: t.colors.text },
  statLabel: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
}));
