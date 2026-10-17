// 냥깨비: 세이지 초록 고양이 도깨비 (mobile/design/냥깨비 보드). 머리 위 주황 뿔 두 개, 고양이 귀,
// 크림색 주둥이, 분홍 볼, 큰 검은 눈, 송곳니가 살짝 보이는 웃는 입, 긴 꼬리.
// 머리는 nyangHead 하나를 모든 자세가 같이 써서 같은 얼굴로 보이게 한다. 순수 TS.

import {
  VIEW_BOX,
  camera,
  circle,
  ellipse,
  foldedStack,
  heart,
  heartBubble,
  line,
  mirrorX,
  path,
  place,
  shadow,
  sparkle,
  wardrobe,
} from './draw';
import type { IMascotArt, IMascotShape, TMascotPose } from './types';

type TShape = IMascotShape;

const C = {
  body: '#7FA587',
  bodyDark: '#628C6E',
  line: '#3F6249',
  cream: '#EDEACB',
  horn: '#F4B03E',
  hornDark: '#D98E25',
  hornLight: '#FCD98C',
  ear: '#F2A08F',
  cheek: '#F59886',
  eye: '#1E2721',
  mouth: '#B5463C',
  tongue: '#F38B7A',
  white: '#FFFFFF',
  accent: '#F4A03A',
  gold: '#F6BE4F',
};

type TFace = 'smile' | 'joy' | 'wink' | 'surprised';

// ── 머리 (원점 기준, 폭 약 104) ─────────────────────────

const HEAD = 'M -52 6 C -52 -26 -28 -42 0 -42 C 28 -42 52 -26 52 6 C 52 30 30 42 0 42 C -30 42 -52 30 -52 6 Z';
const EAR = 'M -51 -2 C -57 -20 -58 -38 -54 -51 C -44 -49 -34 -43 -26 -35 Z';
const EAR_IN = 'M -47 -16 C -51 -28 -52 -38 -50 -45 C -44 -43 -38 -39 -33 -34 Z';
const HORN = 'M -21 -36 C -21 -47 -18 -54 -12 -60 C -7 -53 -5 -46 -6 -38 Z';
const HORN_HI = 'M -16 -45 C -15 -50 -14 -53 -12 -56';
const MASK =
  'M -43 16 C -45 -2 -35 -12 -21 -11 C -12 -10 -6 -5 0 -3 C 6 -5 12 -10 21 -11 C 35 -12 45 -2 43 16 C 41 33 22 41 0 41 C -22 41 -41 33 -43 16 Z';

const openEye = (x: number, big = false): TShape[] => [
  ellipse(x, 6, big ? 7.6 : 6.8, big ? 8.8 : 7.8, C.eye),
  circle(x + 2.2, 2.6, big ? 3 : 2.7, C.white),
  circle(x - 2.4, 9.6, 1.2, C.white),
];

const closedEye = (x: number): TShape => line(`M ${x - 7} 8 Q ${x} -1 ${x + 7} 8`, C.eye, 3.2);

const nose: TShape = path('M -3.4 14.2 Q 0 12.6 3.4 14.2 Q 0 18.6 -3.4 14.2 Z', C.eye);

const openMouth = (big: boolean): TShape[] => {
  const depth = big ? 36 : 32;
  return [
    path(`M -8.5 19 Q 0 ${depth} 8.5 19 Q 4 21.5 0 18 Q -4 21.5 -8.5 19 Z`, C.mouth),
    path(`M -4.5 ${depth - 7} Q 0 ${depth - 10.5} 4.5 ${depth - 7} Q 0 ${depth - 3.5} -4.5 ${depth - 7} Z`, C.tongue),
    path('M -6.6 20.2 L -3.6 20.4 L -5.2 23.8 Z', C.white),
    line('M -9 19 Q -4.5 22 0 18 Q 4.5 22 9 19', C.eye, 1.8),
  ];
};

