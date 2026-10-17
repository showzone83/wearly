import { Text, TextInput, View } from 'react-native';

import type { WardrobeItem } from '@/data/types';
import {
  activeCodes,
  categoryLabel,
  colorLabel,
  patternLabel,
  seasonLabel,
  statusLabel,
  styleLabel,
  subTypeLabel,
  subTypesOf,
  withCurrent,
} from '@/lib/codes';
import { useCodesVersion } from '@/store/useCodes';
import { inputBase, makeStyles, useColors } from '@/theme';
import { ChipGroup } from './Chip';
import { Badge, Label, Row } from './ui';

export type ItemDraft = Omit<WardrobeItem, 'id' | 'wornCount' | 'createdAt' | 'lastWornAt'>;

const FORMALITY_OPTIONS = ['1', '2', '3'] as const;
const FORMALITY_TEXT: Record<(typeof FORMALITY_OPTIONS)[number], string> = { '1': '편한 데일리', '2': '비즈니스 캐주얼', '3': '포멀' };

export function defaultName(d: Pick<ItemDraft, 'color' | 'subType'>): string {
  return `${colorLabel(d.color)} ${subTypeLabel(d.subType)}`;
}

interface Props {
  value: ItemDraft;
  onChange: (next: ItemDraft) => void;
  /** AI가 제안한 항목에 표시 */
  aiFields?: ('category' | 'color' | 'pattern')[];
}

/**
 * 옷 등록 3단계 · 아이템 수정 공용 폼 (목업 p.41).
 * 고를 수 있는 값은 서버 코드의 활성 값이다. 이미 저장된 값이 비활성이면 선택된 칩으로 함께 보여 준다.
 */
export function ItemForm({ value, onChange, aiFields = [] }: Props) {
  const s = useStyles();
  const colors = useColors();
  useCodesVersion(); // 코드가 갱신되면 다시 그린다
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
          options={withCurrent(activeCodes('CATEGORY'), value.category)}
          labels={categoryLabel}
          value={value.category}
          onChange={([category]) => {
            // 종류를 바꾸면 그 종류의 첫 세부 종류로 맞춘다 (서버는 subType의 parent가 category인지 검증한다)
            if (category !== value.category) patch({ category, subType: subTypesOf(category)[0] ?? '' });
          }}
        />
        <View style={s.subTypes}>
          <ChipGroup
            options={withCurrent(subTypesOf(value.category), value.subType)}
            labels={subTypeLabel}
            value={value.subType}
            small
            onChange={([subType]) => patch({ subType })}
          />
        </View>
      </View>
      <View>
        <Row style={s.labelRow}>
          <Label style={s.noMargin}>주요 색상</Label>
          {ai('color')}
        </Row>
        <ChipGroup
          options={withCurrent(activeCodes('COLOR'), value.color)}
          labels={colorLabel}
          value={value.color}
          dots
          small
          onChange={([color]) => patch({ color })}
        />
      </View>
      <View>
        <Row style={s.labelRow}>
          <Label style={s.noMargin}>패턴</Label>
          {ai('pattern')}
        </Row>
        <ChipGroup
          options={withCurrent(activeCodes('PATTERN'), value.pattern)}
          labels={patternLabel}
          value={value.pattern}
          onChange={([pattern]) => patch({ pattern })}
        />
      </View>
      <View>
        <Label>계절</Label>
        <ChipGroup
          options={withCurrent(activeCodes('SEASON'), value.seasons)}
          labels={seasonLabel}
          value={value.seasons}
          multi
          onChange={(seasons) => patch({ seasons })}
        />
      </View>
      <View>
        <Label>스타일</Label>
        <ChipGroup
          options={withCurrent(activeCodes('STYLE'), value.styles)}
          labels={styleLabel}
          value={value.styles}
          multi
          onChange={(styles) => patch({ styles })}
        />
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
        <ChipGroup
          options={withCurrent(activeCodes('ITEM_STATUS'), value.status)}
          labels={statusLabel}
          value={value.status}
          onChange={([status]) => patch({ status })}
        />
      </View>
      <View>
        <Label>이름</Label>
        <TextInput value={value.name} onChangeText={(name) => onChange({ ...value, name })} style={s.input} placeholder="예: 네이비 니트" placeholderTextColor={colors.textMuted} />
      </View>
      <View>
        <Row style={[s.labelRow, { justifyContent: 'space-between' }]}>
          <Label style={s.noMargin}>브랜드 · 가격 · 소재</Label>
          <Text style={{ color: colors.textMuted, fontSize: 12 }}>선택 입력</Text>
        </Row>
        <TextInput
          value={value.brand ?? ''}
          onChangeText={(brand) => onChange({ ...value, brand })}
          style={s.input}
          placeholder="예: 유니클로 · 39,900원 · 울 30%"
          placeholderTextColor={colors.textMuted}
        />
      </View>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  labelRow: { gap: 8, marginBottom: 12 },
  subTypes: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: t.colors.border },
  noMargin: { marginBottom: 0 },
  input: inputBase(t),
}));
