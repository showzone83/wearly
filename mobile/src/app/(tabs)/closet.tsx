import { useMemo, useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View, useWindowDimensions } from 'react-native';

import { Chip } from '@/components/Chip';
import { Icon } from '@/components/Icon';
import { ItemThumb } from '@/components/ItemThumb';
import { Mascot } from '@/components/Mascot';
import { Button, PageTitle, Row, Screen } from '@/components/ui';
import { router } from 'expo-router';

import type { Category, Season, WardrobeItem } from '@/data/types';
import {
  activeCodes,
  categoryLabel,
  colorLabel,
  isRecommendable,
  seasonLabel,
  statusLabel,
  subTypeLabel,
  withCurrent,
} from '@/lib/codes';
import { useCodesVersion } from '@/store/useCodes';
import { useStore } from '@/store/useStore';
import { inputBase, makeStyles, space, useColors } from '@/theme';

type TSortKey = 'recent' | 'worn' | 'sleeping';
const SORT_LABEL: Record<TSortKey, string> = { recent: '최근 등록순', worn: '많이 입은 순', sleeping: '잠자는 옷 순' };
const NEXT_SORT: Record<TSortKey, TSortKey> = { recent: 'worn', worn: 'sleeping', sleeping: 'recent' };
/** 하트(자주 입는 옷)로 표시하는 착용 횟수 기준 */
const FAVORITE_WORN = 5;

export default function Closet() {
  const items = useStore((s) => s.items);
  const codesVersion = useCodesVersion();
  const [q, setQ] = useState('');
  const [cat, setCat] = useState<Category | 'ALL'>('ALL');
  const [season, setSeason] = useState<Season | null>(null);
  const [laundryOnly, setLaundryOnly] = useState(false);
  const [sort, setSort] = useState<TSortKey>('recent');
  /** 검색칸·계절 필터 줄 펼침 (보드의 검색·필터 아이콘) */
  const [searchOpen, setSearchOpen] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const s = useStyles();
  const c = useColors();
  const { width } = useWindowDimensions();

  const cols = 3;
  const gap = 10;
  const tile = (Math.min(width, 480) - space.gutter * 2 - gap * (cols - 1)) / cols;

  const list = useMemo(() => {
    const term = q.trim();
    // 이름·브랜드와 함께 종류·세부 종류·색 이름으로도 찾는다
    const matches = (i: WardrobeItem) =>
      [i.name, i.brand ?? '', categoryLabel(i.category), subTypeLabel(i.subType), colorLabel(i.color)].some((t) =>
        t.includes(term),
      );
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
  const filtering = !!season || laundryOnly;

  return (
    <Screen
      header={
        <PageTitle
          title="내 옷장"
          sub={`총 ${items.length}벌의 옷이 등록되어 있어요${resting ? ` · 세탁·수선 중 ${resting}벌` : ''}`}
          right={
            <>
              <Pressable onPress={() => setSearchOpen(!searchOpen)} hitSlop={8} accessibilityLabel="검색">
                <Icon name="search" color={searchOpen || q ? c.primary : c.text} />
              </Pressable>
              <Pressable onPress={() => setFilterOpen(!filterOpen)} hitSlop={8} accessibilityLabel="필터">
                <Icon name="filter" color={filterOpen || filtering ? c.primary : c.text} />
              </Pressable>
            </>
          }
        />
      }
    >
      {(searchOpen || !!q) && (
        <View style={s.search}>
          <Icon name="search" size={18} color={c.textMuted} />
          <TextInput
            value={q}
            onChangeText={setQ}
            placeholder="종류, 색상, 브랜드 검색"
            placeholderTextColor={c.textMuted}
            style={s.searchInput}
            autoFocus={searchOpen && !q}
          />
          {!!q && (
            <Pressable onPress={() => setQ('')} hitSlop={8} accessibilityLabel="검색어 지우기">
              <Icon name="close" size={16} color={c.textMuted} />
            </Pressable>
          )}
        </View>
      )}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.chips} style={s.chipsWrap}>
        <Chip label="전체" selected={cat === 'ALL'} onPress={() => setCat('ALL')} small />
        {categories.map((x) => (
          <Chip key={x} label={categoryLabel(x)} selected={cat === x} onPress={() => setCat(x)} small />
        ))}
      </ScrollView>
      {(filterOpen || filtering) && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={s.chips}
          style={s.chipsWrap}
        >
          {activeCodes('SEASON').map((x) => (
            <Chip
              key={x}
              label={seasonLabel(x)}
              selected={season === x}
              onPress={() => setSeason(season === x ? null : x)}
              small
            />
          ))}
          <Chip label="세탁 · 수선 중" selected={laundryOnly} onPress={() => setLaundryOnly(!laundryOnly)} small />
        </ScrollView>
      )}

      <Row style={s.toolbar}>
        <Pressable onPress={() => router.push('/analysis')} hitSlop={8} style={s.tool}>
          <Icon name="chart" size={16} color={c.primary} />
          <Text style={s.toolText}>스타일 분석</Text>
        </Pressable>
        <Pressable onPress={() => setSort(NEXT_SORT[sort])} hitSlop={8} style={s.tool} accessibilityLabel="정렬 바꾸기">
          <Icon name="sort" size={16} color={c.textSub} />
          <Text style={[s.toolText, { color: c.textSub }]}>{SORT_LABEL[sort]}</Text>
        </Pressable>
      </Row>

      {list.length === 0 ? (
        items.length === 0 ? (
          <View style={s.empty}>
            <Mascot pose="closet" size={170} sceneWidth={290} />
            <Text style={s.emptyText}>아직 등록한 옷이 없어요.{'\n'}아래 + 버튼으로 첫 옷을 등록해 보세요.</Text>
            <Button title="옷 등록하기" onPress={() => router.push('/item/new')} style={{ alignSelf: 'stretch' }} />
          </View>
        ) : (
          <Text style={s.emptyText}>조건에 맞는 옷이 없어요.</Text>
        )
      ) : (
        <View style={[s.grid, { gap }]}>
          {list.map((it) => (
            <Pressable key={it.id} style={{ width: tile }} onPress={() => router.push(`/item/${it.id}`)}>
              <ItemThumb
                item={it}
                size={tile}
                height={tile * 1.12}
                dim={!isRecommendable(it.status)}
                heart={it.wornCount >= FAVORITE_WORN ? 'on' : 'off'}
              />
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
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  search: {
    ...inputBase(t),
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderRadius: t.radius.pill,
    marginBottom: 4,
  },
  searchInput: { flex: 1, fontSize: 15, color: t.colors.text, height: '100%' },
  chipsWrap: { marginHorizontal: -20, flexGrow: 0 },
  chips: { gap: 8, paddingVertical: 6, paddingHorizontal: 20 },
  toolbar: { justifyContent: 'space-between', marginTop: 8, marginBottom: 12 },
  tool: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  toolText: { fontSize: 13, fontWeight: '700', color: t.colors.primary },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  name: { fontSize: 13, fontWeight: '700', color: t.colors.text, marginTop: 7 },
  meta: { fontSize: 12, color: t.colors.textMuted, marginTop: 2, marginBottom: 8 },
  status: {
    position: 'absolute',
    top: 7,
    left: 7,
    backgroundColor: t.colors.text,
    borderRadius: t.radius.pill,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  statusText: { color: t.colors.surface, fontSize: 10, fontWeight: '700' },
  empty: { alignItems: 'center', gap: 14, marginTop: 30 },
  emptyText: { color: t.colors.textSub, textAlign: 'center', marginTop: 20, lineHeight: 22 },
}));
