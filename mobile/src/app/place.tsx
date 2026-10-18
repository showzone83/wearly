import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';

import { isApiError } from '@/api/errors';
import type { PlaceSearchItem } from '@/api/types';
import { Icon } from '@/components/Icon';
import { Header, Notice, Screen } from '@/components/ui';
import type { Place } from '@/data/types';
import { useCodesVersion } from '@/store/useCodes';
import { usePlaceStore } from '@/store/usePlaceStore';
import { useStore } from '@/store/useStore';
import { api, errorMessage } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { isUnsupported, placeKey } from '@/lib/planWeather';
import { regionList } from '@/lib/weatherSync';
import { cardBase, inputBase, makeStyles, useColors } from '@/theme';

/** 검색어 입력 뒤 기다리는 시간 */
const DEBOUNCE_MS = 300;
/** 서버 검색어 길이 (doc/19) */
const QUERY_MAX = 50;

type TSearch =
  | { status: 'idle' }
  | { status: 'loading'; q: string }
  | { status: 'done'; q: string; items: PlaceSearchItem[] }
  | { status: 'error'; q: string; message: string };

const placeFromItem = (it: PlaceSearchItem): Place => ({
  regionCode: it.regionCode || undefined,
  label: it.label,
  lat: it.lat,
  lon: it.lon,
});

const itemSub = (it: PlaceSearchItem): string => [it.region, it.country].filter(Boolean).join(' · ');

