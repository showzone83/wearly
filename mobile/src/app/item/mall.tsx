import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import { router, useLocalSearchParams } from 'expo-router';
import { type RefObject, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  type GestureResponderHandlers,
  type LayoutChangeEvent,
  PanResponder,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { captureRef } from 'react-native-view-shot';
import { WebView } from 'react-native-webview';

import { Icon } from '@/components/Icon';
import { Button, Header, notify, Screen } from '@/components/ui';
import { availableMalls } from '@/lib/mallCapture';
import {
  clampFrame,
  cropRect,
  frameRect,
  type IMall,
  type IRect,
  isAllowedNavigation,
  type ISize,
  MALL_EMPTY_TEXT,
  MALL_GUIDE_TEXT,
  MALL_NOTICE_TEXT,
  moveFrame,
  resizeFrame,
  type TFrameCorner,
} from '@/lib/mallCaptureRules';
import { useCodesVersion } from '@/store/useCodes';
import { useMallCapture } from '@/store/useMallCapture';
import { cardBase, makeStyles, useColors } from '@/theme';

// 쇼핑몰에서 옷 사진 가져오기 (doc/24). 쇼핑몰 목록은 코드 그룹 MALL(관리자 코드 관리)에서 온다.
// 고른 쇼핑몰을 앱 안 웹 화면으로 열고, 상품 사진을 정사각 틀에 맞춰 캡처하면 틀 안만 잘라 부른 화면에 넘긴다.
// 틀은 처음에 가운데에 있고, 모서리 손잡이로 크기·가운데 손잡이로 위치를 바꾼다(정사각 유지).
// 틀·손잡이는 캡처하는 뷰(shotRef) 밖에 겹쳐 그려서 사진에 찍히지 않는다. 손잡이 밖 터치는 웹 화면으로 간다.
export default function MallCapture() {
  const { for: target = 'new' } = useLocalSearchParams<{ for?: string }>();
  const shotRef = useRef<View>(null);
  const webRef = useRef<WebView>(null);
  const [mall, setMall] = useState<IMall | null>(null);
  const [view, setView] = useState<ISize>({ width: 0, height: 0 });
  /** 사용자가 바꾼 틀. null이면 처음 틀(가운데) */
  const [userFrame, setUserFrame] = useState<IRect | null>(null);
  const [canGoBack, setCanGoBack] = useState(false);
  const [loading, setLoading] = useState(true);
  const [capturing, setCapturing] = useState(false);
  const s = useStyles();
  const c = useColors();
  // 코드가 갱신되면 쇼핑몰 목록도 다시 그린다
  const codesVersion = useCodesVersion();

  const malls = useMemo(() => availableMalls(), [codesVersion]);
  const frame = useMemo(() => (userFrame ? clampFrame(userFrame, view) : frameRect(view)), [userFrame, view]);
  // 손잡이 제스처는 한 번만 만들고 시작할 때 최신 틀·화면 크기를 여기서 읽는다
  const latest = useRef({ frame, view });
  latest.current = { frame, view };
  const dragTL = useFrameDrag('tl', latest, setUserFrame);
  const dragTR = useFrameDrag('tr', latest, setUserFrame);
  const dragBL = useFrameDrag('bl', latest, setUserFrame);
  const dragBR = useFrameDrag('br', latest, setUserFrame);
  const dragMove = useFrameDrag('move', latest, setUserFrame);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setView({ width, height });
  };

  const open = (m: IMall) => {
    setMall(m);
    setUserFrame(null);
    setCanGoBack(false);
    setLoading(true);
  };

  /** 머리 왼쪽: 웹 화면에서 뒤로 갈 곳이 있으면 웹 뒤로, 없으면 쇼핑몰 목록으로 */
  const back = () => {
    if (mall && canGoBack) webRef.current?.goBack();
    else if (mall) setMall(null);
    else router.back();
  };

  const capture = async () => {
    if (!mall || capturing) return;
    setCapturing(true);
    try {
      const shot = await captureRef(shotRef, { format: 'png', quality: 1, result: 'tmpfile' });
      const image = await ImageManipulator.manipulate(shot).renderAsync();
      const rect = cropRect(frame, view, { width: image.width, height: image.height });
      if (!rect) throw new Error('캡처 크기를 알 수 없어요');
      const cropped = await ImageManipulator.manipulate(image)
        .crop({ originX: rect.x, originY: rect.y, width: rect.width, height: rect.height })
        .renderAsync();
      const saved = await cropped.saveAsync({ format: SaveFormat.PNG });
      useMallCapture.getState().put({
        target,
        uri: saved.uri,
        width: saved.width,
        height: saved.height,
        mallCode: mall.code,
      });
      router.back();
    } catch (e) {
      console.warn('[mall] 캡처 실패', e);
      notify('캡처하지 못했어요', '잠시 후 다시 시도하거나 카메라·앨범으로 등록해 주세요.');
    } finally {
      setCapturing(false);
    }
  };

  if (!mall) {
    return (
      <Screen header={<Header title="쇼핑몰에서 가져오기" onBack={back} />}>
        <Text style={s.desc}>
          쇼핑몰에서 내 옷 상품 사진을 찾아 캡처하면 그 사진으로 등록해요. AI가 사진을 보고 종류와 색을 알아서 채워요.
        </Text>
        {malls.length === 0 ? (
          <Text style={s.empty}>{MALL_EMPTY_TEXT}</Text>
        ) : (
          <View style={s.card}>
            {malls.map((m, i) => (
              <Pressable
                key={m.code}
                onPress={() => open(m)}
                style={({ pressed }) => [
                  s.row,
                  i === malls.length - 1 && { borderBottomWidth: 0 },
                  pressed && s.pressed,
                ]}
                accessibilityRole="button"
              >
                <Text style={s.label}>{m.label}</Text>
                <Icon name="chevron" size={18} color={c.textMuted} />
              </Pressable>
            ))}
          </View>
        )}
        <Text style={s.notice}>{MALL_NOTICE_TEXT}</Text>
      </Screen>
    );
  }

  return (
    <Screen
      scroll={false}
      contentStyle={s.webContent}
      header={
        <Header
          title={mall.label}
          onBack={back}
          right={
            <Pressable hitSlop={12} onPress={() => router.back()} accessibilityLabel="닫기">
              <Icon name="close" />
            </Pressable>
          }
        />
      }
      footer={
        <View style={{ gap: 6 }}>
          <Text style={s.guide}>{MALL_GUIDE_TEXT}</Text>
          <Button
            title={capturing ? '' : '캡처'}
            icon={
              capturing ? (
                <ActivityIndicator color={c.onPrimary} />
              ) : (
                <Icon name="camera" size={18} color={c.onPrimary} />
              )
            }
            disabled={capturing || loading || frame.width <= 0}
            onPress={() => void capture()}
          />
          <Text style={s.noticeSmall}>{MALL_NOTICE_TEXT}</Text>
        </View>
      }
    >
      <View style={s.webBox} onLayout={onLayout}>
        <View ref={shotRef} collapsable={false} style={StyleSheet.absoluteFill}>
          <WebView
            ref={webRef}
            source={{ uri: mall.url }}
            style={s.web}
            onShouldStartLoadWithRequest={(req) => isAllowedNavigation(req.url)}
            setSupportMultipleWindows={false}
            onNavigationStateChange={(nav) => setCanGoBack(nav.canGoBack)}
            onLoadStart={() => setLoading(true)}
            onLoadEnd={() => setLoading(false)}
            allowsBackForwardNavigationGestures
          />
        </View>
        {/* 정사각 틀: 바깥은 어둡게. 캡처하는 뷰 밖이라 사진에 찍히지 않는다.
            덮개·테두리는 터치를 받지 않아 웹 화면이 그대로 스크롤되고, 손잡이만 터치를 받는다 */}
        {frame.width > 0 ? (
          <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
            <View pointerEvents="none" style={StyleSheet.absoluteFill}>
              <View style={[s.dim, { left: 0, right: 0, top: 0, height: frame.y }]} />
              <View style={[s.dim, { left: 0, right: 0, top: frame.y + frame.height, bottom: 0 }]} />
              <View style={[s.dim, { left: 0, width: frame.x, top: frame.y, height: frame.height }]} />
              <View style={[s.dim, { left: frame.x + frame.width, right: 0, top: frame.y, height: frame.height }]} />
              <View style={[s.frame, { left: frame.x, top: frame.y, width: frame.width, height: frame.height }]} />
            </View>
            <CornerHandle corner="tl" x={frame.x} y={frame.y} handlers={dragTL} />
            <CornerHandle corner="tr" x={frame.x + frame.width} y={frame.y} handlers={dragTR} />
            <CornerHandle corner="bl" x={frame.x} y={frame.y + frame.height} handlers={dragBL} />
            <CornerHandle corner="br" x={frame.x + frame.width} y={frame.y + frame.height} handlers={dragBR} />
            <View
              {...dragMove}
              accessibilityLabel="틀 옮기기"
              style={[s.handle, { left: frame.x + frame.width / 2 - HANDLE / 2, top: frame.y + frame.height / 2 - HANDLE / 2 }]}
            >
              <View style={s.moveRing}>
                <View style={s.moveDot} />
              </View>
            </View>
          </View>
        ) : null}
        {loading ? (
          <View pointerEvents="none" style={s.loading}>
            <ActivityIndicator color={c.primary} />
          </View>
        ) : null}
      </View>
    </Screen>
  );
}

