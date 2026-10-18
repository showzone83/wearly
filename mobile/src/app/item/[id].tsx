import { useState } from 'react';

import { Icon } from '@/components/Icon';
import { draftOf, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { ItemThumb } from '@/components/ItemThumb';
import { RetireSheet } from '@/components/RetireSheet';
import { Button, confirm, Divider, Header, notify, Row, Screen, SectionHead } from '@/components/ui';

import { useStore } from '@/store/useStore';

import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { removeItem, restoreItem, retireItem, saveItem } from '@/lib/account';
import { errorAlertMessage } from '@/lib/api';
import { colorLabel, colorMeta } from '@/lib/codes';
import { diffDays, todayKey } from '@/lib/date';
import { itemColors, purchaseInfo, purchaseText } from '@/lib/itemAttrs';
import { josa } from '@/lib/josa';
import { isRetired, retiredSummary } from '@/lib/retire';
import { cardBase, makeStyles, useColors } from '@/theme';

import type { WardrobeItem } from '@/data/types';

/** 완전 삭제 확인 문구 (doc/13 4장 그대로) */
const DELETE_MESSAGE =
  "사진과 정보가 지워지고 되돌릴 수 없어요. 이 옷이 들어간 코디·기록에서도 빠져요. 버리거나 기부한 옷이라면 '사용하지 않는 옷'으로 옮기는 걸 권해요.";

const ItemDetail = () => {
  const { id } = useLocalSearchParams<{ id: string }>();
  const item = useStore((st) => st.items.find((i) => i.id === id));
  const [draft, setDraft] = useState<ItemDraft | null>(item ? draftOf(item) : null);
  const [busy, setBusy] = useState(false);
  /** 사용 중지 시트 */
  const [sheetOpen, setSheetOpen] = useState(false);
  const s = useStyles();
  const colors = useColors();

  if (!item || !draft) {
    return (
      <Screen header={<Header title="아이템" />}>
        <Text style={{ color: colors.textSub, marginTop: 40, textAlign: 'center' }}>
          삭제되었거나 없는 아이템이에요.
        </Text>
      </Screen>
    );
  }

  const retired = isRetired(item);
  // 저장된 값 기준 (폼에서 고치는 중인 값이 아니다)
  const savedColors = itemColors(item);
  const since = item.lastWornAt ? diffDays(todayKey(), item.lastWornAt) : null;
  const sleeping = since === null || since >= 30;
  // 폼 값끼리만 비교한다 (draftOf가 키 순서를 맞춘다). 사진은 이 화면에서 바꾸지 않으므로 비교하지 않는다
  // (화면을 연 뒤 사진 옮기기·주소 갱신으로 사진 필드가 바뀌어도 '수정됨'이 되지 않게)
  const formKey = (d: ItemDraft | WardrobeItem) => JSON.stringify({ ...draftOf(d), imageUri: undefined });
  const dirty = formKey(draft) !== formKey(item);

  /** 다른 기기에서 지운 옷이라 기기에서도 지웠을 때 */
  const gone = () => {
    notify('이미 삭제된 옷이에요', '다른 기기에서 지운 옷이라 이 기기에서도 지웠어요.');
    router.back();
  };

  // 계정 모드는 서버에 먼저 반영하고, 성공하면 기기 상태를 바꾼다
  const onDelete = async () => {
    setBusy(true);
    try {
      await removeItem(item.id);
      router.back();
    } catch (e) {
      notify('삭제하지 못했어요', errorAlertMessage(e));
      setBusy(false);
    }
  };

  const onAskDelete = () =>
    confirm(`${josa(item.name, '을', '를')} 완전히 삭제할까요?`, DELETE_MESSAGE, () => void onDelete(), '완전 삭제');

  const onRetire = async (reason: string, memo: string) => {
    setBusy(true);
    try {
      const exists = await retireItem(item.id, { reason, memo });
      setSheetOpen(false);
      if (!exists) gone();
    } catch (e) {
      notify('옮기지 못했어요', errorAlertMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const onRestore = async () => {
    setBusy(true);
    try {
      const exists = await restoreItem(item.id);
      if (!exists) gone();
    } catch (e) {
      notify('다시 사용하지 못했어요', errorAlertMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const onSave = async () => {
    setBusy(true);
    try {
      // 사진은 지금 아이템 값 그대로 (폼을 연 뒤 바뀌었을 수 있다)
      const exists = await saveItem(item.id, { ...draft, imageUri: item.imageUri, image: item.image });
      if (!exists) notify('이미 삭제된 옷이에요', '다른 기기에서 지운 옷이라 이 기기에서도 지웠어요.');
      router.back();
    } catch (e) {
      notify('저장하지 못했어요', errorAlertMessage(e));
      setBusy(false);
    }
  };

  return (
    <Screen
      header={<Header title="아이템 상세" />}
      footer={
        <Button
          title={busy ? '' : dirty ? '수정 내용 저장' : '저장됨'}
          icon={busy ? <ActivityIndicator color={colors.onPrimary} /> : undefined}
          disabled={!dirty || busy}
          onPress={onSave}
        />
      }
    >
      {retired && (
        <View style={s.banner} accessibilityRole="summary">
          <Row style={{ gap: 8 }}>
            <Icon name="hanger" size={18} color={colors.textSub} />
            <Text style={s.bannerTitle}>{retiredSummary(item)}</Text>
          </Row>
          {item.retireMemo ? <Text style={s.bannerMemo}>“{item.retireMemo}”</Text> : null}
          <Text style={s.bannerNote}>옷장과 추천에서 빠져 있어요. 착용 기록은 그대로 남아요.</Text>
          <Button
            title="다시 사용하기"
            variant="outline"
            small
            disabled={busy}
            onPress={() => void onRestore()}
            style={s.bannerButton}
          />
        </View>
      )}

      <View style={{ alignItems: 'center' }}>
        <ItemThumb
          item={{ ...item, ...draft, imageUri: item.imageUri, image: item.image }}
          size={200}
          radius={28}
          dim={retired}
          variant="full"
        />
        <View style={s.attrs} accessible accessibilityLabel={`색 ${savedColors.map(colorLabel).join(', ')}`}>
          {savedColors.map((c, i) => (
            <View
              key={c}
              style={[
                i === 0 ? s.mainDot : s.subDot,
                { backgroundColor: colorMeta(c).hex },
                colorMeta(c).light >= 0.9 && s.dotEdge,
              ]}
            />
          ))}
          <Text style={s.attrText}>
            {colorLabel(savedColors[0])}
            {savedColors.length > 1 ? ` + ${savedColors.slice(1).map(colorLabel).join('·')}` : ''}
          </Text>
        </View>
        <Text style={s.purchase}>구입일 {purchaseText(item)}</Text>
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
          <Text style={[s.statNum, { color: retired ? colors.textMuted : sleeping ? colors.danger : colors.primary }]}>
            {retired ? '사용 안 함' : sleeping ? '잠자는 옷' : '활발'}
          </Text>
          <Text style={s.statLabel}>활용 상태</Text>
        </View>
      </Row>
      <ItemForm value={draft} onChange={setDraft} purchaseBaseDate={purchaseInfo(item).date} />

      <SectionHead title="관리" style={{ marginTop: 32 }} />
      <View style={s.manage}>
        <Pressable style={s.manageRow} disabled={busy} onPress={() => setSheetOpen(true)} accessibilityRole="button">
          <View style={{ flex: 1 }}>
            <Text style={s.manageTitle}>{retired ? '사용하지 않는 이유 바꾸기' : '사용하지 않는 옷으로'}</Text>
            <Text style={s.manageSub}>
              {retired ? '이유와 메모를 고칠 수 있어요.' : '옷장과 추천에서는 빠지고, 착용 기록은 그대로 남아요.'}
            </Text>
          </View>
          <Icon name="chevron" size={16} color={colors.textMuted} />
        </Pressable>
        <Divider />
        <Pressable style={s.manageRow} disabled={busy} onPress={onAskDelete} accessibilityRole="button">
          <Icon name="trash" size={18} color={colors.danger} />
          <View style={{ flex: 1 }}>
            <Text style={s.deleteTitle}>완전 삭제</Text>
            <Text style={s.manageSub}>사진과 정보를 지워요. 되돌릴 수 없어요.</Text>
          </View>
        </Pressable>
      </View>

      <RetireSheet
        item={sheetOpen ? item : null}
        busy={busy}
        onClose={() => setSheetOpen(false)}
        onSubmit={(reason, memo) => void onRetire(reason, memo)}
      />
    </Screen>
  );
};

export default ItemDetail;

const useStyles = makeStyles((t) => ({
  banner: {
    backgroundColor: t.colors.accentSoft,
    borderRadius: t.radius.lg,
    padding: 16,
    marginTop: 4,
    marginBottom: 18,
  },
  bannerTitle: { flex: 1, fontSize: 14, fontWeight: '800', color: t.colors.text },
  bannerMemo: { fontSize: 13, color: t.colors.text, marginTop: 8, lineHeight: 19 },
  bannerNote: { fontSize: 12, color: t.colors.textSub, marginTop: 6, lineHeight: 18 },
  bannerButton: { marginTop: 12, alignSelf: 'flex-start', paddingHorizontal: 18 },
  attrs: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 14 },
  mainDot: { width: 14, height: 14, borderRadius: 7 },
  subDot: { width: 9, height: 9, borderRadius: 4.5 },
  dotEdge: { borderWidth: 1, borderColor: t.colors.border },
  attrText: { fontSize: 13, fontWeight: '700', color: t.colors.text, marginLeft: 2 },
  purchase: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
  stats: { ...cardBase(t), marginVertical: 22 },
  stat: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  statNum: { fontSize: 16, fontWeight: '800', color: t.colors.text },
  statLabel: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
  manage: { ...cardBase(t), paddingHorizontal: 16 },
  manageRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 15 },
  manageTitle: { fontSize: 15, fontWeight: '700', color: t.colors.text },
  manageSub: { fontSize: 12, color: t.colors.textSub, marginTop: 3, lineHeight: 17 },
  deleteTitle: { fontSize: 15, fontWeight: '700', color: t.colors.danger },
}));
