import { View } from 'react-native';

import type { OutfitItems, Slot, WardrobeItem } from '@/data/types';
import { useStore } from '@/store/useStore';
import { ItemThumb } from './ItemThumb';

const ORDER: Slot[] = ['TOP', 'BOTTOM', 'OUTER', 'SHOES'];

/** 캘린더·기록 카드용 작은 코디 미리보기 */
export function OutfitMini({ items, size = 44, max = 3 }: { items: OutfitItems; size?: number; max?: number }) {
  const all = useStore((s) => s.items);
  const list = ORDER.map((k) => all.find((i) => i.id === items[k]))
    .filter((i): i is WardrobeItem => !!i)
    .slice(0, max);
  return (
    <View style={{ flexDirection: 'row', gap: 4 }}>
      {list.map((it) => (
        <ItemThumb key={it.id} item={it} size={size} />
      ))}
    </View>
  );
}
