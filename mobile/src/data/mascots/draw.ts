// 마스코트 그림을 짜는 작은 도구와 두 마스코트가 같이 쓰는 소품(옷장·개어 둔 옷·카메라·하트·반짝이).
// 순수 TS (RN·Expo·`@/` import 금지).

import type { IMascotShape } from './types';

type TShape = IMascotShape;
type TOpt = Partial<IMascotShape>;

export const VIEW_BOX = '0 0 200 200';

// ── 기본 도형 ──────────────────────────────────────────

export const path = (d: string, fill?: string, o: TOpt = {}): TShape => ({ type: 'path', d, fill, ...o });

export const circle = (cx: number, cy: number, r: number, fill?: string, o: TOpt = {}): TShape => ({
  type: 'circle',
  cx,
  cy,
  r,
  fill,
  ...o,
});

export const ellipse = (cx: number, cy: number, rx: number, ry: number, fill?: string, o: TOpt = {}): TShape => ({
  type: 'ellipse',
  cx,
  cy,
  rx,
  ry,
  fill,
  ...o,
});

export const rect = (x: number, y: number, width: number, height: number, fill?: string, o: TOpt = {}): TShape => ({
  type: 'rect',
  x,
  y,
  width,
  height,
  fill,
  ...o,
});

/** 채우지 않는 선 (끝·꺾임 둥글게) */
export const line = (d: string, stroke: string, strokeWidth: number, o: TOpt = {}): TShape => ({
  type: 'path',
  d,
  stroke,
  strokeWidth,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  ...o,
});

/** 도형 여러 개에 같은 transform을 씌운다 (원래 transform은 그 안쪽에 적용) */
export const group = (shapes: TShape[], transform: string): TShape[] =>
  shapes.map((s) => ({ ...s, transform: s.transform ? `${transform} ${s.transform}` : transform }));

/** 원점 기준으로 그린 부품을 (x, y)로 옮기고 크기·각도를 바꾼다 */
export const place = (shapes: TShape[], x: number, y: number, scale = 1, rotate = 0): TShape[] => {
  let t = `translate(${x} ${y})`;
  if (rotate) t += ` rotate(${rotate})`;
  if (scale !== 1) t += ` scale(${scale})`;
  return group(shapes, t);
};

/** 절대 좌표(M·L·C·Q·Z)만 쓴 path를 x=0 기준으로 좌우 뒤집는다 */
export const mirrorX = (d: string): string => {
  const tokens = d.match(/[MLCQZ]|-?\d*\.?\d+/g) ?? [];
  let isX = true;
  return tokens
    .map((t) => {
      if (/[MLCQZ]/.test(t)) {
        isX = true;
        return t;
      }
      const out = isX ? String(-Number(t)) : t;
      isX = !isX;
      return out;
    })
    .join(' ');
};

/**
 * 몸통 조각들을 하나로 이어 붙인 실루엣처럼 그린다: 먼저 모든 조각을 외곽선 색으로 조금 크게 그리고,
 * 그 위에 원래 색을 덮는다. 조각이 겹친 자리에 이음선이 생기지 않는다.
 */
export const silhouette = (parts: TShape[], outline: string, width: number): TShape[] => [
  ...parts.map((s) => ({ ...s, fill: outline, stroke: outline, strokeWidth: width * 2, strokeLinejoin: 'round' as const })),
  ...parts,
];

// ── 장식 ──────────────────────────────────────────────

/** 네 갈래 반짝이 ✦ */
export const sparkle = (x: number, y: number, size: number, color: string): TShape =>
  path(
    'M 0 -1 Q 0.16 -0.16 1 0 Q 0.16 0.16 0 1 Q -0.16 0.16 -1 0 Q -0.16 -0.16 0 -1 Z',
    color,
    { transform: `translate(${x} ${y}) scale(${size})` },
  );

