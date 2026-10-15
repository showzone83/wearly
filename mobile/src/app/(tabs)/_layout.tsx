import { Tabs, type BottomTabBarProps } from 'expo-router/js-tabs';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/Icon';
import { colors } from '@/theme';

const TABS: { name: string; label: string; icon: IconName }[] = [
  { name: 'home', label: '홈', icon: 'home' },
  { name: 'closet', label: '내 옷장', icon: 'hanger' },
  { name: 'coordi', label: 'AI 코디', icon: 'sparkle' },
  { name: 'calendar', label: '캘린더', icon: 'calendar' },
  { name: 'my', label: '마이', icon: 'user' },
];

/** 목업의 하단 탭: 가운데 AI 코디는 원형 버튼 */
function TabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[s.bar, { paddingBottom: Math.max(insets.bottom, 10) }]}>
      {state.routes.map((route, index) => {
        const tab = TABS.find((t) => t.name === route.name);
        if (!tab) return null;
        const focused = state.index === index;
        const center = tab.name === 'coordi';
        const tint = focused ? colors.green : colors.faint;
        return (
          <Pressable
            key={route.key}
            accessibilityRole="tab"
            accessibilityState={{ selected: focused }}
            accessibilityLabel={tab.label}
            onPress={() => {
              const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
              if (!focused && !event.defaultPrevented) navigation.navigate(route.name);
            }}
            style={s.item}
          >
            {center ? (
              <View style={[s.center, { backgroundColor: focused ? colors.green : colors.ink }]}>
                <Icon name="sparkle" color={colors.beige} size={22} />
              </View>
            ) : (
              <Icon name={tab.icon} color={tint} />
            )}
            <Text style={[s.label, { color: focused ? colors.ink : colors.faint, fontWeight: focused ? '800' : '500' }]}>{tab.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export default function TabsLayout() {
  return (
    <Tabs tabBar={(props) => <TabBar {...props} />} screenOptions={{ headerShown: false }}>
      {TABS.map((t) => (
        <Tabs.Screen key={t.name} name={t.name} options={{ title: t.label }} />
      ))}
    </Tabs>
  );
}

const s = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.line,
    paddingTop: 8,
  },
  item: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 4 },
  center: { width: 46, height: 46, borderRadius: 23, alignItems: 'center', justifyContent: 'center', marginTop: -18 },
  label: { fontSize: 11 },
});
