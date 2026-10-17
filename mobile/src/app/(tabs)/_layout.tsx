import { Pressable, Text, View } from 'react-native';

import { Icon, type IconName } from '@/components/Icon';
import { router } from 'expo-router';
import { Tabs, type BottomTabBarProps } from 'expo-router/js-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { makeStyles, softShadow, useColors } from '@/theme';

/** 탭 라우트. 캘린더는 탭 바에 보이지 않고 더보기 > 코디 기록, 홈 예정 알림 등으로 들어간다 */
const ROUTES = ['home', 'closet', 'coordi', 'calendar', 'my'] as const;
type TRoute = (typeof ROUTES)[number];

interface ITabSlot {
  route: TRoute;
  label: string;
  icon: IconName;
}

/** 보드의 하단 탭 5칸: 홈 · 내 옷장 · (가운데 + 옷 등록) · 코디추천 · 더보기 */
const LEFT: ITabSlot[] = [
  { route: 'home', label: '홈', icon: 'home' },
  { route: 'closet', label: '내 옷장', icon: 'hanger' },
];
const RIGHT: ITabSlot[] = [
  { route: 'coordi', label: '코디추천', icon: 'magic' },
  { route: 'my', label: '더보기', icon: 'grid' },
];

/** 탭 바에 없는 라우트가 열려 있을 때 밝게 보일 탭 */
const OWNER: Partial<Record<TRoute, TRoute>> = { calendar: 'my' };

const TabBar = ({ state, navigation }: BottomTabBarProps) => {
  const s = useStyles();
  const c = useColors();
  const insets = useSafeAreaInsets();

  const currentName = state.routes[state.index]?.name as TRoute;
  const active = OWNER[currentName] ?? currentName;

  const renderTab = (tab: ITabSlot) => {
    const route = state.routes.find((r) => r.name === tab.route);
    if (!route) return null;
    const focused = active === tab.route;
    const tint = focused ? c.primary : c.textMuted;
    return (
      <Pressable
        key={tab.route}
        accessibilityRole="tab"
        accessibilityState={{ selected: focused }}
        accessibilityLabel={tab.label}
        onPress={() => {
          const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
          if (currentName !== tab.route && !event.defaultPrevented) navigation.navigate(route.name);
        }}
        style={s.item}
      >
        <Icon name={tab.icon} color={tint} filled={focused && tab.icon === 'home'} />
        <Text style={[s.label, { color: focused ? c.primary : c.textMuted, fontWeight: focused ? '800' : '500' }]}>
          {tab.label}
        </Text>
      </Pressable>
    );
  };

  return (
    <View style={[s.bar, { paddingBottom: Math.max(insets.bottom, 10) }]}>
      {LEFT.map(renderTab)}
      <View style={s.item}>
        <Pressable
          onPress={() => router.push('/item/new')}
          accessibilityRole="button"
          accessibilityLabel="옷 등록"
          style={({ pressed }) => [s.center, { backgroundColor: pressed ? c.primaryPressed : c.primary }]}
        >
          <Icon name="plus" color={c.onPrimary} size={26} strokeWidth={2} />
        </Pressable>
      </View>
      {RIGHT.map(renderTab)}
    </View>
  );
};

export default function TabsLayout() {
  return (
    <Tabs tabBar={(props) => <TabBar {...props} />} screenOptions={{ headerShown: false }}>
      {ROUTES.map((name) => (
        <Tabs.Screen key={name} name={name} />
      ))}
    </Tabs>
  );
}

const useStyles = makeStyles((t) => ({
  bar: {
    flexDirection: 'row',
    backgroundColor: t.colors.tabBar,
    borderTopLeftRadius: t.radius.lg,
    borderTopRightRadius: t.radius.lg,
    paddingTop: 10,
    ...softShadow(t, 1.4),
    shadowOffset: { width: 0, height: -2 },
  },
  item: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 4 },
  center: {
    width: 50,
    height: 50,
    borderRadius: 25,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 6,
    ...softShadow(t, 2),
  },
  label: { fontSize: 11 },
}));
