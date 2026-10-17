import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { ColorKey } from '@/data/types';
import { colorMeta } from '@/lib/codes';
import { colors } from '@/theme';

interface ChipProps {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  dot?: ColorKey;
  small?: boolean;
}

export function Chip({ label, selected, onPress, dot, small }: ChipProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={[s.chip, small && s.small, selected ? s.on : s.off]}
    >
      {dot && <View style={[s.dot, { backgroundColor: colorMeta(dot).hex }, colorMeta(dot).light >= 0.9 && s.dotEdge]} />}
      <Text style={[s.text, small && { fontSize: 13 }, { color: selected ? '#fff' : colors.ink }]}>{label}</Text>
    </Pressable>
  );
}

interface GroupProps<T extends string> {
  options: readonly T[];
  labels: Record<T, string> | ((v: T) => string);
  value: T[] | T | undefined;
  onChange: (next: T[]) => void;
  multi?: boolean;
  dots?: boolean;
  small?: boolean;
  /** 단일 선택에서 다시 눌러 해제 허용 */
  allowEmpty?: boolean;
}

export function ChipGroup<T extends string>({ options, labels, value, onChange, multi, dots, small, allowEmpty }: GroupProps<T>) {
  const selected: T[] = Array.isArray(value) ? value : value ? [value] : [];
  const label = (v: T) => (typeof labels === 'function' ? labels(v) : labels[v]);
  const toggle = (v: T) => {
    if (multi) onChange(selected.includes(v) ? selected.filter((x) => x !== v) : [...selected, v]);
    else if (selected.includes(v)) onChange(allowEmpty ? [] : [v]);
    else onChange([v]);
  };
  return (
    <View style={s.group}>
      {options.map((o) => (
        <Chip
          key={o}
          label={label(o)}
          selected={selected.includes(o)}
          onPress={() => toggle(o)}
          dot={dots ? o : undefined}
          small={small}
        />
      ))}
    </View>
  );
}

const s = StyleSheet.create({
  group: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 38,
    paddingHorizontal: 15,
    borderRadius: 19,
    borderWidth: 1,
  },
  small: { height: 32, paddingHorizontal: 12 },
  on: { backgroundColor: colors.ink, borderColor: colors.ink },
  off: { backgroundColor: colors.surface, borderColor: colors.line },
  text: { fontSize: 14, fontWeight: '600' },
  dot: { width: 10, height: 10, borderRadius: 5 },
  dotEdge: { borderWidth: 1, borderColor: colors.faint },
});
