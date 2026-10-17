import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';

import { Chip } from '@/components/Chip';
import { Icon } from '@/components/Icon';
import { ItemThumb } from '@/components/ItemThumb';
import { Row, Screen } from '@/components/ui';
import type { Category, Season } from '@/data/types';
import { activeCodes, categoryLabel, colorLabel, isRecommendable, seasonLabel, statusLabel, subTypeLabel, withCurrent } from '@/lib/codes';
import { useCodesVersion } from '@/store/useCodes';
import { useStore } from '@/store/useStore';
import { colors, space } from '@/theme';

type SortKey = 'recent' | 'worn' | 'sleeping';
const SORT_LABEL: Record<SortKey, string> = { recent: '최근 등록순', worn: '많이 입은 순', sleeping: '잠자는 옷 순' };

export default function Closet() {
  const items = useStore((s) => s.items);
  const codesVersion = useCodesVersion();
  const [q, setQ] = useState('');
  const [cat, setCat] = useState<Category | 'ALL'>('ALL');
  const [season, setSeason] = useState<Season | null>(null);
  const [laundryOnly, setLaundryOnly] = useState(false);
  const [sort, setSort] = useState<SortKey>('recent');
  const { width } = useWindowDimensions();
  const cols = 3;
  const gap = 10;
  const tile = (Math.min(width, 480) - space.gutter * 2 - gap * (cols - 1)) / cols;

  const list = useMemo(() => {
    const term = q.trim();
    // 이름·브랜드와 함께 종류·세부 종류·색 이름으로도 찾는다
    const matches = (i: (typeof items)[number]) =>
      [i.name, i.brand ?? '', categoryLabel(i.category), subTypeLabel(i.subType), colorLabel(i.color)].some((t) => t.includes(term));
    const filtered = items.filter(
      (i) =>
        (cat === 'ALL' || i.category === cat) &&
        (!season || i.seasons.includes(season)) &&
        (!laundryOnly || !isRecommendable(i.status)) &&
        (!term || matches(i)),
    );
    const sorted = [...filtered];
    if (sort === 'worn') sorted.sort((a, b) => b.wornCount - a.wornCount);
    if (sort === 'sleeping') sorted.sort((a, b) => (a.lastWornAt ?? '0').localeCompare(b.lastWornAt ?? '0'));
    return sorted;
  }, [items, q, cat, season, laundryOnly, sort, codesVersion]); // codesVersion: 코드가 갱신되면 라벨 검색을 다시 한다

  const resting = items.filter((i) => !isRecommendable(i.status)).length;
  // 종류 필터: 활성 종류 + 옷장에 있는 비활성(또는 모르는) 종류
  const categories = withCurrent(activeCodes('CATEGORY'), [...new Set(items.map((i) => i.category))]);
  const nextSort: Record<SortKey, SortKey> = { recent: 'worn', worn: 'sleeping', sleeping: 'recent' };

  return (
    <View style={{ flex: 1 }}>
      <Screen
        header={
          <Row style={s.top}>
            <Text style={s.title}>내 옷장</Text>
            <Pressable onPress={() => router.push('/analysis')} hitSlop={8}>
              <Text style={s.link}>스타일 분석 ›</Text>
            </Pressable>
          </Row>
        }
      >
        <Row style={{ justifyContent: 'space-between' }}>
          <Text style={s.count}>
            총 <Text style={{ fontWeight: '800', color: colors.ink }}>{items.length}벌</Text> · 세탁 · 수선 중 {resting}벌
          </Text>
          <Pressable onPress={() => setSort(nextSort[sort])} hitSlop={8}>
            <Text style={s.sort}>{SORT_LABEL[sort]} ▾</Text>
          </Pressable>
        </Row>
        <View style={s.search}>
          <Icon name="search" size={18} color={colors.faint} />
          <TextInput value={q} onChangeText={setQ} placeholder="종류, 색상, 브랜드 검색" placeholderTextColor={colors.faint} style={s.searchInput} />
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.chips}>
          <Chip label="전체" selected={cat === 'ALL'} onPress={() => setCat('ALL')} small />
          {categories.map((c) => (
            <Chip key={c} label={categoryLabel(c)} selected={cat === c} onPress={() => setCat(c)} small />
          ))}
        </ScrollView>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[s.chips, { marginTop: 0 }]}>
          {activeCodes('SEASON').map((x) => (
            <Chip key={x} label={seasonLabel(x)} selected={season === x} onPress={() => setSeason(season === x ? null : x)} small />
          ))}
          <Chip label="세탁 · 수선 중" selected={laundryOnly} onPress={() => setLaundryOnly(!laundryOnly)} small />
        </ScrollView>

        {list.length === 0 ? (
          <Text style={s.empty}>{items.length === 0 ? '아직 등록한 옷이 없어요.\n오른쪽 아래 + 버튼으로 첫 옷을 등록해 보세요.' : '조건에 맞는 옷이 없어요.'}</Text>
        ) : (
          <View style={[s.grid, { gap }]}>
            {list.map((it) => (
              <Pressable key={it.id} style={{ width: tile }} onPress={() => router.push(`/item/${it.id}`)}>
                <ItemThumb item={it} size={tile} height={tile * 1.15} dim={!isRecommendable(it.status)} />
                {!isRecommendable(it.status) && (
                  <View style={s.status}>
                    <Text style={s.statusText}>{statusLabel(it.status)}</Text>
                  </View>
                )}
                <Text style={s.name} numberOfLines={1}>
                  {it.name}
                </Text>
                <Text style={s.meta}>
                  {colorLabel(it.color)} · {it.wornCount}회
                </Text>
              </Pressable>
            ))}
          </View>
        )}
        <View style={{ height: 70 }} />
      </Screen>
      <Pressable style={s.fab} onPress={() => router.push('/item/new')} accessibilityLabel="옷 등록">
        <Icon name="plus" color="#fff" size={26} />
      </Pressable>
    </View>
  );
}

const s = StyleSheet.create({
  top: { justifyContent: 'space-between', paddingHorizontal: 20, height: 52 },
  title: { fontSize: 18, fontWeight: '800', color: colors.ink, letterSpacing: 2 },
  link: { color: colors.ink, fontWeight: '600' },
  count: { color: colors.sub, fontSize: 13 },
  sort: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  search: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 46, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14, marginTop: 12 },
  searchInput: { flex: 1, fontSize: 15, color: colors.ink, height: '100%' },
  chips: { gap: 8, marginTop: 12, marginBottom: 8, paddingRight: 20 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 10 },
  name: { fontSize: 13, fontWeight: '700', color: colors.ink, marginTop: 6 },
  meta: { fontSize: 12, color: colors.beige, marginTop: 2, marginBottom: 6 },
  status: { position: 'absolute', top: 8, right: 8, backgroundColor: colors.ink, paddingHorizontal: 6, paddingVertical: 2 },
  statusText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  empty: { color: colors.sub, textAlign: 'center', marginTop: 60, lineHeight: 22 },
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.green,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4,
  },
});