/** 하트 (size는 반지름 정도) */
export const heart = (x: number, y: number, size: number, color: string, rotate = 0): TShape =>
  path(
    'M 0 0.85 C -0.15 0.72 -1 0.22 -1 -0.32 C -1 -0.78 -0.52 -1.02 -0.2 -0.86 C -0.08 -0.8 0 -0.68 0 -0.58 C 0 -0.68 0.08 -0.8 0.2 -0.86 C 0.52 -1.02 1 -0.78 1 -0.32 C 1 0.22 0.15 0.72 0 0.85 Z',
    color,
    { transform: `translate(${x} ${y}) rotate(${rotate}) scale(${size})` },
  );

/** 하얀 말풍선 동그라미 안의 하트 (보드의 하트 배지) */
export const heartBubble = (x: number, y: number, r: number, color: string, bubble = '#FFFFFF'): TShape[] => [
  path(`M ${x + r * 0.35} ${y + r * 0.9} L ${x + r * 0.75} ${y + r * 1.25} L ${x + r * 0.8} ${y + r * 0.62} Z`, bubble),
  circle(x, y, r, bubble),
  heart(x, y + r * 0.05, r * 0.5, color),
];

/** 바닥 그림자 */
export const shadow = (cx: number, cy: number, rx: number, color: string): TShape =>
  ellipse(cx, cy, rx, rx * 0.09, color, { opacity: 0.18 });

// ── 소품 ──────────────────────────────────────────────

export interface IWardrobeColors {
  wood: string;
  woodDark: string;
  woodLight: string;
  inside: string;
  clothes: string[];
}

/** 옷걸이에 걸린 셔츠 한 벌 (cx: 가운데, top: 옷걸이 봉 높이) */
const hangingShirt = (cx: number, top: number, w: number, len: number, color: string, hook: string): TShape[] => [
  line(`M ${cx} ${top + 4} L ${cx} ${top - 1} Q ${cx} ${top - 4} ${cx + 3} ${top - 4}`, hook, 1.6),
  path(
    `M ${cx - w / 2 + 3} ${top + 5} L ${cx - 3} ${top + 4} L ${cx} ${top + 8} L ${cx + 3} ${top + 4} L ${cx + w / 2 - 3} ${top + 5} ` +
      `Q ${cx + w / 2} ${top + 6} ${cx + w / 2} ${top + 10} L ${cx + w / 2 + 1} ${top + len} L ${cx - w / 2 - 1} ${top + len} ` +
      `L ${cx - w / 2} ${top + 10} Q ${cx - w / 2} ${top + 6} ${cx - w / 2 + 3} ${top + 5} Z`,
    color,
  ),
  line(`M ${cx} ${top + 8} L ${cx} ${top + len - 2}`, '#000000', 1, { opacity: 0.08 }),
  line(`M ${cx - w / 2 + 3} ${top + 5} L ${cx} ${top + 1} L ${cx + w / 2 - 3} ${top + 5}`, hook, 1.6),
];

/**
 * 나무 옷장 (앞에서 본 모습, 문 열림). 옷장 몸통 왼쪽 위가 (x, y), 바닥은 y + h.
 * 마스코트가 옷장 뒤에 숨으려면 옷장보다 먼저 그린다.
 */
