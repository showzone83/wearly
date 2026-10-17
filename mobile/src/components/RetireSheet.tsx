import { useEffect, useState } from 'react';

import { ChipGroup } from '@/components/Chip';
import { Icon } from '@/components/Icon';
import { Button, Label } from '@/components/ui';

import { useCodesVersion } from '@/store/useCodes';

import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { RETIRE_MEMO_MAX, retireReasonLabel, retireReasonOptions } from '@/lib/retire';
import { inputBase, makeStyles, space, useColors } from '@/theme';

import type { WardrobeItem } from '@/data/types';

interface IRetireSheetProps {
  /** 시트를 띄울 옷. null이면 닫힘 */
  item: WardrobeItem | null;
  busy?: boolean;
  onClose: () => void;
  onSubmit: (reason: string, memo: string) => void;
}

/**
 * "사용하지 않는 옷으로" 시트 (doc/13 4장): 이유 칩(RETIRE_REASON) + 메모(선택) + 옮기기.
 * 이미 사용 중지한 옷이면 지금 이유·메모를 채워 두고 "바꾸기"로 보여 준다.
 */
export const RetireSheet = ({ item, busy, onClose, onSubmit }: IRetireSheetProps) => {
  const [reason, setReason] = useState<string | undefined>();
  const [memo, setMemo] = useState('');
  const s = useStyles();
  const c = useColors();
  const insets = useSafeAreaInsets();
  useCodesVersion(); // 코드가 갱신되면 이유 목록을 다시 그린다

  useEffect(() => {
    // 열 때마다 그 옷의 지금 값으로 채운다
    setReason(item?.retireReason);
    setMemo(item?.retireMemo ?? '');
  }, [item?.id, item?.retireReason, item?.retireMemo]);

  const already = !!item?.retiredAt;
  const options =
    reason && !retireReasonOptions().includes(reason) ? [...retireReasonOptions(), reason] : retireReasonOptions();

  return (
    <Modal visible={!!item} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={s.backdrop} onPress={busy ? undefined : onClose} accessibilityLabel="닫기" />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={[s.sheet, { paddingBottom: Math.max(insets.bottom, 16) }]}>
          <View style={s.grip} />
          <View style={s.head}>
            <Text style={s.title}>{already ? '사용하지 않는 이유' : '사용하지 않는 옷으로'}</Text>
            <Pressable onPress={onClose} disabled={busy} hitSlop={10} accessibilityLabel="닫기">
              <Icon name="close" />
            </Pressable>
          </View>
          <Text style={s.sub}>옷장과 추천에서는 빠지고, 착용 기록은 그대로 남아요.</Text>

          <Label style={s.label}>이유</Label>
          <ChipGroup
            options={options}
            labels={retireReasonLabel}
            value={reason}
            small
            onChange={([r]) => setReason(r)}
          />

          <Label style={[s.label, { marginTop: 18 }]}>
            메모 <Text style={{ color: c.textMuted, fontWeight: '400' }}>(선택)</Text>
          </Label>
          <TextInput
            value={memo}
            onChangeText={setMemo}
            placeholder="예: 아름다운가게 기부"
            placeholderTextColor={c.textMuted}
            maxLength={RETIRE_MEMO_MAX}
            style={s.input}
            returnKeyType="done"
          />

          <Button
            title={busy ? '' : already ? '바꾸기' : '옮기기'}
            icon={busy ? <ActivityIndicator color={c.onPrimary} /> : undefined}
            disabled={!reason || busy}
            onPress={() => reason && onSubmit(reason, memo)}
            style={{ marginTop: 22 }}
          />
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const useStyles = makeStyles((t) => ({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: {
    backgroundColor: t.colors.bg,
    borderTopLeftRadius: t.radius.xl,
    borderTopRightRadius: t.radius.xl,
    paddingHorizontal: space.gutter,
    paddingTop: 8,
  },
  grip: {
    alignSelf: 'center',
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: t.colors.border,
    marginBottom: 10,
  },
  head: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { fontSize: 18, fontWeight: '800', color: t.colors.text },
  sub: { fontSize: 13, color: t.colors.textSub, marginTop: 6, marginBottom: 18, lineHeight: 19 },
  label: { marginBottom: 10 },
  input: { ...inputBase(t) },
}));
