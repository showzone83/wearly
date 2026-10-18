import { useEffect, useRef } from 'react';

import { Icon } from '@/components/Icon';

import { Modal, Platform, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { photoSheetOptions, photoSheetTitle, type TPhotoAction } from '@/lib/photoEditRules';
import { makeStyles, space, useColors } from '@/theme';

interface IPhotoSheetProps {
  visible: boolean;
  /** 지금 사진이 있는지 (있으면 '사진 빼기'가 보인다) */
  hasPhoto: boolean;
  onClose: () => void;
  /** 고른 선택지. 시트가 완전히 닫힌 뒤 부른다 (iOS는 모달이 닫히는 중에 카메라·앨범·확인 창을 띄우지 못한다) */
  onSelect: (action: TPhotoAction) => void;
}

const ICON: Record<TPhotoAction, 'camera' | 'image' | 'trash'> = { camera: 'camera', library: 'image', remove: 'trash' };

/** 옷 상세의 사진 시트 (이슈 #8): 카메라로 다시 찍기 · 앨범에서 고르기 · 사진 빼기(사진이 있을 때) · 취소 */
export const PhotoSheet = ({ visible, hasPhoto, onClose, onSelect }: IPhotoSheetProps) => {
  const chosen = useRef<TPhotoAction | null>(null);

  const s = useStyles();
  const c = useColors();
  const insets = useSafeAreaInsets();

  // iOS는 모달이 다 닫힌 뒤(onDismiss) 부른다. onDismiss가 오지 않는 경우를 위해 잠시 뒤에도 한 번 확인한다.
  // 그 밖의 플랫폼은 onDismiss가 없으므로 닫히면 바로 부른다
  useEffect(() => {
    if (visible || !chosen.current) return;
    if (Platform.OS !== 'ios') {
      fire();
      return;
    }
    const timer = setTimeout(fire, 700);
    return () => clearTimeout(timer);
    // fire는 ref만 읽으므로 visible만 본다
  }, [visible]);

  /** 고른 선택지를 한 번만 넘긴다 */
  const fire = () => {
    const action = chosen.current;
    if (!action) return;
    chosen.current = null;
    onSelect(action);
  };

  const choose = (action: TPhotoAction) => {
    chosen.current = action;
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose} onDismiss={fire}>
      <Pressable style={s.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <View style={[s.sheet, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <View style={s.grip} />
        <Text style={s.title}>{photoSheetTitle(hasPhoto)}</Text>
        {photoSheetOptions(hasPhoto).map((o) => (
          <Pressable
            key={o.action}
            style={({ pressed }) => [s.row, pressed && s.pressed]}
            onPress={() => choose(o.action)}
            accessibilityRole="button"
          >
            <Icon name={ICON[o.action]} size={20} color={o.danger ? c.danger : c.text} />
            <Text style={[s.label, o.danger && { color: c.danger }]}>{o.label}</Text>
          </Pressable>
        ))}
        <Pressable style={({ pressed }) => [s.cancel, pressed && s.pressed]} onPress={onClose} accessibilityRole="button">
          <Text style={s.cancelText}>취소</Text>
        </Pressable>
      </View>
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
  title: { fontSize: 18, fontWeight: '800', color: t.colors.text, marginBottom: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 15 },
  pressed: { opacity: 0.6 },
  label: { fontSize: 16, fontWeight: '600', color: t.colors.text },
  cancel: {
    marginTop: 8,
    paddingVertical: 14,
    alignItems: 'center',
    borderRadius: t.radius.md,
    backgroundColor: t.colors.surfaceAlt,
  },
  cancelText: { fontSize: 15, fontWeight: '700', color: t.colors.textSub },
}));
