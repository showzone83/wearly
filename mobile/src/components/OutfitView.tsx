import { useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { SLOTS } from '@/data/constants';
import type { Outfit, Slot, WardrobeItem } from '@/data/types';
import { categoryLabel, colorLabel, isRecommendable, seasonLabel, slotLabel, statusLabel } from '@/lib/codes';
import { alternativesFor, evaluate, slotOf, type EngineInput } from '@/lib/recommend';
import { isRetired } from '@/lib/retire';
import { useStore } from '@/store/useStore';
import { makeStyles, space, useColors } from '@/theme';
import { Icon } from './Icon';
import { ItemThumb } from './ItemThumb';
import { RetiredTag } from './ui';

const MAX_WIDTH = 480;

/**
 * 목업 p.42의 코디 보드: 상의·하의 크게, 아우터 크게, 신발·가방 작게.
 * 원피스 코디는 왼쪽에 원피스를 길게, 오른쪽에 아우터와 신발·가방을 둔다.
 */
export function OutfitBoard({ outfit, compact, inset = 0 }: { outfit: Outfit; compact?: boolean; inset?: number }) {
  const styles = useStyles();
  const items = useStore((s) => s.items);
  const { width } = useWindowDimensions();
  const byId = useMemo(() => new Map(items.map((i) => [i.id, i])), [items]);
  const get = (slot: Slot) => (outfit.items[slot] ? byId.get(outfit.items[slot]!) : undefined);
  const inner = Math.min(width, MAX_WIDTH) - space.gutter * 2 - inset * 2;
  const gap = 10;
  const big = (inner - gap) / 2;
  const small = (big - gap) / 2;
  const scale = compact ? 0.8 : 1;

  const outer = get('OUTER');
  const outerTile = outer ? (
    <ItemThumb item={outer} size={big} height={big * scale} tag="OUTER" />
  ) : (
    <View style={[styles.noOuter, { width: big, height: big * scale }]}>
      <Text style={styles.noOuterText}>아우터 없이{'\n'}가볍게</Text>
    </View>
  );
  const smalls = (
    <View style={{ gap, flexDirection: 'row', alignItems: 'flex-start' }}>
      <ItemThumb item={get('SHOES')} size={small} />
      <ItemThumb item={get('BAG')} size={small} />
    </View>
  );

  if (outfit.items.DRESS && !outfit.items.TOP && !outfit.items.BOTTOM) {
    return (
      <View style={{ flexDirection: 'row', gap }}>
        {/* 오른쪽 아우터 + 신발·가방 줄과 아래 끝을 맞춘다 */}
        <ItemThumb item={get('DRESS')} size={big} height={big * scale + gap + small} tag="DRESS" />
        <View style={{ gap }}>
          {outerTile}
          {smalls}
        </View>
      </View>
    );
  }

  return (
    <View style={{ gap }}>
      <View style={{ flexDirection: 'row', gap }}>
        <ItemThumb item={get('TOP')} size={big} height={big * scale} tag="TOP" />
        <ItemThumb item={get('BOTTOM')} size={big} height={big * scale} tag="BOTTOM" />
      </View>
      <View style={{ flexDirection: 'row', gap }}>
        {outerTile}
        {smalls}
      </View>
    </View>
  );
}

export function ReasonBox({ reasons, title = '추천 이유' }: { reasons: string[]; title?: string }) {
  const styles = useStyles();
  if (reasons.length === 0) return null;
  return (
    <View style={styles.reason}>
      <Text style={styles.reasonText}>
        <Text style={styles.reasonHead}>{title} </Text>
        {reasons.join('. ')}.
      </Text>
    </View>
  );
}

interface RowsProps {
  outfit: Outfit;
  input: EngineInput;
  onChange: (next: Outfit) => void;
  pinned?: Slot[];
  onTogglePin?: (slot: Slot) => void;
}

/** 아이템 목록 + 한 벌만 교체하는 시트 (목업 p.13 아이템 교체 시트) */
export function OutfitItemRows({ outfit, input, onChange, pinned = [], onTogglePin }: RowsProps) {
  const styles = useStyles();
  const colors = useColors();
  const items = useStore((s) => s.items);
  const bumpAffinity = useStore((s) => s.bumpAffinity);
  const byId = useMemo(() => new Map(items.map((i) => [i.id, i])), [items]);
  const [slot, setSlot] = useState<Slot | null>(null);

  const rows = SLOTS.filter((k) => outfit.items[k] || k === 'OUTER');
  return (
    <View>
      {rows.map((k) => {
        const it = outfit.items[k] ? byId.get(outfit.items[k]!) : undefined;
        const isPinned = pinned.includes(k);
        return (
          <View key={k} style={styles.row}>
            <ItemThumb item={it} size={56} />
            <View style={{ flex: 1 }}>
              <Text style={styles.rowTitle}>
                {it ? it.name : '아우터 없음'}
                {it && isRetired(it) && <RetiredTag />}
              </Text>
              <Text style={styles.rowSub}>{it ? itemSubtitle(it) : '날씨에 따라 추가할 수 있어요'}</Text>
            </View>
            {onTogglePin && it && (
              <Pressable onPress={() => onTogglePin(k)} hitSlop={8} accessibilityLabel={isPinned ? '고정 해제' : '고정'} style={styles.pin}>
                <Icon name="pin" size={18} color={isPinned ? colors.primary : colors.textMuted} filled={isPinned} />
              </Pressable>
            )}
            <Pressable onPress={() => setSlot(k)} style={styles.swapBtn} accessibilityRole="button">
              <Text style={styles.swapText}>{it ? '교체' : '추가'}</Text>
            </Pressable>
          </View>
        );
      })}
      <SwapSheet
        slot={slot}
        outfit={outfit}
        input={input}
        onClose={() => setSlot(null)}
        onPick={(next, fromId) => {
          if (fromId) bumpAffinity([fromId], -0.3); // 교체 행동도 다음 추천에 반영
          onChange(next);
          setSlot(null);
        }}
      />
    </View>
  );
}

export function itemSubtitle(it: WardrobeItem): string {
  return `${categoryLabel(it.category)} · ${colorLabel(it.color)} · ${it.seasons.map(seasonLabel).join('/')}`;
}

function SwapSheet({
  slot,
  outfit,
  input,
  onClose,
  onPick,
}: {
  slot: Slot | null;
  outfit: Outfit;
  input: EngineInput;
  onClose: () => void;
  onPick: (next: Outfit, fromId?: string) => void;
}) {
  const styles = useStyles();
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const options = useMemo(() => (slot ? alternativesFor(slot, outfit.items, input) : []), [slot, outfit, input]);
  // 사용하지 않는 옷(doc/13)은 교체 후보에도, 세탁·수선 목록에도 보이지 않는다
  const laundry = slot
    ? input.items.filter((i) => slotOf(i.category) === slot && !isRetired(i) && !isRecommendable(i.status))
    : [];

  return (
    <Modal visible={!!slot} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} />
      <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <View style={styles.grip} />
        <View style={styles.sheetHead}>
          <Text style={styles.sheetTitle}>{slot ? `${slotLabel(slot)} 교체` : ''}</Text>
          <Pressable onPress={onClose} hitSlop={10} accessibilityLabel="닫기">
            <Icon name="close" />
          </Pressable>
        </View>
        <Text style={styles.sheetSub}>내 옷장 후보 중 한 벌만 바꿔요 · 세탁·수선 중인 옷은 제외</Text>
        <ScrollView style={{ maxHeight: 420 }}>
          {options.length === 0 && <Text style={styles.emptyText}>바꿀 수 있는 옷이 없어요. 옷장에 더 등록해 보세요.</Text>}
          {options.map(({ item, outfit: next }) => {
            const delta = next.score - outfit.score;
            return (
              <Pressable
                key={item?.id ?? 'none'}
                style={styles.optRow}
                onPress={() => onPick(evaluate(next.items, input), slot ? outfit.items[slot] : undefined)}
              >
                <ItemThumb item={item ?? undefined} size={56} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.rowTitle}>{item ? item.name : `${slotLabel(slot!)} 빼기`}</Text>
                  <Text style={styles.rowSub}>{item ? `${colorLabel(item.color)} · 착용 ${item.wornCount}회` : '더 가볍게 입어요'}</Text>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <Text style={styles.score}>{next.score}점</Text>
                  <Text style={[styles.delta, { color: delta >= 0 ? colors.primary : colors.danger }]}>
                    {delta >= 0 ? `+${delta}` : delta}
                  </Text>
                </View>
              </Pressable>
            );
          })}
          {laundry.map((it) => (
            <View key={it.id} style={[styles.optRow, { opacity: 0.45 }]}>
              <ItemThumb item={it} size={56} />
              <View style={{ flex: 1 }}>
                <Text style={styles.rowTitle}>{it.name}</Text>
                <Text style={styles.rowSub}>{statusLabel(it.status)} · 추천에서 제외</Text>
              </View>
            </View>
          ))}
        </ScrollView>
      </View>
    </Modal>
  );
}

