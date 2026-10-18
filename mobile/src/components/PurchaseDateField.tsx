import { Pressable, ScrollView, Text, View } from 'react-native';

import { todayKey } from '@/lib/date';
import { PURCHASE_MIN } from '@/lib/itemAttrs';
import { makeStyles, useColors } from '@/theme';
import { Chip, ChipGroup } from './Chip';
import { Label, Row } from './ui';

type TMode = 'UNKNOWN' | 'DATE';

const MODE_OPTIONS: readonly TMode[] = ['UNKNOWN', 'DATE'];
const MODE_LABEL: Record<TMode, string> = { UNKNOWN: '모름 (등록일로 저장)', DATE: '날짜 고르기' };

interface IPurchaseDateFieldProps {
  /** 구입일 YYYY-MM-DD. undefined = 모름 (저장하면 등록일이 들어간다) */
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  /** "날짜 고르기"를 처음 누를 때 채울 날짜 (등록일, 새 옷이면 오늘) */
  baseDate?: string;
}

const pad = (n: number) => String(n).padStart(2, '0');
const keyOf = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`;
const partsOf = (key: string): [number, number, number] => key.split('-').map(Number) as [number, number, number];
const daysIn = (y: number, m: number) => new Date(y, m, 0).getDate();

/** 고른 연·월·일을 1990-01-01 ~ 오늘 안으로 맞춘다 (월이 바뀌어 없는 날이면 그달 마지막 날) */
const clampKey = (y: number, m: number, d: number, today: string): string => {
  const key = keyOf(y, m, Math.min(d, daysIn(y, m)));
  if (key > today) return today;
  if (key < PURCHASE_MIN) return PURCHASE_MIN;
  return key;
};

/**
 * 구입일 입력: "모름(등록일로 저장)" 또는 날짜 고르기(연도 가로 목록 · 월 · 일).
 * 고를 수 있는 날짜는 1990-01-01 ~ 오늘(기기 날짜)이다. 미래 달·날은 흐리게 보이고 눌리지 않는다.
 */
export const PurchaseDateField = ({ value, onChange, baseDate }: IPurchaseDateFieldProps) => {
  const s = useStyles();
  const c = useColors();

  const today = todayKey();
  const mode: TMode = value ? 'DATE' : 'UNKNOWN';
  const [ty, tm] = partsOf(today);
  const [y, m, d] = partsOf(value ?? today);
  const minYear = Number(PURCHASE_MIN.slice(0, 4));
  const years = Array.from({ length: ty - minYear + 1 }, (_, i) => String(ty - i));

  const onMode = (next: TMode) => {
    if (next === mode) return;
    if (next === 'UNKNOWN') onChange(undefined);
    else {
      const base = baseDate && baseDate <= today && baseDate >= PURCHASE_MIN ? baseDate : today;
      onChange(base);
    }
  };

  return (
    <View>
      <Row style={s.head}>
        <Label style={s.noMargin}>구입일</Label>
        <Text style={s.summary}>{value ? `${y}년 ${m}월 ${d}일` : '모르면 등록일로 저장돼요'}</Text>
      </Row>
      <ChipGroup options={MODE_OPTIONS} labels={MODE_LABEL} value={mode} small onChange={([next]) => onMode(next)} />

      {value && (
        <View style={s.box}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.years}>
            {years.map((yy) => (
              <Chip
                key={yy}
                label={`${yy}년`}
                small
                selected={Number(yy) === y}
                onPress={() => onChange(clampKey(Number(yy), m, d, today))}
              />
            ))}
          </ScrollView>

          <View style={s.grid}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map((mm) => {
              const future = y === ty && mm > tm;
              const on = mm === m;
              return (
                <Pressable
                  key={mm}
                  disabled={future}
                  onPress={() => onChange(clampKey(y, mm, d, today))}
                  accessibilityRole="button"
                  accessibilityLabel={`${mm}월`}
                  accessibilityState={{ selected: on, disabled: future }}
                  style={[s.monthCell, on && s.cellOn, future && s.cellOff]}
                >
                  <Text style={[s.cellText, { color: on ? c.onPrimary : c.text }]}>{mm}월</Text>
                </Pressable>
              );
            })}
          </View>

          <View style={s.grid}>
            {Array.from({ length: daysIn(y, m) }, (_, i) => i + 1).map((dd) => {
              const future = keyOf(y, m, dd) > today;
              const on = dd === d;
              return (
                <Pressable
                  key={dd}
                  disabled={future}
                  onPress={() => onChange(keyOf(y, m, dd))}
                  accessibilityRole="button"
                  accessibilityLabel={`${dd}일`}
                  accessibilityState={{ selected: on, disabled: future }}
                  style={[s.dayCell, on && s.cellOn, future && s.cellOff]}
                >
                  <Text style={[s.cellText, { color: on ? c.onPrimary : c.text }]}>{dd}</Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      )}
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  head: { justifyContent: 'space-between', marginBottom: 12, gap: 8 },
  noMargin: { marginBottom: 0 },
  summary: { fontSize: 13, color: t.colors.textSub, fontWeight: '600' },
  box: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: t.colors.border,
    gap: 12,
  },
  years: { gap: 8 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', rowGap: 6 },
  // 한 줄에 달 6개, 날 7개
  monthCell: {
    width: '16.66%',
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: t.radius.pill,
  },
  dayCell: {
    width: '14.28%',
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: t.radius.pill,
  },
  cellOn: { backgroundColor: t.colors.primary },
  cellOff: { opacity: 0.3 },
  cellText: { fontSize: 13, fontWeight: '600' },
}));