// 예정 장소 고르기 (doc/19): 예정일 코디(PLAN 모드)에서 그날 갈 곳을 고른다. 내 날씨 지역(app/region.tsx)은 바꾸지 않는다.
// 시·도 목록(코드 REGION)과, 로그인했으면 서버 장소 검색(해외 등). 고르면 최근 장소에 넣고 돌아간다.
const PlacePicker = () => {
  const seq = useRef(0);

  const [query, setQuery] = useState('');
  const [search, setSearch] = useState<TSearch>({ status: 'idle' });

  const params = useLocalSearchParams<{ current?: string }>();
  const isAccount = useStore((st) => st.authMode === 'account');
  const s = useStyles();
  const c = useColors();
  useCodesVersion(); // 코드가 갱신되면 지역 목록도 다시 그린다

  const canSearch = !MOCK_MODE && isAccount;
  const regions = regionList();
  const trimmed = query.trim().slice(0, QUERY_MAX);

  useEffect(() => {
    if (!canSearch || !trimmed) {
      seq.current++;
      setSearch({ status: 'idle' });
      return;
    }
    const run = ++seq.current;
    setSearch({ status: 'loading', q: trimmed });
    const timer = setTimeout(() => {
      api.weather
        .places(trimmed)
        .then((res) => {
          if (run === seq.current) setSearch({ status: 'done', q: trimmed, items: res.items.slice(0, 10) });
        })
        .catch((e: unknown) => {
          if (run !== seq.current) return;
          const message =
            isApiError(e) && isUnsupported(e, 'search')
              ? '서버가 아직 장소 검색을 지원하지 않아요. 아래 시·도 목록에서 골라 주세요.'
              : errorMessage(e, '장소를 찾지 못했어요. 잠시 후 다시 시도해 주세요.');
          setSearch({ status: 'error', q: trimmed, message });
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [trimmed, canSearch]);

  const pick = (place: Place) => {
    usePlaceStore.getState().pick(place);
    router.back();
  };

  return (
    <Screen header={<Header title="예정 장소" />}>
      <Text style={s.desc}>
        이 예정일에 갈 곳을 골라요. 그곳의 그날 날씨로 코디를 추천해요.{'\n'}홈의 ‘날씨 지역’은 바뀌지 않아요.
      </Text>

      {canSearch ? (
        <View>
          <View style={s.searchBox}>
            <Icon name="search" size={18} color={c.textMuted} />
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="도시·장소 검색 (예: 도쿄, 제주)"
              placeholderTextColor={c.textMuted}
              maxLength={QUERY_MAX}
              autoCorrect={false}
              returnKeyType="search"
              style={s.searchInput}
            />
            {query ? (
              <Pressable onPress={() => setQuery('')} hitSlop={8} accessibilityLabel="검색어 지우기">
                <Icon name="close" size={16} color={c.textMuted} />
              </Pressable>
            ) : null}
          </View>
          {search.status === 'loading' && <ActivityIndicator color={c.primary} style={{ marginTop: 14 }} />}
          {search.status === 'error' && <Text style={s.note}>{search.message}</Text>}
          {search.status === 'done' && search.items.length === 0 && <Text style={s.note}>‘{search.q}’에 맞는 장소가 없어요.</Text>}
          {search.status === 'done' && search.items.length > 0 && (
            <View style={s.card}>
              {search.items.map((it, i) => {
                const place = placeFromItem(it);
                const on = params.current === placeKey(place);
                return (
                  <Pressable
                    key={`${it.lat},${it.lon},${i}`}
                    onPress={() => pick(place)}
                    style={[s.row, i === search.items.length - 1 && { borderBottomWidth: 0 }]}
                    accessibilityRole="button"
                  >
                    <View style={{ flex: 1 }}>
                      <Text style={[s.label, on && { color: c.primary }]}>{it.label}</Text>
                      {itemSub(it) ? <Text style={s.sub}>{itemSub(it)}</Text> : null}
                    </View>
                    {on ? <Icon name="check" size={18} color={c.primary} /> : <Icon name="pin" size={16} color={c.textMuted} />}
                  </Pressable>
                );
              })}
            </View>
          )}
        </View>
      ) : (
        <Notice tone="plain" icon="pin">
          <Text style={s.noticeText}>해외 도시 등 다른 장소 검색은 로그인하면 쓸 수 있어요. 지금은 시·도 목록에서 골라 주세요.</Text>
        </Notice>
      )}

      <Text style={s.section}>시·도</Text>
      {regions.length === 0 ? (
        <Text style={s.empty}>지역 목록을 아직 받지 못했어요. 서버에 연결되면 다시 열어 주세요.</Text>
      ) : (
        <View style={[s.card, { marginTop: 0 }]}>
          {regions.map((r, i) => {
            const on = params.current === r.code;
            return (
              <Pressable
                key={r.code}
                onPress={() => pick({ regionCode: r.code, label: r.label, lat: r.lat, lon: r.lon })}
                style={[s.row, i === regions.length - 1 && { borderBottomWidth: 0 }]}
                accessibilityRole="button"
                accessibilityState={{ selected: on }}
              >
                <Text style={[s.label, on && { color: c.primary }]}>{r.label}</Text>
                {on ? <Icon name="check" size={18} color={c.primary} /> : null}
              </Pressable>
            );
          })}
        </View>
      )}
    </Screen>
  );
};

export default PlacePicker;

const useStyles = makeStyles((t) => ({
  desc: { fontSize: 13, color: t.colors.textSub, lineHeight: 19, marginTop: 8, marginBottom: 14 },
  searchBox: { ...inputBase(t), flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 14 },
  searchInput: { flex: 1, height: '100%', fontSize: 15, color: t.colors.text },
  note: { fontSize: 13, color: t.colors.textSub, marginTop: 12, lineHeight: 19 },
  noticeText: { fontSize: 13, color: t.colors.textSub, lineHeight: 19 },
  section: { fontSize: 14, fontWeight: '800', color: t.colors.text, marginTop: 24, marginBottom: 10 },
  empty: { fontSize: 13, color: t.colors.textMuted, marginTop: 8, textAlign: 'center' },
  card: { ...cardBase(t), marginTop: 12, paddingVertical: 4 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  label: { fontSize: 15, fontWeight: '600', color: t.colors.text },
  sub: { fontSize: 12, color: t.colors.textSub, marginTop: 2 },
}));