export const wardrobe = (x: number, y: number, w: number, h: number, c: IWardrobeColors): TShape[] => {
  const inX = x + 9;
  const inW = w - 18;
  const rodY = y + 17;
  const shapes: TShape[] = [
    // 열린 문짝 (좌우)
    path(`M ${x} ${y + 4} L ${x - 14} ${y + 10} L ${x - 14} ${y + h} L ${x} ${y + h} Z`, c.woodDark),
    path(`M ${x - 10} ${y + 18} L ${x - 4} ${y + 16} L ${x - 4} ${y + h - 8} L ${x - 10} ${y + h - 6} Z`, c.wood, { opacity: 0.7 }),
    path(`M ${x + w} ${y + 4} L ${x + w + 14} ${y + 10} L ${x + w + 14} ${y + h} L ${x + w} ${y + h} Z`, c.woodDark),
    path(`M ${x + w + 4} ${y + 16} L ${x + w + 10} ${y + 18} L ${x + w + 10} ${y + h - 6} L ${x + w + 4} ${y + h - 8} Z`, c.wood, { opacity: 0.7 }),
    // 몸통과 안쪽
    rect(x, y, w, h, c.wood),
    rect(inX, y + 8, inW, h - 8, c.inside),
    rect(inX, y + 8, inW, 5, '#000000', { opacity: 0.06 }),
    // 옷걸이 봉
    line(`M ${inX + 2} ${rodY} L ${inX + inW - 2} ${rodY}`, c.woodDark, 2.6),
  ];
  // 걸린 옷
  const n = c.clothes.length;
  const step = (inW - 16) / Math.max(1, n - 1);
  c.clothes.forEach((color, i) => {
    const len = 40 + ((i * 7) % 3) * 6;
    shapes.push(...hangingShirt(inX + 8 + i * step, rodY, 19, len, color, c.woodDark));
  });
  // 아래 선반과 개어 둔 옷
  const shelfY = y + h - 18;
  shapes.push(
    rect(inX, shelfY, inW, 4, c.woodDark),
    rect(inX + 6, shelfY + 6, 30, 7, c.clothes[1] ?? c.wood, { rx: 2 }),
    rect(inX + 8, shelfY + 13, 28, 5, c.clothes[3] ?? c.wood, { rx: 2 }),
    // 윗판 (몸통보다 살짝 넓게)
    rect(x - 5, y - 6, w + 10, 9, c.woodLight, { rx: 3 }),
    rect(x - 5, y + 1, w + 10, 2, c.woodDark, { opacity: 0.5 }),
  );
  return shapes;
};

export interface IFolded {
  color: string;
  /** 왼쪽 끝 x */
  x: number;
  w: number;
  h: number;
}

/** 아래부터 차곡차곡 개어 둔 옷 더미. bottom: 맨 아래 옷의 바닥 y */
export const foldedStack = (bottom: number, items: IFolded[]): TShape[] => {
  const shapes: TShape[] = [];
  let y = bottom;
  items.forEach((it) => {
    y -= it.h;
    shapes.push(
      rect(it.x, y, it.w, it.h, it.color, { rx: it.h * 0.4 }),
      rect(it.x, y + it.h * 0.62, it.w, it.h * 0.38, '#000000', { rx: it.h * 0.3, opacity: 0.07 }),
      line(`M ${it.x + 6} ${y + it.h * 0.42} L ${it.x + it.w * 0.55} ${y + it.h * 0.42}`, '#FFFFFF', 1.4, { opacity: 0.55 }),
      line(`M ${it.x + it.w * 0.7} ${y + 3} L ${it.x + it.w * 0.7} ${y + it.h - 3}`, '#000000', 1, { opacity: 0.08 }),
    );
    y += 1; // 살짝 눌려 겹치게
  });
  return shapes;
};

/** 카메라 (가운데 cx, cy, 폭 w) */
export const camera = (cx: number, cy: number, w: number): TShape[] => {
  const h = w * 0.64;
  const x = cx - w / 2;
  const y = cy - h / 2;
  const r = h * 0.36;
  return [
    rect(x + w * 0.16, y - h * 0.16, w * 0.3, h * 0.24, '#3A3D42', { rx: 3 }),
    rect(x, y, w, h, '#2E3135', { rx: 8 }),
    rect(x, y, w, h * 0.22, '#45494F', { rx: 6 }),
    rect(x + w * 0.76, y + h * 0.1, w * 0.13, h * 0.13, '#F2C46A', { rx: 2 }),
    circle(cx, cy + h * 0.06, r + 3, '#55595F'),
    circle(cx, cy + h * 0.06, r, '#1A1C1F'),
    circle(cx, cy + h * 0.06, r * 0.62, '#3B5470'),
    circle(cx - r * 0.25, cy + h * 0.06 - r * 0.25, r * 0.2, '#FFFFFF', { opacity: 0.85 }),
  ];
};
