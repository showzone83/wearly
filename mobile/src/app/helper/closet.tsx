import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { isApiError } from '@/api/errors';
import type { PairingPermission } from '@/api/types';
import { ItemThumb } from '@/components/ItemThumb';
import { Button, Header, notify, RetiredTag, Screen, TraceLabel } from '@/components/ui';
import type { WardrobeItem } from '@/data/types';
import { api, errorMessage } from '@/lib/api';
import { systemTraceId } from '@/lib/errorText';
import { canEditCloset, OWNER_ACCESS_TEXT, ownerAccessProblem, PERMISSION_LABEL } from '@/lib/pairingRules';
import { itemFromDto } from '@/lib/serverItems';
import { makeStyles, useColors } from '@/theme';

type Tab = 'active' | 'retired';
const COLUMNS = 3;
const GAP = 10;

// 옷장 도우미가 보는 주인 옷장 (doc/26 5단계): 주인 옷 목록(사용 중 / 사용 안 함)과 옷 등록·열기.
// 내 옷장 스토어(useStore)와 섞지 않으려고 이 화면의 상태로만 들고 있다 (저장하지 않는다).
// 권한은 열 때마다 연결 목록에서 새로 확인한다 (주인이 바꿨거나 끊었을 수 있다).
export default function OwnerCloset() {
  const { ownerId, name } = useLocalSearchParams<{ ownerId?: string; name?: string }>();
  const [permission, setPermission] = useState<PairingPermission | null>(null);
  const [ownerName, setOwnerName] = useState(name ?? '');
  const [tab, setTab] = useState<Tab>('active');
  const [items, setItems] = useState<WardrobeItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  const [width, setWidth] = useState(0);
  const s = useStyles();
  const c = useColors();

  /** 연결이 끊겼거나 없으면 도우미 목록으로 돌려보낸다 */
  const lost = useCallback((message: string) => {
    notify('옷장을 열 수 없어요', message);
    router.replace('/helper');
  }, []);

  const load = useCallback(
    async (which: Tab) => {
      if (!ownerId) {
        lost(OWNER_ACCESS_TEXT.gone);
        return;
      }
      setError(null);
      setTrace(undefined);
      try {
        const pairings = await api.pairing.list();
        const mine = pairings.asHelper.find((p) => p.partnerId === ownerId && p.status === 'ACTIVE');
        if (!mine) {
          lost(OWNER_ACCESS_TEXT.gone);
          return;
        }
        setPermission(mine.permission);
        setOwnerName(mine.partnerName);
        const res = await api.owners.list(ownerId, { retired: which === 'retired', sort: 'recent' });
        setItems(res.items.map((dto) => itemFromDto(dto)));
      } catch (e) {
        const problem = isApiError(e) ? ownerAccessProblem(e.status, e.code) : null;
        if (problem) {
          lost(OWNER_ACCESS_TEXT[problem]);
          return;
        }
        setError(errorMessage(e, '옷장을 불러오지 못했어요.'));
        setTrace(systemTraceId(e));
      }
    },
    [ownerId, lost],
  );

  // 열 때마다(등록·수정에서 돌아올 때 포함) 새로 받는다
  useFocusEffect(
    useCallback(() => {
      void load(tab);
    }, [load, tab]),
  );

  const switchTab = (next: Tab) => {
    if (next === tab) return;
    setItems(null);
    setTab(next);
  };

  const size = width > 0 ? Math.floor((width - GAP * (COLUMNS - 1)) / COLUMNS) : 0;
  const canEdit = canEditCloset(permission);

  return (
    <Screen
      header={<Header title={ownerName ? `${ownerName}님의 옷장` : '옷장'} />}
      footer={
        canEdit && ownerId ? (
          <Button
            title="옷 등록하기"
            onPress={() => router.push(`/helper/new?ownerId=${encodeURIComponent(ownerId)}&name=${encodeURIComponent(ownerName)}`)}
          />
        ) : undefined
      }
    >
      <View style={s.banner}>
        <Text style={s.bannerText}>
          옷장 도우미로 보는 중 · {permission ? PERMISSION_LABEL[permission] : '확인하는 중'}
          {permission === 'VIEW' ? ' (등록·수정은 주인이 권한을 줘야 해요)' : ''}
        </Text>
      </View>

      <View style={s.tabs}>
        {(
          [
            ['active', '사용 중'],
            ['retired', '사용 안 함'],
          ] as const
        ).map(([t, label]) => (
          <Pressable
            key={t}
            onPress={() => switchTab(t)}
            accessibilityRole="tab"
            accessibilityState={{ selected: tab === t }}
            style={[s.tab, tab === t && s.tabOn]}
          >
            <Text style={[s.tabText, tab === t && { color: c.onPrimary }]}>{label}</Text>
          </Pressable>
        ))}
      </View>

      {error ? (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
          <Button title="다시 시도" variant="outline" small onPress={() => void load(tab)} style={{ marginTop: 8 }} />
        </View>
      ) : !items ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} />
      ) : items.length === 0 ? (
        <Text style={s.empty}>
          {tab === 'active' ? '아직 옷이 없어요.' : '사용 안 함으로 옮긴 옷이 없어요.'}
          {tab === 'active' && canEdit ? ' 아래 버튼으로 옷을 등록해 보세요.' : ''}
        </Text>
      ) : (
        <View style={s.grid} onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
          {size > 0 &&
            items.map((item) => (
              <Pressable
                key={item.id}
                onPress={() =>
                  router.push(
                    `/helper/item?ownerId=${encodeURIComponent(ownerId ?? '')}&id=${encodeURIComponent(item.id)}`,
                  )
                }
                accessibilityRole="button"
                accessibilityLabel={item.name}
                style={{ width: size }}
              >
                <ItemThumb item={item} size={size} dim={!!item.retiredAt} />
                <Text style={s.name} numberOfLines={1}>
                  {item.name} {item.retiredAt ? <RetiredTag /> : null}
                </Text>
              </Pressable>
            ))}
        </View>
      )}
      {/* 목록이 아직 없을 때도 폭을 재도록 */}
      {(!items || items.length === 0) && <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)} />}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  banner: { backgroundColor: t.colors.primarySoft, borderRadius: t.radius.md, padding: 12, marginBottom: 14 },
  bannerText: { fontSize: 13, color: t.colors.text, fontWeight: '700', lineHeight: 19 },
  tabs: { flexDirection: 'row', backgroundColor: t.colors.surfaceAlt, borderRadius: t.radius.pill, padding: 4, marginBottom: 16 },
  tab: { flex: 1, alignItems: 'center', paddingVertical: 8, borderRadius: t.radius.pill },
  tabOn: { backgroundColor: t.colors.primary },
  tabText: { fontSize: 14, color: t.colors.textSub, fontWeight: '600' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: GAP },
  name: { fontSize: 12, color: t.colors.text, marginTop: 6, fontWeight: '600' },
  empty: { color: t.colors.textMuted, fontSize: 13, lineHeight: 19 },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