const nyangHead = (face: TFace): TShape[] => {
  const shapes: TShape[] = [
    // 뿔과 귀 (머리 뒤)
    path(HORN, C.horn, { stroke: C.hornDark, strokeWidth: 1.6, strokeLinejoin: 'round' }),
    path(mirrorX(HORN), C.horn, { stroke: C.hornDark, strokeWidth: 1.6, strokeLinejoin: 'round' }),
    line(HORN_HI, C.hornLight, 2.6),
    line(mirrorX(HORN_HI), C.hornLight, 2.6),
    path(EAR, C.body, { stroke: C.line, strokeWidth: 2, strokeLinejoin: 'round' }),
    path(mirrorX(EAR), C.body, { stroke: C.line, strokeWidth: 2, strokeLinejoin: 'round' }),
    path(EAR_IN, C.ear),
    path(mirrorX(EAR_IN), C.ear),
    // 머리, 크림색 얼굴, 이마 줄무늬, 외곽선
    path(HEAD, C.body),
    path(MASK, C.cream),
    line('M -7 -36 L -5 -29', C.bodyDark, 2.4),
    line('M 0 -38 L 0 -30', C.bodyDark, 2.4),
    line('M 7 -36 L 5 -29', C.bodyDark, 2.4),
    path(HEAD, undefined, { stroke: C.line, strokeWidth: 2 }),
    // 볼
    ellipse(-31, 17, 8.5, 5.8, C.cheek, { opacity: 0.85 }),
    ellipse(31, 17, 8.5, 5.8, C.cheek, { opacity: 0.85 }),
  ];
  if (face === 'smile') shapes.push(...openEye(-18), ...openEye(18), nose, ...openMouth(false));
  if (face === 'joy') shapes.push(closedEye(-18), closedEye(18), nose, ...openMouth(true));
  if (face === 'wink') shapes.push(...openEye(-18), closedEye(18), nose, ...openMouth(false));
  if (face === 'surprised')
    shapes.push(
      line('M -25 -9 Q -19 -13 -13 -10', C.line, 2.2),
      line('M 25 -9 Q 19 -13 13 -10', C.line, 2.2),
      ...openEye(-18, true),
      ...openEye(18, true),
      nose,
      ellipse(0, 25, 4.6, 5.6, C.mouth),
      ellipse(0, 27.4, 2.8, 2.4, C.tongue),
    );
  return shapes;
};

// ── 몸 부품 ───────────────────────────────────────────

const paw = (x: number, y: number, rx = 12, ry = 8.5, rotate = 0): TShape[] => {
  const t = rotate ? { transform: `rotate(${rotate} ${x} ${y})` } : {};
  return [
    ellipse(x, y, rx, ry, C.body, { stroke: C.line, strokeWidth: 2, ...t }),
    line(`M ${x - 4} ${y + ry - 0.5} L ${x - 4} ${y + ry - 4}`, C.line, 1.6, t),
    line(`M ${x + 4} ${y + ry - 0.5} L ${x + 4} ${y + ry - 4}`, C.line, 1.6, t),
  ];
};

/** 들어 올린 앞발 (분홍 발바닥이 보임) */
const raisedPaw = (x: number, y: number, r = 11): TShape[] => [
  circle(x, y, r, C.body, { stroke: C.line, strokeWidth: 2 }),
  ellipse(x, y + 2.5, r * 0.42, r * 0.34, C.ear),
  circle(x - r * 0.45, y - r * 0.35, r * 0.17, C.ear),
  circle(x, y - r * 0.55, r * 0.17, C.ear),
  circle(x + r * 0.45, y - r * 0.35, r * 0.17, C.ear),
];

/** 팔 (어깨 → 손목, 굵은 선 두 겹) */
const arm = (d: string, w = 16): TShape[] => [line(d, C.line, w + 4), line(d, C.body, w)];

/** 꼬리 (끝은 크림색) */
const tail = (d: string, tipX: number, tipY: number): TShape[] => [
  line(d, C.line, 13),
  line(d, C.body, 9),
  circle(tipX, tipY, 4.6, C.cream),
];

/** 흔드는 손 옆 움직임 선 */
const waveMarks = (x: number, y: number, flip = 1): TShape[] => [
  line(`M ${x} ${y - 10} Q ${x + 6 * flip} ${y - 4} ${x + 3 * flip} ${y + 4}`, C.bodyDark, 2.4),
  line(`M ${x + 8 * flip} ${y - 14} Q ${x + 16 * flip} ${y - 5} ${x + 12 * flip} ${y + 6}`, C.bodyDark, 2.4),
];

const body = (d: string): TShape => path(d, C.body, { stroke: C.line, strokeWidth: 2, strokeLinejoin: 'round' });

// ── 자세 ─────────────────────────────────────────────

const WARDROBE = {
  wood: '#EBC9A0',
  woodDark: '#C99C6C',
  woodLight: '#F2D7B3',
  inside: '#F8EAD6',
  clothes: ['#F6F0E6', '#BCD0E6', '#7FA3CB', '#EBCDA9', '#8E9C8A', '#D7C4E0'],
};

const art = (shapes: TShape[]): IMascotArt => ({ viewBox: VIEW_BOX, shapes });

