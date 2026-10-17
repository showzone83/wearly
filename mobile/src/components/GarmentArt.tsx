import { useId, type ReactNode } from 'react';
import Svg, { Circle, ClipPath, Defs, Ellipse, G, LinearGradient, Path, RadialGradient, Rect, Stop } from 'react-native-svg';

import {
  GARMENT_BUTTONS,
  GARMENT_DETAIL_DEFAULT_WIDTH,
  GARMENT_DETAIL_WIDTH,
  GARMENT_DETAILS,
  GARMENT_FOLD_OPACITY,
  GARMENT_FOLD_WIDTH,
  GARMENT_FOLDS,
  GARMENT_LIGHT,
  GARMENT_SHAPES,
  garmentShadow,
  garmentTones,
  resolveShape,
  shadeHex,
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

// 디자인 보드 옷장 사진 카드 느낌의 부드러운 평면 일러스트. 세부 종류별 실루엣을 옷 색 그라데이션으로 칠하고
// 아래에 옅은 그림자, 몸판에 옅은 주름 선을 더한다. 모양·색 규칙은 src/data/garmentShapes.ts (관리자 사이트와 같은 데이터)
export function GarmentArt({ subType, category, color, pattern = 'SOLID', size = 80 }: Props) {
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const clipId = `garmentClip${uid}`;
  const fillId = `garmentFill${uid}`;
  const shadowId = `garmentShadow${uid}`;
  const cat = category ?? parentOf(subType);
  const shape = resolveShape(subTypeMeta(subType).shape, cat);
  const c = colorMeta(color);
  const tone = garmentTones(c.hex, c.light);
  const detail = shadeHex(c.hex, c.light > 0.5 ? -0.2 : 0.18);
  const accent = c.light > 0.5 ? '#C4566B' : '#F2D7DE';
  const body = GARMENT_SHAPES[shape];
  const layer = patternLayer(pattern, detail, accent);
  const shadow = garmentShadow(shape, cat);
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      <Defs>
        <LinearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={tone.top} />
          <Stop offset="0.45" stopColor={tone.fill} />
          <Stop offset="1" stopColor={tone.bottom} />
        </LinearGradient>
        <RadialGradient id={shadowId} cx="50%" cy="50%" rx="50%" ry="50%">
          <Stop offset="0" stopColor="#5A4632" stopOpacity={0.22} />
          <Stop offset="0.6" stopColor="#5A4632" stopOpacity={0.1} />
          <Stop offset="1" stopColor="#5A4632" stopOpacity={0} />
        </RadialGradient>
        <ClipPath id={clipId}>
          <Path d={body} />
        </ClipPath>
      </Defs>
      <Ellipse cx={shadow.cx} cy={shadow.cy} rx={shadow.rx} ry={shadow.ry} fill={`url(#${shadowId})`} />
      <Path d={body} fill={`url(#${fillId})`} stroke={tone.edge} strokeWidth={0.9} strokeLinejoin="round" />
      {layer && <G clipPath={`url(#${clipId})`}>{layer}</G>}
      {GARMENT_FOLDS[shape] && (
        <Path
          d={GARMENT_FOLDS[shape]}
          fill="none"
          stroke={tone.line}
          strokeWidth={GARMENT_FOLD_WIDTH}
          strokeOpacity={GARMENT_FOLD_OPACITY}
          strokeLinecap="round"
          clipPath={`url(#${clipId})`}
        />
      )}
      {GARMENT_LIGHT[shape] && <Path d={GARMENT_LIGHT[shape]} fill="#FFFFFF" stroke={tone.edge} strokeWidth={2.2} strokeLinejoin="round" />}
      {GARMENT_DETAILS[shape] && (
        <Path
          d={GARMENT_DETAILS[shape]}
          fill="none"
          stroke={tone.line}
          strokeOpacity={0.85}
          strokeWidth={GARMENT_DETAIL_WIDTH[shape] ?? GARMENT_DETAIL_DEFAULT_WIDTH}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      )}
      {GARMENT_BUTTONS[shape]?.map(([x, y]) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={1.5} fill={tone.line} />)}
    </Svg>
  );
}