const useStyles = makeStyles((t) => ({
  noOuter: {
    backgroundColor: t.colors.surface,
    borderWidth: 1,
    borderColor: t.colors.border,
    borderStyle: 'dashed',
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  noOuterText: { color: t.colors.textMuted, fontSize: 13, textAlign: 'center', lineHeight: 19 },
  reason: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, marginTop: 14 },
  reasonHead: { color: t.colors.primary, fontWeight: '800' },
  reasonText: { color: t.colors.text, fontSize: 14, lineHeight: 21 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  rowTitle: { fontSize: 15, fontWeight: '700', color: t.colors.text },
  rowSub: { fontSize: 12, color: t.colors.textSub, marginTop: 3 },
  pin: { padding: 4 },
  swapBtn: {
    borderWidth: 1,
    borderColor: t.colors.primary,
    borderRadius: t.radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 6,
  },
  swapText: { color: t.colors.primary, fontWeight: '700', fontSize: 13 },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: {
    backgroundColor: t.colors.bg,
    borderTopLeftRadius: t.radius.xl,
    borderTopRightRadius: t.radius.xl,
    paddingHorizontal: space.gutter,
    paddingTop: 8,
  },
  grip: {
    alignSelf: 'center',
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: t.colors.border,
    marginBottom: 10,
  },
  sheetHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sheetTitle: { fontSize: 18, fontWeight: '800', color: t.colors.text },
  sheetSub: { fontSize: 12, color: t.colors.textSub, marginTop: 4, marginBottom: 8 },
  optRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  score: { fontSize: 15, fontWeight: '800', color: t.colors.text },
  delta: { fontSize: 12, fontWeight: '700', marginTop: 2 },
  emptyText: { color: t.colors.textSub, paddingVertical: 24, textAlign: 'center' },
}));
