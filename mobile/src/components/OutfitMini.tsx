import { Pressable, Text, View } from 'react-native';

import type { OutfitItems, Slot, WardrobeItem } from '@/data/types';
import { useStore } from '@/store/useStore';
import { makeStyles, useColors } from '@/theme';
import { Icon } from './Icon';
import { ItemThumb } from './ItemThumb';

const ORDER: Slot[] = ['TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES'];
const COLLAGE_ORDER: Slot[] = ['OUTER', 'TOP', 'DRESS', 'BOTTOM', 'SHOES', 'BAG'];

const pickItems = (all: WardrobeItem[], items: OutfitItems, order: Slot[]) =>
  order.map((k) => all.find((i) => i.id === items[k])).filter((i): i is WardrobeItem => !!i);

/** 캘린더·기록 카드용 작은 코디 미리보기 */
export function OutfitMini({ items, size = 44, max = 3 }: { items: OutfitItems; size?: number; max?: number }) {
  const all = useStore((s) => s.items);
  const list = pickItems(all, items, ORDER).slice(0, max);
  return (
    <View style={{ flexDirection: 'row', gap: 4 }}>
      {list.map((it) => (
        <ItemThumb key={it.id} item={it} size={size} />
      ))}
    </View>
  );
}

interface IOutfitCardProps {
  items: OutfitItems;
  width: number;
  /** 카드 아래 이름 */
  title?: string;
  sub?: string;
  selected?: boolean;
  /** 오른쪽 위 하트 (저장한 코디면 채움) */
  saved?: boolean;
  onPress?: () => void;
}

/** 보드의 "이런 코디는 어때요?" 가로 카드: 코디 아이템 4벌을 2×2로 모은 둥근 카드 */
export const OutfitCard = ({ items, width, title, sub, selected, saved, onPress }: IOutfitCardProps) => {
  const all = useStore((s) => s.items);
  const s = useStyles();
  const c = useColors();

  const list = pickItems(all, items, COLLAGE_ORDER).slice(0, 4);
  const pad = 8;
  const gap = 6;
  const cell = Math.floor((width - pad * 2 - gap - 3) / 2);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={[s.card, { width }, selected && s.cardOn]}
    >
      <View style={[s.grid, { padding: pad, gap }]}>
        {list.map((it) => (
          <ItemThumb key={it.id} item={it} size={cell} radius={10} />
        ))}
      </View>
      <View style={s.heart}>
        <Icon name="heart" size={13} color={c.primary} filled={saved} strokeWidth={1.8} />
      </View>
      {(title || sub) && (
        <View style={s.caption}>
          {title ? (
            <Text style={s.title} numberOfLines={1}>
              {title}
            </Text>
          ) : null}
          {sub ? (
            <Text style={s.sub} numberOfLines={1}>
              {sub}
            </Text>
          ) : null}
        </View>
      )}
    </Pressable>
  );
};

const useStyles = makeStyles((t) => ({
  card: {
    backgroundColor: t.colors.surface,
    borderRadius: t.radius.lg,
    borderWidth: 1.5,
    borderColor: t.colors.border,
    overflow: 'hidden',
  },
  cardOn: { borderColor: t.colors.primary },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  heart: {
    position: 'absolute',
    top: 12,
    right: 12,
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: t.colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  caption: { paddingHorizontal: 12, paddingBottom: 12, paddingTop: 2 },
  title: { fontSize: 14, fontWeight: '800', color: t.colors.text },
  sub: { fontSize: 12, color: t.colors.textSub, marginTop: 2 },
}));
