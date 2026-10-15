import { StyleSheet, Text, TextInput, View } from 'react-native';

import {
  CATEGORIES,
  CATEGORY_LABEL,
  COLOR,
  COLOR_KEYS,
  PATTERN_LABEL,
  SEASON_LABEL,
  SEASONS,
  STATUS_LABEL,
  STYLE_LABEL,
  STYLE_TAGS,
  SUBTYPE,
  SUBTYPES_BY_CATEGORY,
} from '@/data/constants';
import type { ItemStatus, Pattern, WardrobeItem } from '@/data/types';
import { colors } from '@/theme';
import { ChipGroup } from './Chip';
import { Badge, Label, Row } from './ui';

export type ItemDraft = Omit<WardrobeItem, 'id' | 'wornCount' | 'createdAt' | 'lastWornAt'>;

const FORMALITY_OPTIONS = ['1', '2', '3'] as const;
const FORMALITY_TEXT: Record<(typeof FORMALITY_OPTIONS)[number], string> = { '1': '편한 데일리', '2': '비즈니스 캐주얼', '3': '포멀' };

export function defaultName(d: Pick<ItemDraft, 'color' | 'subType'>): string {
  return `${COLOR[d.color].label} ${SUBTYPE[d.subType].label}`;
}

interface Props {
  value: ItemDraft;
  onChange: (next: ItemDraft) => void;
  /** AI가 제안한 항목에 표시 */
  aiFields?: ('category' | 'color' | 'pattern')[];
}

/** 옷 등록 3단계 · 아이템 수정 공용 폼 (목업 p.41) */
export function ItemForm({ value, onChange, aiFields = [] }: Props) {
  // 이름을 직접 바꾸지 않았다면 종류·색 변경에 맞춰 자동으로 갱신한다
  const autoName = value.name === defaultName(value);
  const patch = (p: Partial<ItemDraft>) => {
    const next = { ...value, ...p };
    if (autoName && (p.color || p.subType)) next.name = defaultName(next);
    onChange(next);
  };
  const ai = (f: (typeof aiFields)[number]) => aiFields.includes(f) && <Badge tone="green">AI</Badge>;

  return (
    <View style={{ gap: 24 }}>
      <View>
        <Row style={s.labelRow}>
          <Label style={s.noMargin}>종류</Label>
          {ai('category')}
        </Row>
        <ChipGroup
          options={CATEGORIES}
          labels={CATEGORY_LABEL}
          value={value.category}
          onChange={([category]) => patch({ category, subType: SUBTYPES_BY_CATEGORY[category][0] })}
        />
        <View style={{ height: 10 }} />
        <ChipGroup
          options={SUBTYPES_BY_CATEGORY[value.category]}
          labels={(t) => SUBTYPE[t].label}
          value={value.subType}
          small
          onChange={([subType]) => patch({ subType })}
        />
      </View>
      <View>
        <Row style={s.labelRow}>
          <Label style={s.noMargin}>주요 색상</Label>
          {ai('color')}
        </Row>
        <ChipGroup options={COLOR_KEYS} labels={(c) => COLOR[c].label} value={value.color} dots onChange={([color]) => patch({ color })} />
      </View>
      <View>
        <Row style={s.labelRow}>
          <Label style={s.noMargin}>패턴</Label>
          {ai('pattern')}
        </Row>
        <ChipGroup options={Object.keys(PATTERN_LABEL) as Pattern[]} labels={PATTERN_LABEL} value={value.pattern} onChange={([pattern]) => patch({ pattern })} />
      </View>
      <View>
        <Label>계절</Label>
        <ChipGroup options={SEASONS} labels={SEASON_LABEL} value={value.seasons} multi onChange={(seasons) => patch({ seasons })} />
      </View>
      <View>
        <Label>스타일</Label>
        <ChipGroup options={STYLE_TAGS} labels={STYLE_LABEL} value={value.styles} multi onChange={(styles) => patch({ styles })} />
      </View>
      <View>
        <Label>격식</Label>
        <ChipGroup
          options={FORMALITY_OPTIONS}
          labels={FORMALITY_TEXT}
          value={String(value.formality) as (typeof FORMALITY_OPTIONS)[number]}
          onChange={([f]) => patch({ formality: Number(f) as 1 | 2 | 3 })}
        />
      </View>
      <View>
        <Label>보유 상태</Label>
        <ChipGroup options={Object.keys(STATUS_LABEL) as ItemStatus[]} labels={STATUS_LABEL} value={value.status} onChange={([status]) => patch({ status })} />
      </View>
      <View>
        <Label>이름</Label>
        <TextInput value={value.name} onChangeText={(name) => onChange({ ...value, name })} style={s.input} placeholder="예: 네이비 니트" placeholderTextColor={colors.faint} />
      </View>
      <View>
        <Row style={[s.labelRow, { justifyContent: 'space-between' }]}>
          <Label style={s.noMargin}>브랜드 · 가격 · 소재</Label>
          <Text style={{ color: colors.faint, fontSize: 12 }}>선택 입력</Text>
        </Row>
        <TextInput
          value={value.brand ?? ''}
          onChangeText={(brand) => onChange({ ...value, brand })}
          style={s.input}
          placeholder="예: 유니클로 · 39,900원 · 울 30%"
          placeholderTextColor={colors.faint}
        />
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  labelRow: { gap: 8, marginBottom: 10 },
  noMargin: { marginBottom: 0 },
  input: {
    height: 48,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.line,
    paddingHorizontal: 14,
    fontSize: 15,
    color: colors.ink,
  },
});
