import { useState } from 'react';
import { Text, TextInput, View } from 'react-native';

import type { ColorKey, WardrobeItem } from '@/data/types';
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
import { itemColors, makePrimaryColor, MAX_COLORS, toggleColor, type IColorPick } from '@/lib/itemAttrs';
import { useCodesVersion } from '@/store/useCodes';
import { inputBase, makeStyles, useColors } from '@/theme';
import { Chip, ChipGroup } from './Chip';
import { PurchaseDateField } from './PurchaseDateField';
import { Badge, Label, Row } from './ui';

/** 등록·수정 폼 값. 착용 기록과 사용 중지(doc/13)는 폼에서 고치지 않는다 */
export type ItemDraft = Omit<
  WardrobeItem,
  | 'id'
  | 'wornCount'
  | 'createdAt'
  | 'lastWornAt'
  | 'retiredAt'
  | 'retireReason'
  | 'retireMemo'
  | 'colors'
  | 'purchasedAt'
  | 'purchasedAtEstimated'
> & {
  /** 색 1~3개, 첫 번째가 주 색상. color는 늘 colors[0]과 같게 둔다 */
  colors: ColorKey[];
  /** 구입일 YYYY-MM-DD. undefined = 모름 (저장하면 등록일이 들어가고 estimated) */
  purchasedAt?: string;
};

/**
 * 아이템(또는 폼 값)에서 폼 값만 뽑는다 (사용 중지 필드가 폼 저장으로 덮이지 않게).
 * 등록일로 채운 구입일(estimated)은 "모름"으로 연다. 예전 데이터(colors 없음)는 [color]
 */
export const draftOf = (i: ItemDraft | WardrobeItem): ItemDraft => ({
  name: i.name,
  category: i.category,
  subType: i.subType,
  color: itemColors(i)[0],
  colors: itemColors(i),
  purchasedAt: 'purchasedAtEstimated' in i && i.purchasedAtEstimated ? undefined : i.purchasedAt,
  pattern: i.pattern,
  seasons: i.seasons,
  styles: i.styles,
  formality: i.formality,
  status: i.status,
  imageUri: i.imageUri,
  brand: i.brand,
});

const FORMALITY_OPTIONS = ['1', '2', '3'] as const;
export const FORMALITY_TEXT: Record<(typeof FORMALITY_OPTIONS)[number], string> = { '1': '편한 데일리', '2': '비즈니스 캐주얼', '3': '포멀' };

export function defaultName(d: Pick<ItemDraft, 'color' | 'subType'>): string {
  return `${colorLabel(d.color)} ${subTypeLabel(d.subType)}`;
}

/** 고른 순서 표시 (①②③) */
const ORDER_MARK = ['①', '②', '③'];

const COLOR_HINT = '첫 번째가 주 색상 · 길게 누르면 주 색상으로';
const COLOR_BLOCK_TEXT: Record<NonNullable<IColorPick['blocked']>, string> = {
  FULL: `색은 ${MAX_COLORS}개까지 고를 수 있어요`,
  LAST: '색은 하나 이상 골라야 해요',
};

interface Props {
  value: ItemDraft;
  onChange: (next: ItemDraft) => void;
  /** AI가 제안한 항목에 표시 */
  aiFields?: ('category' | 'color' | 'pattern')[];
  /** 구입일 "날짜 고르기"를 처음 누를 때 채울 날짜 (수정 화면은 등록일). 없으면 오늘 */
  purchaseBaseDate?: string;
}

/**
 * 옷 등록 3단계 · 아이템 수정 공용 폼 (목업 p.41).
 * 고를 수 있는 값은 서버 코드의 활성 값이다. 이미 저장된 값이 비활성이면 선택된 칩으로 함께 보여 준다.
 */
export function ItemForm({ value, onChange, aiFields = [], purchaseBaseDate }: Props) {
  /** 색을 더 고르거나 빼지 못했을 때 안내 (다음 색 변경까지 보인다) */
  const [colorNote, setColorNote] = useState<string | null>(null);
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

  const picked = value.colors.length > 0 ? value.colors : [value.color];
  const onPickColor = (r: IColorPick) => {
    setColorNote(r.blocked ? COLOR_BLOCK_TEXT[r.blocked] : null);
    if (!r.blocked) patch({ colors: r.colors, color: r.colors[0] });
  };

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
          <Text style={s.labelSub}>{MAX_COLORS}개까지</Text>
        </Row>
        <View style={s.chips}>
          {withCurrent(activeCodes('COLOR'), picked).map((c) => {
            const order = picked.indexOf(c);
            return (
              <Chip
                key={c}
                label={colorLabel(c)}
                dot={c}
                small
                selected={order >= 0}
                mark={order >= 0 ? ORDER_MARK[order] : undefined}
                accessibilityLabel={
                  order === 0 ? `${colorLabel(c)}, 주 색상` : order > 0 ? `${colorLabel(c)}, ${order + 1}번째 색` : colorLabel(c)
                }
                accessibilityHint="길게 누르면 주 색상으로 바꿔요"
                onPress={() => onPickColor(toggleColor(picked, c))}
                onLongPress={() => onPickColor(makePrimaryColor(picked, c))}
              />
            );
          })}
        </View>
        <Text style={s.colorSummary}>
          주 색상 {colorLabel(picked[0])}
          {picked.length > 1 ? ` · 보조 색 ${picked.slice(1).map(colorLabel).join(', ')}` : ''}
        </Text>
        <Text style={[s.hint, colorNote && { color: colors.danger }]} accessibilityLiveRegion="polite">
          {colorNote ?? COLOR_HINT}
        </Text>
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
      <PurchaseDateField
        value={value.purchasedAt}
        onChange={(purchasedAt) => onChange({ ...value, purchasedAt })}
        baseDate={purchaseBaseDate}
      />
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
  labelSub: { fontSize: 12, color: t.colors.textMuted },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  colorSummary: { fontSize: 13, color: t.colors.text, fontWeight: '600', marginTop: 10 },
  hint: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
  input: inputBase(t),
}));
