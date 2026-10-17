// Closeta: 둥근 하얀 떡 같은 몸, 짙은 초록 비니, 초록 크로스백, 점 눈과 볼터치, 작은 팔
// (mobile/design/파스텔 클로제타 보드). 몸 조각은 silhouette으로 이어 붙여 한 덩어리처럼 그리고,
// 모자·얼굴은 모든 자세가 같은 함수를 써서 같은 캐릭터로 보이게 한다. 순수 TS.

import {
  VIEW_BOX,
  camera,
  circle,
  ellipse,
  foldedStack,
  heart,
  heartBubble,
  line,
  path,
  place,
  shadow,
  silhouette,
  sparkle,
  wardrobe,
} from './draw';
import type { IMascotArt, IMascotShape, TMascotPose } from './types';

type TShape = IMascotShape;

const C = {
  body: '#FFFDF9',
  shade: '#F1EBE2',
  line: '#D5C9B8',
  hat: '#4F8F6B',
  hatDark: '#3B7556',
  hatLight: '#79B391',
  bag: '#3F7352',
  bagDark: '#2E5A40',
  eye: '#2B2A28',
  cheek: '#F6AFA8',
  mouth: '#4A3532',
  tongue: '#F08C86',
  white: '#FFFFFF',
  accent: '#E9B949',
  heart: '#F2A0A0',
};

type TFace = 'smile' | 'joy' | 'wink' | 'surprised';

// ── 머리 (원점 기준, 폭 84) ─────────────────────────────

const HEAD = 'M -42 -6 C -42 -40 -22 -56 0 -56 C 22 -56 42 -40 42 -6 C 42 18 22 28 0 28 C -22 28 -42 18 -42 -6 Z';

/** 비니: 접힌 단, 꼭지 방울, 옆으로 늘어진 끝 */
const beanie = (): TShape[] => [
  path('M 28 -55 C 44 -55 53 -43 51 -27 C 50 -21 43 -22 43 -28 C 43 -37 39 -43 27 -46 Z', C.hat),
  circle(0, -67, 7.5, C.hat),
  circle(-2.5, -69.5, 2.4, C.hatLight, { opacity: 0.8 }),
  path('M -41 -24 C -44 -52 -24 -66 0 -66 C 24 -66 44 -52 41 -24 C 28 -31 -28 -31 -41 -24 Z', C.hat),
  path('M -30 -50 C -24 -58 -14 -62 -6 -62', undefined, { stroke: C.hatLight, strokeWidth: 3, strokeLinecap: 'round', opacity: 0.7 }),
  line('M -40 -26 Q 0 -38 40 -26', C.hatDark, 8),
  ...[-28, -14, 0, 14, 28].map((x) => line(`M ${x} ${-33 + Math.abs(x) * 0.12} L ${x} ${-27 + Math.abs(x) * 0.12}`, C.hatLight, 1.6, { opacity: 0.55 })),
];

const openEye = (x: number, big = false): TShape[] => [
  ellipse(x, -9, big ? 6 : 5, big ? 7.2 : 6.2, C.eye),
  circle(x + 1.6, -11.6, big ? 2.2 : 1.9, C.white),
];

const closedEye = (x: number): TShape => line(`M ${x - 5.5} -8 Q ${x} -15 ${x + 5.5} -8`, C.eye, 2.8);

const smileMouth = (big: boolean): TShape[] => {
  const d = big ? 13 : 10;
  return [
    path(`M -7 2 Q 0 ${d + 1} 7 2 Q 0 4.5 -7 2 Z`, C.mouth),
    path(`M -3.5 ${d - 2.5} Q 0 ${d - 5} 3.5 ${d - 2.5} Q 0 ${d - 0.5} -3.5 ${d - 2.5} Z`, C.tongue),
  ];
};

