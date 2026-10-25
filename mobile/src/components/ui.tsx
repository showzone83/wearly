import type { ReactNode } from 'react';
import {
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';

import * as Clipboard from 'expo-clipboard';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { traceLabelOf } from '@/lib/errorText';
import { useDialogStore } from '@/store/useDialogStore';
import { cardBase, makeStyles, space, useColors } from '@/theme';
import { Icon, type IconName } from './Icon';

// 보드(doc/12 2장)의 공통 디자인 언어: 크림 바탕, 짙은 초록 알약 버튼, 둥근 카드, 굵은 산세리프 제목

interface ITitleProps {
  style?: StyleProp<TextStyle>;
  children: ReactNode;
  numberOfLines?: number;
}

/** 굵은 산세리프 제목 (세리프 제목은 쓰지 않는다) */
export const Title = ({ style, children, numberOfLines }: ITitleProps) => {
  const s = useStyles();

  return (
    <Text style={[s.title, style]} numberOfLines={numberOfLines}>
      {children}
    </Text>
  );
};

interface IScreenProps {
  children: ReactNode;
  scroll?: boolean;
  /** 화면 하단 고정 영역 (버튼 등) */
  footer?: ReactNode;
  contentStyle?: StyleProp<ViewStyle>;
  /** 탭 화면은 상단 안전 영역을 직접 처리 */
  header?: ReactNode;
  /** 하단 고정 영역 위 구분선 */
  footerLine?: boolean;
}

export const Screen = ({ children, scroll = true, footer, contentStyle, header, footerLine = false }: IScreenProps) => {
  const s = useStyles();
  const insets = useSafeAreaInsets();

  return (
    <View style={[s.screen, { paddingTop: insets.top }]}>
      {header}
      {scroll ? (
        <ScrollView
          contentContainerStyle={[s.content, contentStyle]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          {children}
        </ScrollView>
      ) : (
        <View style={[s.content, { flex: 1 }, contentStyle]}>{children}</View>
      )}
      {footer && (
        <View style={[s.footer, footerLine && s.footerLine, { paddingBottom: Math.max(insets.bottom, 14) }]}>
          {footer}
        </View>
      )}
    </View>
  );
};

interface IHeaderProps {
  title?: string;
  right?: ReactNode;
  onBack?: () => void;
  /** 가운데에 제목 대신 넣을 것 (예: 단계 점) */
  center?: ReactNode;
  hideBack?: boolean;
}

export const Header = ({ title, right, onBack, center, hideBack }: IHeaderProps) => {
  const s = useStyles();

  return (
    <View style={s.header}>
      <View style={s.headerSide}>
        {!hideBack && (
          <Pressable
            hitSlop={12}
            onPress={onBack ?? (() => (router.canGoBack() ? router.back() : router.replace('/')))}
            accessibilityLabel="뒤로"
          >
            <Icon name="back" />
          </Pressable>
        )}
      </View>
      <View style={s.headerCenter}>
        {center ?? (
          <Text style={s.headerTitle} numberOfLines={1}>
            {title}
          </Text>
        )}
      </View>
      <View style={[s.headerSide, { alignItems: 'flex-end' }]}>{right}</View>
    </View>
  );
};

/** 탭 화면 맨 위의 큰 제목 줄. sub에 Text를 섞어 일부만 작게 쓸 수 있다 */
export const PageTitle = ({ title, sub, right }: { title: string; sub?: ReactNode; right?: ReactNode }) => {
  const s = useStyles();

  return (
    <View style={s.pageTitle}>
      <View style={{ flex: 1 }}>
        <Text style={s.pageTitleText}>{title}</Text>
        {sub ? <Text style={s.pageSub}>{sub}</Text> : null}
      </View>
      {right ? <View style={s.pageRight}>{right}</View> : null}
    </View>
  );
};

/** 단계 표시 점 (●○○): 지금 단계는 길쭉한 점 */
export const StepDots = ({ total, current }: { total: number; current: number }) => {
  const s = useStyles();

  return (
    <View style={s.dots} accessibilityLabel={`${total}단계 중 ${current}단계`}>
      {Array.from({ length: total }, (_, i) => (
        <View key={i} style={[s.dot, i + 1 === current && s.dotOn, i + 1 < current && s.dotDone]} />
      ))}
    </View>
  );
};

export const Label = ({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) => {
  const s = useStyles();

  return <Text style={[s.label, style]}>{children}</Text>;
};

/** 제목 위의 작은 글자 (예: STEP 1 / 2) */
export const Kicker = ({ children, color }: { children: ReactNode; color?: string }) => {
  const s = useStyles();

  return <Text style={[s.kicker, color ? { color } : null]}>{children}</Text>;
};

/** 섹션 제목 + 오른쪽 "더보기 ›" 같은 글자 버튼 */
export const SectionHead = ({
  title,
  action,
  onAction,
  style,
}: {
  title: string;
  action?: string;
  onAction?: () => void;
  style?: StyleProp<ViewStyle>;
}) => {
  const s = useStyles();
  const c = useColors();

  return (
    <View style={[s.sectionHead, style]}>
      <Text style={s.sectionTitle}>{title}</Text>
      {action && onAction ? (
        <Pressable onPress={onAction} hitSlop={8} style={s.sectionAction} accessibilityRole="button">
          <Text style={s.sectionActionText}>{action}</Text>
          <Icon name="chevron" size={13} color={c.textSub} />
        </Pressable>
      ) : null}
    </View>
  );
};

type TButtonVariant = 'primary' | 'green' | 'outline' | 'ghost' | 'soft' | 'danger';

interface IButtonProps {
  title: string;
  onPress: () => void;
  variant?: TButtonVariant;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  icon?: ReactNode;
  /** 글자 뒤에 붙는 아이콘 (예: 시작하기 →) */
  trailing?: ReactNode;
  small?: boolean;
}

export const Button = ({
  title,
  onPress,
  variant = 'primary',
  disabled,
  style,
  icon,
  trailing,
  small,
}: IButtonProps) => {
  const s = useStyles();
  const c = useColors();

  const filled = variant === 'primary' || variant === 'green';
  const bg = {
    primary: c.primary,
    green: c.primary,
    outline: c.surface,
    ghost: 'transparent',
    soft: c.primarySoft,
    danger: c.surface,
  }[variant];
  const fg = filled ? c.onPrimary : variant === 'danger' ? c.danger : c.primary;
  const border = variant === 'outline' ? c.primary : variant === 'danger' ? c.danger : 'transparent';

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={title || undefined}
      style={({ pressed }) => [
        s.button,
        small && s.buttonSmall,
        {
          backgroundColor: filled && pressed ? c.primaryPressed : bg,
          borderColor: border,
          opacity: disabled ? 0.4 : pressed && !filled ? 0.75 : 1,
        },
        style,
      ]}
    >
      {icon}
      {title ? <Text style={[s.buttonText, small && { fontSize: 14 }, { color: fg }]}>{title}</Text> : null}
      {trailing}
    </Pressable>
  );
};

/** 옷 이름 뒤에 붙이는 작은 "사용 안 함" 표시 (doc/13). 이름 Text 안에 넣는다 */
export const RetiredTag = () => {
  const s = useStyles();

  return <Text style={s.retiredTag}>{'  사용 안 함'}</Text>;
};

export const Card = ({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) => {
  const s = useStyles();

  return <View style={[s.card, style]}>{children}</View>;
};

type TBadgeTone = 'outline' | 'green' | 'dark' | 'beige';

export const Badge = ({ children, tone = 'outline' }: { children: ReactNode; tone?: TBadgeTone }) => {
  const s = useStyles();
  const c = useColors();

  const map = {
    outline: { bg: c.surface, fg: c.textSub, border: c.chipBorder },
    green: { bg: c.primarySoft, fg: c.primary, border: c.primarySoft },
    dark: { bg: c.primary, fg: c.onPrimary, border: c.primary },
    beige: { bg: c.accentSoft, fg: c.text, border: c.accentSoft },
  }[tone];

  return (
    <View style={[s.badge, { backgroundColor: map.bg, borderColor: map.border }]}>
      <Text style={[s.badgeText, { color: map.fg }]}>{children}</Text>
    </View>
  );
};

/** 둥근 아이콘 배경 (메뉴·통계 카드) */
export const IconBubble = ({
  name,
  size = 40,
  tint,
  bg,
}: {
  name: IconName;
  size?: number;
  tint?: string;
  bg?: string;
}) => {
  const c = useColors();

  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: bg ?? c.primarySoft,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon name={name} size={size * 0.52} color={tint ?? c.primary} />
    </View>
  );
};

export const Stars = ({
  value,
  size = 16,
  onChange,
}: {
  value: number;
  size?: number;
  onChange?: (v: number) => void;
}) => {
  const c = useColors();

  return (
    <View style={{ flexDirection: 'row', gap: 2 }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable key={n} disabled={!onChange} onPress={() => onChange?.(n)} hitSlop={4} accessibilityLabel={`${n}점`}>
          <Icon name="star" size={size} color={c.accent} filled={n <= value} />
        </Pressable>
      ))}
    </View>
  );
};