/** 옷장 윗판 위로 빼꼼 */
const splash = art([
  sparkle(36, 46, 10, C.gold),
  sparkle(170, 36, 8, C.accent),
  sparkle(154, 18, 4, C.gold),
  ...tail('M 142 112 C 162 110 174 96 168 74', 168, 74),
  ...place(nyangHead('smile'), 100, 74, 0.98),
  ...wardrobe(40, 118, 120, 82, WARDROBE),
  ...paw(70, 110, 13, 9),
  ...paw(130, 110, 13, 9),
]);

/** 상반신, 손 흔들기 + 하트 */
const hello = art([
  ...heartBubble(22, 26, 15, C.accent),
  sparkle(172, 34, 8, C.gold),
  sparkle(186, 58, 4, C.accent),
  body('M 50 204 C 50 164 70 142 100 142 C 130 142 150 164 150 204 Z'),
  path('M 76 204 C 76 176 86 162 100 162 C 114 162 124 176 124 204 Z', C.cream),
  ...place(nyangHead('smile'), 102, 100, 1.14),
  ...arm('M 138 168 Q 164 158 166 124'),
  ...raisedPaw(166, 116, 12),
  ...waveMarks(182, 108),
  ...paw(70, 190, 12, 9, -10),
]);

/** 엎드려 웃음 + 하트 */
const style = art([
  shadow(106, 188, 74, C.line),
  ...heartBubble(164, 52, 18, C.accent),
  heart(184, 92, 6, C.accent, 15),
  sparkle(30, 58, 8, C.gold),
  ...tail('M 152 164 C 174 160 184 142 174 120', 174, 120),
  body('M 54 180 C 50 152 88 136 128 138 C 162 140 176 160 172 176 C 170 186 152 188 120 188 L 76 188 C 62 188 55 186 54 180 Z'),
  ...paw(164, 184, 10, 6),
  ...place(nyangHead('smile'), 86, 114, 1.12),
  ...paw(66, 178, 14, 9),
  ...paw(108, 180, 14, 9),
]);

/** 카메라를 든 모습 */
const cameraPose = art([
  sparkle(34, 56, 9, C.gold),
  sparkle(170, 46, 8, C.accent),
  sparkle(180, 76, 4, C.gold),
  body('M 54 204 C 54 168 74 150 100 150 C 126 150 146 168 146 204 Z'),
  ...place(nyangHead('joy'), 100, 92, 1.12),
  ...camera(100, 164, 70),
  ...paw(66, 168, 11, 13),
  ...paw(134, 168, 11, 13),
]);

/** 개어 둔 옷 더미 위에 앉아 놀람 */
const closet = art([
  shadow(100, 194, 76, C.line),
  ...foldedStack(194, [
    { color: '#F1E2CB', x: 26, w: 148, h: 17 },
    { color: '#8FAFD2', x: 32, w: 140, h: 15 },
    { color: '#F7F1E7', x: 28, w: 142, h: 14 },
    { color: '#E6C59F', x: 36, w: 128, h: 13 },
  ]),
  body('M 62 146 C 58 120 78 108 100 108 C 124 108 142 120 140 146 Z'),
  ...tail('M 136 140 C 158 140 170 152 166 172', 166, 172),
  ...place(nyangHead('surprised'), 98, 94, 1.1),
  ...paw(74, 144, 13, 8),
  ...paw(122, 144, 13, 8),
  line('M 160 40 L 168 30', C.accent, 3.4),
  line('M 168 56 L 180 52', C.accent, 3.4),
  line('M 148 32 L 150 20', C.accent, 3.4),
  sparkle(32, 70, 8, C.gold),
]);

/** 엎드려 손 흔들기 + 반짝이 */
const cheer = art([
  shadow(98, 188, 76, C.line),
  sparkle(36, 48, 11, C.gold),
  sparkle(168, 40, 8, C.accent),
  sparkle(186, 82, 5, C.gold),
  sparkle(20, 92, 5, C.accent),
  sparkle(146, 18, 4, C.gold),
  ...tail('M 46 166 C 24 162 16 142 26 122', 26, 122),
  body('M 30 178 C 28 156 52 140 86 138 C 124 136 150 152 150 174 C 150 186 136 188 112 188 L 48 188 C 36 188 31 186 30 178 Z'),
  ...paw(40, 184, 10, 6),
  ...place(nyangHead('wink'), 108, 112, 1.12),
  ...paw(92, 180, 14, 9),
  ...arm('M 138 176 Q 162 164 164 136'),
  ...raisedPaw(164, 128, 12),
  ...waveMarks(180, 120),
]);

export const NYANG: Record<TMascotPose, IMascotArt> = {
  splash,
  hello,
  style,
  camera: cameraPose,
  closet,
  cheer,
};