const closetaFace = (face: TFace): TShape[] => {
  const shapes: TShape[] = [ellipse(-25, 1, 7.5, 4.8, C.cheek, { opacity: 0.85 }), ellipse(25, 1, 7.5, 4.8, C.cheek, { opacity: 0.85 })];
  if (face === 'smile') shapes.push(...openEye(-14), ...openEye(14), ...smileMouth(false));
  if (face === 'joy') shapes.push(closedEye(-14), closedEye(14), ...smileMouth(true));
  if (face === 'wink') shapes.push(...openEye(-14), closedEye(14), ...smileMouth(false));
  if (face === 'surprised')
    shapes.push(
      line('M -20 -22 Q -15 -25 -10 -23', C.eye, 2),
      line('M 20 -22 Q 15 -25 10 -23', C.eye, 2),
      ...openEye(-14, true),
      ...openEye(14, true),
      ellipse(0, 6, 4, 5, C.mouth),
      ellipse(0, 8.2, 2.4, 2, C.tongue),
    );
  return shapes;
};

// ── 몸 부품 ───────────────────────────────────────────

const arm = (x: number, y: number, rotate: number, rx = 8.5, ry = 12.5): TShape =>
  ellipse(x, y, rx, ry, C.body, { stroke: C.line, strokeWidth: 2, transform: `rotate(${rotate} ${x} ${y})` });

/** 크로스백 (왼쪽 어깨 → 오른쪽 옆구리) */
const bag = (): TShape[] => [
  line('M -38 4 Q -2 22 30 28', C.bagDark, 4.2),
  path('M 22 24 L 48 24 Q 52 24 52 28 L 50 48 Q 50 52 46 52 L 24 52 Q 20 52 20 48 L 18 28 Q 18 24 22 24 Z', C.bag),
  path('M 18 28 Q 18 24 22 24 L 48 24 Q 52 24 52 28 L 52 34 Q 35 41 18 34 Z', C.bagDark),
  circle(35, 37, 2.4, C.hatLight),
];

interface IStand {
  face: TFace;
  /** 흔드는 팔 쪽 (없으면 두 팔 내림) */
  wave?: 'left' | 'right';
  /** 팔을 따로 그릴 때 (카메라 들기) */
  noArms?: boolean;
}

/** 서 있는 Closeta (원점이 얼굴 가운데쯤, 모자 꼭지 y≈-75, 발 y≈69) */
const standing = ({ face, wave, noArms }: IStand): TShape[] => {
  const parts: TShape[] = [
    ellipse(-18, 62, 12, 7, C.body),
    ellipse(18, 62, 12, 7, C.body),
    path('M -40 12 C -50 32 -46 62 0 62 C 46 62 50 32 40 12 C 30 0 -30 0 -40 12 Z', C.body),
    path(HEAD, C.body),
  ];
  if (!noArms) {
    parts.push(wave === 'left' ? arm(-49, -10, -35) : arm(-45, 22, 22));
    parts.push(wave === 'right' ? arm(49, -10, 35) : arm(45, 22, -22));
  }
  return [
    ...silhouette(parts, C.line, 1.6),
    path('M 34 46 C 42 38 44 26 42 16 C 50 34 46 56 16 61 C 24 58 30 52 34 46 Z', C.shade),
    ...bag(),
    ...beanie(),
    ...closetaFace(face),
  ];
};

/** 엎드린 Closeta. 머리 위치 (hx, hy), 몸은 dir 쪽(1: 오른쪽, -1: 왼쪽)으로 눕는다 */
const lying = (hx: number, hy: number, s: number, dir: 1 | -1, face: TFace, extra: TShape[] = []): TShape[] => {
  const X = (dx: number) => hx + dx * dir;
  const bodyD =
    `M ${X(-30)} ${hy + 30} C ${X(-12)} ${hy + 4} ${X(40)} ${hy - 2} ${X(76)} ${hy + 12} ` +
    `C ${X(100)} ${hy + 22} ${X(100)} ${hy + 58} ${X(76)} ${hy + 66} C ${X(46)} ${hy + 74} ${X(-6)} ${hy + 72} ${X(-26)} ${hy + 66} ` +
    `C ${X(-42)} ${hy + 60} ${X(-40)} ${hy + 40} ${X(-30)} ${hy + 30} Z`;
  const parts: TShape[] = [
    arm(X(92), hy + 8, 40 * dir, 8, 12),
    arm(X(102), hy + 24, 70 * dir, 7, 11),
    path(bodyD, C.body),
    ...place([path(HEAD, C.body)], hx, hy, s),
    ...extra,
  ];
  return [
    ...silhouette(parts.slice(0, 4), C.line, 1.6),
    ...parts.slice(4),
    ...place([...beanie(), ...closetaFace(face)], hx, hy, s),
  ];
};