/** 손잡이 터치 영역 한 변 (논리 px) */
const HANDLE = 44;

const CORNER_LABEL: Record<TFrameCorner, string> = {
  tl: '왼쪽 위',
  tr: '오른쪽 위',
  bl: '왼쪽 아래',
  br: '오른쪽 아래',
};

/**
 * 틀 손잡이 끌기. 끌기 시작할 때의 틀에서 움직인 거리(dx, dy)만큼 크기(모서리) 또는 위치(move)를 바꾼다.
 * 끄는 동안에는 웹 화면이 터치를 가져가지 않는다.
 */
const useFrameDrag = (
  kind: TFrameCorner | 'move',
  latest: RefObject<{ frame: IRect; view: ISize }>,
  onChange: (frame: IRect) => void,
): GestureResponderHandlers => {
  const start = useRef<IRect | null>(null);
  const responder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        start.current = latest.current.frame;
      },
      onPanResponderMove: (_, g) => {
        const from = start.current;
        if (!from) return;
        const { view } = latest.current;
        onChange(kind === 'move' ? moveFrame(from, g.dx, g.dy, view) : resizeFrame(from, kind, g.dx, g.dy, view));
      },
      onPanResponderTerminationRequest: () => false,
      onPanResponderRelease: () => {
        start.current = null;
      },
      onPanResponderTerminate: () => {
        start.current = null;
      },
    }),
  );
  return responder.current.panHandlers;
};

