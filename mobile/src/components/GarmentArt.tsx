import { useId, type ReactNode } from 'react';
import Svg, { Circle, ClipPath, Defs, G, Path, Rect } from 'react-native-svg';

import {
  GARMENT_BUTTONS,
  GARMENT_DETAIL_WIDTH,
  GARMENT_DETAILS,
  GARMENT_LIGHT,
  GARMENT_SHAPES,
  resolveShape,
} from '@/data/garmentShapes';
import type { Category, ColorKey, Pattern, SubType } from '@/data/types';
import { colorMeta, parentOf, subTypeMeta } from '@/lib/codes';

interface Props {
  subType: SubType;
  /** 모르는 모양일 때 기본 모양을 고르는 종류. 없으면 세부 종류의 상위 종류 */
  category?: Category;
  color: ColorKey;
  pattern?: Pattern;
  size?: number;
}

/** 진하게/연하게 조정한 테두리 색 */
function shade(hex: string, amount: number): string {
  const n = parseInt(hex.slice(1), 16);
  const ch = (v: number) => Math.max(0, Math.min(255, Math.round(v + amount * 255)));
  const r = ch(n >> 16);
  const g = ch((n >> 8) & 0xff);
  const b = ch(n & 0xff);
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

const GRID = [8, 20, 32, 44, 56, 68, 80, 92];

/** 몸판 안에 잘라 그릴 패턴. 모르는 패턴(기타 포함)은 무늬 없이 둔다 */
function patternLayer(pattern: string, detail: string, accent: string): ReactNode {
  switch (pattern) {
    case 'STRIPE':
      return GRID.map((y) => <Rect key={y} x={0} y={y} width={100} height={3.2} fill={detail} opacity={0.7} />);
    case 'CHECK':
      return [
        ...GRID.map((x) => <Rect key={`v${x}`} x={x} y={0} width={2.6} height={100} fill={detail} opacity={0.55} />),
        ...GRID.map((y) => <Rect key={`h${y}`} x={0} y={y} width={100} height={2.6} fill={detail} opacity={0.4} />),
      ];
    case 'DOT':
      return GRID.flatMap((y, row) =>
        GRID.map((x) => <Circle key={`${x}-${y}`} cx={x + (row % 2 ? 6 : 0)} cy={y} r={1.9} fill={detail} opacity={0.8} />),
      );
    case 'FLORAL':
      return [
        [30, 30],
        [62, 26],
        [46, 52],
        [24, 70],
        [70, 64],
        [52, 84],
        [80, 40],
        [16, 46],
      ].map(([x, y]) => (
        <G key={`${x}-${y}`} opacity={0.85}>
          {[0, 72, 144, 216, 288].map((deg) => {
            const rad = (deg * Math.PI) / 180;
            return <Circle key={deg} cx={x + Math.cos(rad) * 3.2} cy={y + Math.sin(rad) * 3.2} r={2.4} fill={accent} />;
          })}
          <Circle cx={x} cy={y} r={1.6} fill={detail} />
        </G>
      ));
    case 'PRINT':
      return (
        <G opacity={0.85}>
          <Rect x={38} y={38} width={24} height={20} rx={3} fill={detail} />
          <Circle cx={50} cy={48} r={5.5} fill={accent} />
        </G>
      );
    default:
      return null;
  }
}

// 목업(p.40)의 평면 일러스트처럼 세부 종류별 실루엣을 옷 색으로 칠한다. 모양 데이터는 src/data/garmentShapes.ts
export function GarmentArt({ subType, category, color, pattern = 'SOLID', size = 80 }: Props) {
  const clipId = `garment${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const shape = resolveShape(subTypeMeta(subType).shape, category ?? parentOf(subType));
  const c = colorMeta(color);
  const hex = c.hex;
  const edge = shade(hex, c.light > 0.6 ? -0.28 : -0.12);
  const detail = shade(hex, c.light > 0.5 ? -0.2 : 0.18);
  const accent = c.light > 0.5 ? '#C4566B' : '#F2D7DE';
  const body = GARMENT_SHAPES[shape];
  const layer = patternLayer(pattern, detail, accent);
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      {layer && (
        <Defs>
          <ClipPath id={clipId}>
            <Path d={body} />
          </ClipPath>
        </Defs>
      )}
      <Path d={body} fill={hex} stroke={edge} strokeWidth={1.4} strokeLinejoin="round" />
      {layer && <G clipPath={`url(#${clipId})`}>{layer}</G>}
      {GARMENT_LIGHT[shape] && <Path d={GARMENT_LIGHT[shape]} fill="#FFFFFF" stroke={edge} strokeWidth={2.5} strokeLinejoin="round" />}
      {GARMENT_DETAILS[shape] && (
        <Path
          d={GARMENT_DETAILS[shape]}
          fill="none"
          stroke={edge}
          strokeWidth={GARMENT_DETAIL_WIDTH[shape] ?? 1.2}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      )}
      {GARMENT_BUTTONS[shape]?.map(([x, y]) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={1.6} fill={edge} />)}
    </Svg>
  );
}
