import { Pressable, Text, View } from 'react-native';

import type { ColorKey } from '@/data/types';
import { colorMeta } from '@/lib/codes';
import { makeStyles, useColors } from '@/theme';
import { Icon, type IconName } from './Icon';

interface IChipProps {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  dot?: ColorKey;
  small?: boolean;
}

/** 알약 칩: 선택 = 초록 채움 + 흰 글자, 비선택 = 흰 바탕 + 연한 테두리 */
export const Chip = ({ label, selected, onPress, dot, small }: IChipProps) => {
  const s = useStyles();
  const c = useColors();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={[s.chip, small && s.small, selected ? s.on : s.off]}
    >
      {dot && (
        <View style={[s.dot, { backgroundColor: colorMeta(dot).hex }, colorMeta(dot).light >= 0.9 && s.dotEdge]} />
      )}
      <Text style={[s.text, small && { fontSize: 13 }, { color: selected ? c.onPrimary : c.text }]}>{label}</Text>
    </Pressable>
  );
};

interface IGroupProps<T extends string> {
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

/** 선택 토글 규칙 (칩·색 동그라미·아이콘 버튼 공통) */
const toggleValue = <T extends string>(selected: T[], v: T, multi?: boolean, allowEmpty?: boolean): T[] => {
  if (multi) return selected.includes(v) ? selected.filter((x) => x !== v) : [...selected, v];
  if (selected.includes(v)) return allowEmpty ? [] : [v];
  return [v];
};

const asList = <T extends string>(value: T[] | T | undefined): T[] =>
  Array.isArray(value) ? value : value ? [value] : [];

export const ChipGroup = <T extends string>({
  options,
  labels,
  value,
  onChange,
  multi,
  dots,
  small,
  allowEmpty,
}: IGroupProps<T>) => {
  const s = useStyles();

  const selected = asList(value);
  const label = (v: T) => (typeof labels === 'function' ? labels(v) : labels[v]);

  return (
    <View style={s.group}>
      {options.map((o) => (
        <Chip
          key={o}
          label={label(o)}
          selected={selected.includes(o)}
          onPress={() => onChange(toggleValue(selected, o, multi, allowEmpty))}
          dot={dots ? o : undefined}
          small={small}
        />
      ))}
    </View>
  );
};

interface IColorDotsProps {
  options: readonly ColorKey[];
  labels: (v: ColorKey) => string;
  value: ColorKey[];
  onChange: (next: ColorKey[]) => void;
}

/** 보드의 "선호하는 색상": 색 동그라미 줄. 고른 색은 초록 테두리 + 체크, 아래에 이름을 보여 준다 */
export const ColorDots = ({ options, labels, value, onChange }: IColorDotsProps) => {
  const s = useStyles();
  const c = useColors();

  return (
    <View>
      <View style={s.dots}>
        {options.map((o) => {
          const on = value.includes(o);
          const meta = colorMeta(o);
          return (
            <Pressable
              key={o}
              onPress={() => onChange(toggleValue(value, o, true))}
              accessibilityRole="button"
              accessibilityLabel={labels(o)}
              accessibilityState={{ selected: on }}
              style={[s.ring, on && { borderColor: c.primary }]}
            >
              <View style={[s.swatch, { backgroundColor: meta.hex }, meta.light >= 0.9 && s.dotEdge]}>
                {on && <Icon name="check" size={16} strokeWidth={2.4} color={meta.light >= 0.6 ? c.text : '#FFFFFF'} />}
              </View>
            </Pressable>
          );
        })}
      </View>
      <Text style={s.dotsNote}>{value.length ? value.map(labels).join(' · ') : '아직 고르지 않았어요'}</Text>
    </View>
  );
};

interface IIconChoiceProps<T extends string> {
  options: readonly T[];
  labels: Record<T, string>;
  icons: Record<T, IconName>;
  value: T[];
  onChange: (next: T[]) => void;
  multi?: boolean;
}

/** 보드의 "자주 입는 옷": 원형 아이콘 버튼 + 아래 이름 */
export const IconChoice = <T extends string>({
  options,
  labels,
  icons,
  value,
  onChange,
  multi,
}: IIconChoiceProps<T>) => {
  const s = useStyles();
  const c = useColors();

  return (
    <View style={s.iconRow}>
      {options.map((o) => {
        const on = value.includes(o);
        return (
          <Pressable
            key={o}
            onPress={() => onChange(toggleValue(value, o, multi))}
            accessibilityRole="button"
            accessibilityLabel={labels[o]}
            accessibilityState={{ selected: on }}
            style={s.iconItem}
          >
            <View style={[s.iconCircle, on ? s.on : s.off]}>
              <Icon name={icons[o]} size={28} color={on ? c.onPrimary : c.text} strokeWidth={1.4} />
            </View>
            <Text style={[s.iconLabel, on && { color: c.primary, fontWeight: '800' }]}>{labels[o]}</Text>
          </Pressable>
        );
      })}
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  group: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    height: 40,
    paddingHorizontal: 18,
    borderRadius: t.radius.pill,
    borderWidth: 1,
  },
  small: { height: 34, paddingHorizontal: 14 },
  on: { backgroundColor: t.colors.primary, borderColor: t.colors.primary },
  off: { backgroundColor: t.colors.chip, borderColor: t.colors.chipBorder },
  text: { fontSize: 14, fontWeight: '600' },
  dot: { width: 10, height: 10, borderRadius: 5 },
  dotEdge: { borderWidth: 1, borderColor: t.colors.border },
  dots: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  ring: { padding: 3, borderRadius: 22, borderWidth: 2, borderColor: 'transparent' },
  swatch: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  dotsNote: { fontSize: 12, color: t.colors.textSub, marginTop: 8 },
  iconRow: { flexDirection: 'row', gap: 12 },
  iconItem: { flex: 1, alignItems: 'center', gap: 8 },
  iconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconLabel: { fontSize: 13, color: t.colors.textSub, fontWeight: '600', textAlign: 'center' },
}));