export const Divider = ({ style }: { style?: StyleProp<ViewStyle> }) => {
  const c = useColors();

  return <View style={[{ height: 1, backgroundColor: c.border }, style]} />;
};

export const Row = ({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) => (
  <View style={[{ flexDirection: 'row', alignItems: 'center' }, style]}>{children}</View>
);

/** 연한 바탕의 안내 상자 (팁·주의) */
export const Notice = ({
  children,
  tone = 'green',
  icon,
  style,
}: {
  children: ReactNode;
  tone?: 'green' | 'beige' | 'plain';
  icon?: IconName;
  style?: StyleProp<ViewStyle>;
}) => {
  const s = useStyles();
  const c = useColors();

  const bg = { green: c.primarySoft, beige: c.accentSoft, plain: c.surfaceAlt }[tone];

  return (
    <View style={[s.notice, { backgroundColor: bg }, style]}>
      {icon && <Icon name={icon} size={18} color={c.primary} />}
      <View style={{ flex: 1 }}>{children}</View>
    </View>
  );
};

/** 확인 대화상자 (앱이 직접 그린다, DialogHost). 취소·확인 두 버튼이고 확인은 위험한 동작 색이다 (danger=false면 기본 색) */
export const confirm = (title: string, message: string, onOk: () => void, okLabel = '확인', danger = true) => {
  useDialogStore.getState().show({ kind: 'confirm', title, message, okLabel, onOk, danger });
};

/** 알림 대화상자 (앱이 직접 그린다, DialogHost). 확인 버튼 하나 */
export const notify = (title: string, message?: string) => {
  useDialogStore.getState().show({ kind: 'notify', title, message, okLabel: '확인' });
};

/**
 * notify·confirm을 그리는 곳. 루트 레이아웃에 하나만 둔다. 시스템 Alert·브라우저 alert/confirm 대신 쓰므로
 * 웹·iOS·안드로이드 모두 테마 색의 같은 모양이다. 여러 개가 오면 차례로 하나씩 보인다.
 * 뒤로 가기·바깥 누르기는 취소(알림은 닫기)로 본다.
 */
export const DialogHost = () => {
  const dialog = useDialogStore((st) => st.queue[0]);
  const close = useDialogStore((st) => st.close);
  const s = useStyles();

  if (!dialog) return null;

  const cancel = () => close(dialog.id, false);
  const isConfirm = dialog.kind === 'confirm';

  return (
    <Modal visible transparent animationType="fade" onRequestClose={cancel} statusBarTranslucent>
      <View style={s.dialogBackdrop}>
        <Pressable style={StyleSheet.absoluteFill} onPress={cancel} accessibilityLabel="닫기" />
        <View style={s.dialogCard} accessibilityViewIsModal>
          <Text style={s.dialogTitle} accessibilityRole="header">
            {dialog.title}
          </Text>
          {dialog.message ? (
            <ScrollView style={s.dialogBody} contentContainerStyle={{ paddingBottom: 2 }}>
              <Text style={s.dialogMessage}>{dialog.message}</Text>
            </ScrollView>
          ) : null}
          <View style={s.dialogActions}>
            {isConfirm && <Button title="취소" variant="outline" small onPress={cancel} style={s.dialogButton} />}
            <Button
              title={dialog.okLabel}
              variant={isConfirm && dialog.danger !== false ? 'danger' : 'primary'}
              small
              onPress={() => close(dialog.id, true)}
              style={s.dialogButton}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
};

/**
 * 시스템 오류의 오류 번호 ("오류 번호 ab12cd34", traceId 앞 8자). 오류 문구 아래에 작게 둔다.
 * 길게 누르면 전체 traceId를 복사한다 (문의할 때 서버 로그에서 찾는 값). traceId가 없으면 그리지 않는다.
 */
export const TraceLabel = ({ traceId, style }: { traceId?: string | null; style?: StyleProp<TextStyle> }) => {
  const s = useStyles();

  if (!traceId) return null;

  const copy = async () => {
    try {
      await Clipboard.setStringAsync(traceId);
      notify('오류 번호를 복사했어요', `문의할 때 붙여 넣어 주세요.\n${traceId}`);
    } catch (e) {
      console.warn('[ui] 오류 번호 복사 실패', e);
      notify('오류 번호', traceId);
    }
  };

  return (
    <Text
      style={[s.trace, style]}
      onLongPress={() => void copy()}
      selectable={Platform.OS === 'web'}
      accessibilityHint="길게 누르면 오류 번호 전체를 복사해요"
    >
      {traceLabelOf(traceId)}
    </Text>
  );
};

const useStyles = makeStyles((t) => ({
  title: { color: t.colors.text, fontWeight: '800', letterSpacing: -0.4 },
  screen: { flex: 1, backgroundColor: t.colors.bg },
  content: { paddingHorizontal: space.gutter, paddingBottom: 40 },
  footer: {
    paddingHorizontal: space.gutter,
    paddingTop: 12,
    backgroundColor: t.colors.bg,
  },
  footerLine: { borderTopWidth: 1, borderTopColor: t.colors.border },
  header: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: space.gutter,
  },
  headerSide: { width: 72 },
  headerCenter: { flex: 1, alignItems: 'center' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: t.colors.text },
  pageTitle: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: space.gutter,
    paddingTop: 14,
    paddingBottom: 12,
  },
  pageTitleText: { fontSize: 26, fontWeight: '800', color: t.colors.text, letterSpacing: -0.5 },
  pageSub: { fontSize: 13, color: t.colors.textSub, marginTop: 6 },
  pageRight: { flexDirection: 'row', alignItems: 'center', gap: 16, paddingTop: 6 },
  dots: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: t.colors.border },
  dotOn: { width: 20, backgroundColor: t.colors.primary },
  dotDone: { backgroundColor: t.colors.primary },
  label: { fontSize: 15, color: t.colors.text, fontWeight: '700', marginBottom: 12 },
  kicker: { fontSize: 13, fontWeight: '700', letterSpacing: 0.4, color: t.colors.textSub },
  sectionHead: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 26,
    marginBottom: 12,
  },
  sectionTitle: { fontSize: 17, fontWeight: '800', color: t.colors.text },
  sectionAction: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  sectionActionText: { fontSize: 13, color: t.colors.textSub, fontWeight: '600' },
  button: {
    height: 52,
    borderRadius: t.radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 20,
    borderWidth: 1,
  },
  buttonSmall: { height: 42 },
  buttonText: { fontSize: 16, fontWeight: '700' },
  card: { ...cardBase(t), padding: 16 },
  badge: {
    borderWidth: 1,
    borderRadius: t.radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 4,
    alignSelf: 'flex-start',
  },
  badgeText: { fontSize: 11, fontWeight: '700' },
  retiredTag: { fontSize: 11, fontWeight: '600', color: t.colors.textMuted },
  notice: { flexDirection: 'row', gap: 10, padding: 14, borderRadius: t.radius.md, alignItems: 'flex-start' },
  trace: { fontSize: 11, color: t.colors.textMuted, marginTop: 6, letterSpacing: 0.2 },
  dialogBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  dialogCard: {
    width: '100%',
    maxWidth: 360,
    backgroundColor: t.colors.surface,
    borderRadius: t.radius.lg,
    paddingHorizontal: 22,
    paddingTop: 22,
    paddingBottom: 18,
    gap: 10,
  },
  dialogTitle: { fontSize: 17, fontWeight: '800', color: t.colors.text, letterSpacing: -0.3 },
  dialogBody: { maxHeight: 320 },
  dialogMessage: { fontSize: 14, lineHeight: 21, color: t.colors.textSub },
  dialogActions: { flexDirection: 'row', gap: 8, marginTop: 8 },
  dialogButton: { flex: 1 },
}));
