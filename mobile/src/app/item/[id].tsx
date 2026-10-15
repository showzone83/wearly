import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { ItemForm, type ItemDraft } from '@/components/ItemForm';
import { ItemThumb } from '@/components/ItemThumb';
import { Button, confirm, Header, Row, Screen } from '@/components/ui';
import { diffDays, todayKey } from '@/lib/date';
import { josa } from '@/lib/josa';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

export default function ItemDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const item = useStore((s) => s.items.find((i) => i.id === id));
  const updateItem = useStore((s) => s.updateItem);
  const deleteItem = useStore((s) => s.deleteItem);
  const [draft, setDraft] = useState<ItemDraft | null>(item ?? null);

  if (!item || !draft) {
    return (
      <Screen header={<Header title="아이템" />}>
        <Text style={{ color: colors.sub, marginTop: 40, textAlign: 'center' }}>삭제되었거나 없는 아이템이에요.</Text>
      </Screen>
    );
  }

  const since = item.lastWornAt ? diffDays(todayKey(), item.lastWornAt) : null;
  const dirty = JSON.stringify(draft) !== JSON.stringify({ ...item, ...draft });

  return (
    <Screen
      header={
        <Header
          title="아이템 상세"
          right={
            <Pressable
              hitSlop={10}
              accessibilityLabel="삭제"
              onPress={() =>
                confirm('옷을 삭제할까요?', `${josa(item.name, '을', '를')} 옷장에서 지워요. 저장한 코디에서도 빠져요.`, () => {
                  deleteItem(item.id);
                  router.back();
                }, '삭제')
              }
            >
              <Icon name="trash" color={colors.danger} />
            </Pressable>
          }
        />
      }
      footer={
        <Button
          title={dirty ? '수정 내용 저장' : '저장됨'}
          disabled={!dirty}
          onPress={() => {
            updateItem(item.id, draft);
            router.back();
          }}
        />
      }
    >
      <View style={{ alignItems: 'center' }}>
        <ItemThumb item={{ ...item, ...draft }} size={200} />
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
          <Text style={[s.statNum, { color: since === null || since >= 30 ? colors.danger : colors.green }]}>
            {since === null || since >= 30 ? '잠자는 옷' : '활발'}
          </Text>
          <Text style={s.statLabel}>활용 상태</Text>
        </View>
      </Row>
      <ItemForm value={draft} onChange={setDraft} />
    </Screen>
  );
}

const s = StyleSheet.create({
  stats: { marginVertical: 22, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line },
  stat: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  statNum: { fontSize: 16, fontWeight: '800', color: colors.ink },
  statLabel: { fontSize: 12, color: colors.sub, marginTop: 4 },
});
