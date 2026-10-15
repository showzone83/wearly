import { router } from 'expo-router';
import type { ReactNode } from 'react';
import {
  Alert,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, fonts, space } from '@/theme';
import { Icon } from './Icon';

export function Serif({ style, children, numberOfLines }: { style?: StyleProp<TextStyle>; children: ReactNode; numberOfLines?: number }) {
  return (
    <Text style={[s.serif, style]} numberOfLines={numberOfLines}>
      {children}
    </Text>
  );
}

interface ScreenProps {
  children: ReactNode;
  scroll?: boolean;
  /** 화면 하단 고정 영역 (버튼 등) */
  footer?: ReactNode;
  contentStyle?: StyleProp<ViewStyle>;
  /** 탭 화면은 상단 안전 영역을 직접 처리 */
  header?: ReactNode;
}

export function Screen({ children, scroll = true, footer, contentStyle, header }: ScreenProps) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[s.screen, { paddingTop: insets.top }]}>
      {header}
      {scroll ? (
        <ScrollView
          contentContainerStyle={[s.content, contentStyle]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          {children}
        </ScrollView>
      ) : (
        <View style={[s.content, { flex: 1 }, contentStyle]}>{children}</View>
      )}
      {footer && <View style={[s.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>{footer}</View>}
    </View>
  );
}

export function Header({ title, right, onBack }: { title?: string; right?: ReactNode; onBack?: () => void }) {
  return (
    <View style={s.header}>
      <Pressable
        hitSlop={12}
        onPress={onBack ?? (() => (router.canGoBack() ? router.back() : router.replace('/')))}
        accessibilityLabel="뒤로"
        style={s.headerSide}
      >
        <Icon name="back" />
      </Pressable>
      <Text style={s.headerTitle}>{title}</Text>
      <View style={[s.headerSide, { alignItems: 'flex-end' }]}>{right}</View>
    </View>
  );
}

export function Label({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[s.label, style]}>{children}</Text>;
}

export function Kicker({ children, color = colors.green }: { children: ReactNode; color?: string }) {
  return <Text style={[s.kicker, { color }]}>{children}</Text>;
}

type ButtonVariant = 'primary' | 'green' | 'outline' | 'ghost';

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled,
  style,
  icon,
}: {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  icon?: ReactNode;
}) {
  const bg = { primary: colors.ink, green: colors.green, outline: colors.surface, ghost: 'transparent' }[variant];
  const fg = variant === 'outline' || variant === 'ghost' ? colors.ink : '#fff';
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      style={({ pressed }) => [
        s.button,
        { backgroundColor: bg, opacity: disabled ? 0.35 : pressed ? 0.8 : 1 },
        variant === 'outline' && { borderWidth: 1, borderColor: colors.line },
        style,
      ]}
    >
      {icon}
      <Text style={[s.buttonText, { color: fg }]}>{title}</Text>
    </Pressable>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[s.card, style]}>{children}</View>;
}

export function Badge({ children, tone = 'outline' }: { children: ReactNode; tone?: 'outline' | 'green' | 'dark' | 'beige' }) {
  const map = {
    outline: { bg: 'transparent', fg: colors.ink, border: colors.ink },
    green: { bg: colors.greenSoft, fg: colors.green, border: colors.green },
    dark: { bg: colors.ink, fg: '#fff', border: colors.ink },
    beige: { bg: colors.beigeSoft, fg: '#8A6A3F', border: colors.beige },
  }[tone];
  return (
    <View style={[s.badge, { backgroundColor: map.bg, borderColor: map.border }]}>
      <Text style={[s.badgeText, { color: map.fg }]}>{children}</Text>
    </View>
  );
}

export function Stars({ value, size = 16, onChange }: { value: number; size?: number; onChange?: (v: number) => void }) {
  return (
    <View style={{ flexDirection: 'row', gap: 2 }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable key={n} disabled={!onChange} onPress={() => onChange?.(n)} hitSlop={4} accessibilityLabel={`${n}점`}>
          <Icon name="star" size={size} color={colors.beige} filled={n <= value} />
        </Pressable>
      ))}
    </View>
  );
}

export function Divider({ style }: { style?: StyleProp<ViewStyle> }) {
  return <View style={[{ height: 1, backgroundColor: colors.line }, style]} />;
}

export function Row({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center' }, style]}>{children}</View>;
}

/** 확인 대화상자 (웹에서는 Alert 버튼이 동작하지 않아 window.confirm 사용) */
export function confirm(title: string, message: string, onOk: () => void, okLabel = '확인') {
  if (Platform.OS === 'web') {
    if (globalThis.confirm?.(`${title}\n\n${message}`)) onOk();
    return;
  }
  Alert.alert(title, message, [
    { text: '취소', style: 'cancel' },
    { text: okLabel, style: 'destructive', onPress: onOk },
  ]);
}

export function notify(title: string, message?: string) {
  if (Platform.OS === 'web') {
    globalThis.alert?.(message ? `${title}\n\n${message}` : title);
    return;
  }
  Alert.alert(title, message);
}

const s = StyleSheet.create({
  serif: { fontFamily: fonts.serif, color: colors.ink },
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: space.gutter, paddingBottom: 40 },
  footer: {
    paddingHorizontal: space.gutter,
    paddingTop: 12,
    backgroundColor: colors.bg,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  header: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: space.gutter,
  },
  headerSide: { width: 72 },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 17, fontWeight: '700', color: colors.ink },
  label: { fontSize: 13, color: colors.sub, fontWeight: '600', marginBottom: 10, letterSpacing: 0.5 },
  kicker: { fontSize: 13, fontWeight: '700', letterSpacing: 1.2 },
  button: {
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 20,
  },
  buttonText: { fontSize: 16, fontWeight: '700' },
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 16 },
  badge: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 9, paddingVertical: 3, alignSelf: 'flex-start' },
  badgeText: { fontSize: 11, fontWeight: '700' },
});