/** 모서리 손잡이: (x, y)가 틀 모서리. 터치 영역은 모서리 가운데, 흰 꺾쇠를 그린다 */
function CornerHandle({
  corner,
  x,
  y,
  handlers,
}: {
  corner: TFrameCorner;
  x: number;
  y: number;
  handlers: GestureResponderHandlers;
}) {
  const s = useStyles();
  const edge = HANDLE / 2 - 2;
  const bracket = {
    ...(corner[0] === 't' ? { top: edge, borderTopWidth: 4 } : { bottom: edge, borderBottomWidth: 4 }),
    ...(corner[1] === 'l' ? { left: edge, borderLeftWidth: 4 } : { right: edge, borderRightWidth: 4 }),
  };
  return (
    <View
      {...handlers}
      accessibilityLabel={`틀 크기 조절(${CORNER_LABEL[corner]})`}
      style={[s.handle, { left: x - HANDLE / 2, top: y - HANDLE / 2 }]}
    >
      <View style={[s.bracket, bracket]} />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  desc: { fontSize: 13, color: t.colors.textSub, lineHeight: 19, marginTop: 8, marginBottom: 14 },
  empty: { fontSize: 13, color: t.colors.textMuted, marginTop: 20, textAlign: 'center' },
  card: { ...cardBase(t), paddingVertical: 4 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  pressed: { opacity: 0.7 },
  label: { fontSize: 15, fontWeight: '600', color: t.colors.text },
  notice: { fontSize: 12, color: t.colors.textMuted, marginTop: 16, textAlign: 'center' },
  webContent: { paddingHorizontal: 0, paddingTop: 0, paddingBottom: 0 },
  webBox: { flex: 1, overflow: 'hidden', backgroundColor: t.colors.surface },
  web: { flex: 1, backgroundColor: t.colors.surface },
  dim: { position: 'absolute', backgroundColor: 'rgba(0,0,0,0.35)' },
  frame: { position: 'absolute', borderWidth: 2, borderColor: '#FFFFFF', borderRadius: 4 },
  handle: { position: 'absolute', width: HANDLE, height: HANDLE, alignItems: 'center', justifyContent: 'center' },
  bracket: { position: 'absolute', width: 20, height: 20, borderColor: '#FFFFFF' },
  moveRing: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 2,
    borderColor: '#FFFFFF',
    backgroundColor: 'rgba(0,0,0,0.35)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  moveDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#FFFFFF' },
  loading: { position: 'absolute', top: 12, alignSelf: 'center' },
  guide: { fontSize: 13, fontWeight: '600', color: t.colors.text, textAlign: 'center' },
  noticeSmall: { fontSize: 11, color: t.colors.textMuted, textAlign: 'center' },
}));
