import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import { FORMALITY_LABEL, WEEKDAY_SHORT } from '@/data/constants';
import type { Formality, Profile, WorkType } from '@/data/types';
import { activeCodes, colorLabel, styleLabel, withCurrent } from '@/lib/codes';
import { useCodesVersion } from '@/store/useCodes';
import { colors } from '@/theme';
import { ChipGroup } from './Chip';
import { Badge, Label } from './ui';

const DRESS: Formality[] = ['BIZ_CASUAL', 'FORMAL', 'CASUAL'];
const WORK_TYPES: WorkType[] = ['WEEKDAY', 'WEEKEND', 'CUSTOM'];
const WORK_LABEL: Record<WorkType, string> = { WEEKDAY: '평일 근무', WEEKEND: '주말 근무', CUSTOM: '요일 직접 선택' };
const WORK_PRESET: Record<WorkType, number[]> = { WEEKDAY: [1, 2, 3, 4, 5], WEEKEND: [0, 6], CUSTOM: [] };
const DAY_ORDER = [1, 2, 3, 4, 5, 6, 0];

export function StylePrefs({ value, onChange }: { value: Profile; onChange: (p: Profile) => void }) {
  useCodesVersion(); // 코드가 갱신되면 다시 그린다
  return (
    <View style={{ gap: 26 }}>
      <View>
        <Label>출근 복장</Label>
        <ChipGroup
          options={DRESS}
          labels={{ ...FORMALITY_LABEL, CASUAL: '자유복' }}
          value={value.dressCodes}
          multi
          onChange={(dressCodes) => onChange({ ...value, dressCodes })}
        />
      </View>
      <View>
        <Label>선호 분위기 (복수 선택)</Label>
        <ChipGroup
          options={withCurrent(activeCodes('STYLE'), value.moods)}
          labels={styleLabel}
          value={value.moods}
          multi
          onChange={(moods) => onChange({ ...value, moods })}
        />
      </View>
      <View>
        <Label>자주 입는 색</Label>
        <ChipGroup
          options={withCurrent(activeCodes('COLOR'), value.colors)}
          labels={colorLabel}
          value={value.colors}
          multi
          dots
          small
          onChange={(cs) => onChange({ ...value, colors: cs })}
        />
      </View>
    </View>
  );
}

export function WorkPattern({ value, onChange }: { value: Profile; onChange: (p: Profile) => void }) {
  const toggleDay = (d: number) => {
    const has = value.workDays.includes(d);
    onChange({ ...value, workType: 'CUSTOM', workDays: has ? value.workDays.filter((x) => x !== d) : [...value.workDays, d] });
  };
  return (
    <View style={s.card}>
      <View style={s.head}>
        <Text style={s.title}>나의 근무 패턴</Text>
        <Badge>선택</Badge>
      </View>
      <Text style={s.desc}>근무하는 날에는 출근 복장 위주로, 쉬는 날에는 편한 데일리·주말 코디로 추천해 드려요.</Text>
      <Label style={{ marginTop: 18 }}>근무 형태</Label>
      <ChipGroup
        options={WORK_TYPES}
        labels={WORK_LABEL}
        value={value.workType}
        onChange={([t]) => onChange({ ...value, workType: t, workDays: t === 'CUSTOM' ? value.workDays : WORK_PRESET[t] })}
        small
      />
      <Label style={{ marginTop: 18 }}>
        근무 요일 <Text style={{ color: colors.faint, fontWeight: '400' }}>(탭해서 바꿀 수 있어요)</Text>
      </Label>
      <View style={s.days}>
        {DAY_ORDER.map((d) => {
          const on = value.workDays.includes(d);
          return (
            <Pressable key={d} onPress={() => toggleDay(d)} style={[s.day, on ? s.dayOn : s.dayOff]} accessibilityState={{ selected: on }}>
              <Text style={[s.dayText, { color: on ? '#fff' : colors.ink }]}>{WEEKDAY_SHORT[d]}</Text>
            </Pressable>
          );
        })}
      </View>
      <View style={s.toggle}>
        <View style={{ flex: 1 }}>
          <Text style={s.toggleTitle}>근무일엔 출근 코디로</Text>
          <Text style={s.toggleSub}>상황을 ‘출근’으로 두고 추천해요</Text>
        </View>
        <Switch value={value.workRule} onValueChange={(workRule) => onChange({ ...value, workRule })} trackColor={{ true: colors.green }} />
      </View>
      <View style={s.toggle}>
        <View style={{ flex: 1 }}>
          <Text style={s.toggleTitle}>쉬는 날엔 편한 데일리로</Text>
          <Text style={s.toggleSub}>휴무일은 캐주얼 위주로 추천해요</Text>
        </View>
        <Switch value={value.offRule} onValueChange={(offRule) => onChange({ ...value, offRule })} trackColor={{ true: colors.green }} />
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 18 },
  head: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { fontSize: 17, fontWeight: '800', color: colors.ink },
  desc: { fontSize: 13, color: colors.sub, marginTop: 8, lineHeight: 19 },
  days: { flexDirection: 'row', gap: 6 },
  day: { flex: 1, height: 40, alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  dayOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  dayOff: { backgroundColor: colors.surface, borderColor: colors.line },
  dayText: { fontWeight: '700' },
  toggle: { flexDirection: 'row', alignItems: 'center', marginTop: 16, gap: 12 },
  toggleTitle: { fontSize: 14, fontWeight: '700', color: colors.ink },
  toggleSub: { fontSize: 12, color: colors.sub, marginTop: 2 },
});
