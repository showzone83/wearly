import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';

import type { Profile } from '@/data/types';
import { activeCodes, codeLabel, withCurrent } from '@/lib/codes';
import {
  bodyTypeInfo,
  HEIGHT_MAX,
  HEIGHT_MIN,
  heightText,
  parseHeight,
  PERSONAL_NOTICE,
  personalOf,
  withPersonal,
} from '@/lib/personalInfo';
import { useCodesVersion } from '@/store/useCodes';
import { inputBase, makeStyles, useColors } from '@/theme';
import { ChipGroup } from './Chip';
import { Icon } from './Icon';
import { Label, Notice, Row } from './ui';

interface IProps {
  value: Profile;
  onChange: (p: Profile) => void;
  /** 키 입력 칸이 올바른지 (틀리면 저장 버튼을 막는다) */
  onValidChange?: (ok: boolean) => void;
}

/**
 * 나의 정보 고르기: 연령대 · 체형(설명 포함) · 키 · 핏 선호. 모두 선택이고, 고른 칩을 다시 누르면 비운다.
 * 코드 목록은 서버 코드(AGE_RANGE·BODY_TYPE·FIT)라 관리자가 바꿀 수 있다.
 */
export const PersonalForm = ({ value, onChange, onValidChange }: IProps) => {
  const info = personalOf(value);
  const [height, setHeight] = useState(() => heightText(info.heightCm));
  const [heightError, setHeightError] = useState(false);
  useCodesVersion(); // 코드가 갱신되면 다시 그린다
  const s = useStyles();
  const c = useColors();

  const set = (patch: Partial<typeof info>) => onChange(withPersonal(value, { ...info, ...patch }));

  const onHeight = (text: string) => {
    const digits = text.replace(/[^0-9]/g, '').slice(0, 3);
    setHeight(digits);
    const parsed = parseHeight(digits);
    setHeightError(!parsed.ok);
    onValidChange?.(parsed.ok);
    if (parsed.ok) set({ heightCm: parsed.value });
  };

  const bodyTypes = withCurrent(activeCodes('BODY_TYPE'), info.bodyType ?? undefined);

  return (
    <View style={{ gap: 26 }}>
      <Notice tone="plain">
        <Text style={s.notice}>{PERSONAL_NOTICE}</Text>
      </Notice>

      <View>
        <Label>연령대</Label>
        <ChipGroup
          options={withCurrent(activeCodes('AGE_RANGE'), info.ageRange ?? undefined)}
          labels={(v) => codeLabel('AGE_RANGE', v)}
          value={info.ageRange ?? undefined}
          allowEmpty
          small
          onChange={([v]) => set({ ageRange: v ?? null })}
        />
      </View>

      <View>
        <Label>체형</Label>
        <Text style={s.help}>가장 가까운 것 하나를 골라 주세요. 모르겠으면 비워 두셔도 돼요.</Text>
        <View style={{ gap: 8 }}>
          {bodyTypes.map((code) => {
            const b = bodyTypeInfo(code);
            const on = info.bodyType === code;
            return (
              <Pressable
                key={code}
                onPress={() => set({ bodyType: on ? null : code })}
                accessibilityRole="button"
                accessibilityState={{ selected: on }}
                style={[s.card, on && s.cardOn]}
              >
                <Row style={{ justifyContent: 'space-between' }}>
                  <Text style={s.cardTitle}>{b.label}</Text>
                  {on && <Icon name="check" size={18} color={c.primary} strokeWidth={2.4} />}
                </Row>
                {b.description && <Text style={s.cardDesc}>{b.description}</Text>}
                {b.hint && <Text style={s.cardHint}>· {b.hint}</Text>}
              </Pressable>
            );
          })}
        </View>
      </View>

      <View>
        <Label>키</Label>
        <Row style={{ gap: 8 }}>
          <TextInput
            value={height}
            onChangeText={onHeight}
            keyboardType="number-pad"
            maxLength={3}
            placeholder="예: 165"
            placeholderTextColor={c.textMuted}
            style={[s.input, { flex: 1 }, heightError && { borderColor: c.danger }]}
            accessibilityLabel="키 (cm)"
          />
          <Text style={s.unit}>cm</Text>
        </Row>
        <Text style={[s.help, { marginTop: 6 }, heightError && { color: c.danger }]}>
          {heightError ? `${HEIGHT_MIN}~${HEIGHT_MAX} 사이 숫자로 입력해 주세요` : '비워 두면 키는 빼고 추천해요'}
        </Text>
      </View>

      <View>
        <Label>핏 선호</Label>
        <ChipGroup
          options={withCurrent(activeCodes('FIT'), info.fitPreference ?? undefined)}
          labels={(v) => codeLabel('FIT', v)}
          value={info.fitPreference ?? undefined}
          allowEmpty
          onChange={([v]) => set({ fitPreference: v ?? null })}
        />
      </View>
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  notice: { fontSize: 13, color: t.colors.textSub, lineHeight: 19 },
  help: { fontSize: 12, color: t.colors.textSub, lineHeight: 18, marginBottom: 10 },
  card: {
    borderWidth: 1,
    borderColor: t.colors.chipBorder,
    backgroundColor: t.colors.surface,
    borderRadius: t.radius.lg,
    paddingVertical: 12,
    paddingHorizontal: 14,
  },
  cardOn: { borderColor: t.colors.primary, backgroundColor: t.colors.primarySoft },
  cardTitle: { fontSize: 15, fontWeight: '700', color: t.colors.text },
  cardDesc: { fontSize: 13, color: t.colors.text, marginTop: 4, lineHeight: 19 },
  cardHint: { fontSize: 12, color: t.colors.textSub, marginTop: 3, lineHeight: 18 },
  input: inputBase(t),
  unit: { fontSize: 15, color: t.colors.textSub, fontWeight: '600' },
}));