// ── 자세 ─────────────────────────────────────────────

const WARDROBE = {
  wood: '#F1D3B0',
  woodDark: '#D2A97D',
  woodLight: '#F6DEC0',
  inside: '#FBEEDD',
  clothes: ['#CBD0EE', '#A9C4E8', '#F3CFB5', '#4F8F6B', '#F4ECDD', '#F2B9BB'],
};

const art = (shapes: TShape[]): IMascotArt => ({ viewBox: VIEW_BOX, shapes });

/** 열린 옷장 옆에서 빼꼼, 손 흔들기 */
const splash = art([
  sparkle(176, 34, 9, C.accent),
  sparkle(160, 16, 4.5, C.hatLight),
  ...wardrobe(22, 70, 104, 130, WARDROBE),
  ...place(standing({ face: 'smile', wave: 'left' }), 150, 124, 0.94),
]);

/** 상반신, 손 흔들기 + 하트 */
const hello = art([
  ...heartBubble(36, 44, 19, C.heart),
  sparkle(176, 30, 8, C.accent),
  sparkle(188, 56, 4, C.hatLight),
  ...place(standing({ face: 'smile', wave: 'right' }), 96, 122, 1.28),
]);

/** 엎드려 웃음 + 하트 */
const style = art([
  shadow(110, 192, 78, C.hatDark),
  ...heartBubble(166, 50, 17, C.heart),
  heart(186, 90, 6, C.heart, 15),
  sparkle(30, 52, 8, C.accent),
  ...lying(78, 116, 1.1, 1, 'smile', [arm(58, 182, 70, 8, 12.5), arm(102, 186, 80, 8, 12.5)]),
]);

/** 카메라를 든 모습 */
const cameraPose = art([
  sparkle(30, 56, 9, C.accent),
  sparkle(172, 44, 8, C.hatLight),
  sparkle(182, 76, 4, C.accent),
  ...place(standing({ face: 'joy', noArms: true }), 100, 104, 1.2),
  ...camera(100, 164, 70),
  arm(64, 166, 10, 10, 13),
  arm(136, 166, -10, 10, 13),
]);

/** 개어 둔 옷 더미 위에 앉아 놀람 */
const closet = art([
  shadow(100, 194, 76, C.hatDark),
  ...foldedStack(194, [
    { color: '#F3E6D2', x: 26, w: 148, h: 17 },
    { color: '#A9C4E8', x: 32, w: 140, h: 15 },
    { color: '#FFFFFF', x: 28, w: 142, h: 14 },
    { color: '#F3CFB5', x: 36, w: 128, h: 13 },
  ]),
  ...place(standing({ face: 'surprised' }), 100, 78, 0.96),
  line('M 162 38 L 170 28', C.accent, 3.4),
  line('M 170 54 L 182 50', C.accent, 3.4),
  line('M 150 28 L 152 16', C.accent, 3.4),
  sparkle(30, 70, 8, C.hatLight),
]);

/** 엎드려 손 흔들기 + 반짝이 */
const cheer = art([
  shadow(96, 192, 78, C.hatDark),
  sparkle(34, 44, 11, C.accent),
  sparkle(170, 34, 8, C.hatLight),
  sparkle(188, 76, 5, C.accent),
  sparkle(18, 86, 5, C.hatLight),
  sparkle(146, 14, 4, C.accent),
  ...lying(122, 116, 1.1, -1, 'wink', [arm(110, 186, -80, 8, 12.5), arm(170, 122, 35, 8.5, 13)]),
  line('M 182 104 Q 190 112 186 122', C.line, 2.4),
  line('M 190 96 Q 200 108 196 124', C.line, 2.4),
]);

export const CLOSETA: Record<TMascotPose, IMascotArt> = {
  splash,
  hello,
  style,
  camera: cameraPose,
  closet,
  cheer,
};
