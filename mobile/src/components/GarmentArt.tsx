import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { COLOR } from '@/data/constants';
import type { ColorKey, Pattern, SubType } from '@/data/types';

interface Props {
  subType: SubType;
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

// 목업(p.40)의 평면 일러스트처럼 종류별 실루엣을 옷 색으로 칠한다.
const SHAPES: Record<SubType, string> = {
  TEE: 'M34 18 L44 14 Q50 20 56 14 L66 18 L82 32 L73 43 L66 37 L66 84 L34 84 L34 37 L27 43 L18 32 Z',
  KNIT: 'M34 18 L44 15 Q50 22 56 15 L66 18 L80 30 L84 72 L75 73 L70 42 L68 84 L32 84 L30 42 L25 73 L16 72 L20 30 Z',
  SHIRT: 'M35 18 L44 14 L50 22 L56 14 L65 18 L80 30 L84 74 L75 75 L70 42 L68 86 L32 86 L30 42 L25 75 L16 74 L20 30 Z',
  CARDIGAN: 'M34 18 L44 14 L50 40 L56 14 L66 18 L80 30 L84 76 L75 77 L70 44 L69 86 L31 86 L30 44 L25 77 L16 76 L20 30 Z',
  JACKET: 'M33 16 L44 13 L50 34 L56 13 L67 16 L82 28 L86 78 L76 79 L71 44 L70 88 L30 88 L29 44 L24 79 L14 78 L18 28 Z',
  COAT: 'M34 12 L44 10 L50 30 L56 10 L66 12 L80 24 L85 82 L76 83 L71 42 L72 94 L28 94 L29 42 L24 83 L15 82 L20 24 Z',
  SLACKS: 'M34 12 L66 12 L68 90 L55 90 L50 36 L45 90 L32 90 Z',
  WIDE: 'M33 12 L67 12 L76 90 L56 90 L50 38 L44 90 L24 90 Z',
  CHINO: 'M34 12 L66 12 L69 90 L56 90 L50 38 L44 90 L31 90 Z',
  SKIRT: 'M36 16 L64 16 L76 82 L24 82 Z',
  LOAFER: 'M16 62 Q18 48 34 50 L48 54 Q70 56 82 64 Q86 70 80 72 L18 72 Q14 70 16 62 Z',
  SNEAKER: 'M14 64 Q16 46 30 46 L44 52 Q66 54 84 62 Q88 70 82 74 L16 74 Q12 72 14 64 Z',
  TOTE: 'M22 40 L78 40 L74 86 L26 86 Z',
  SHOULDER: 'M24 46 L76 46 Q80 46 79 50 L75 80 L25 80 L21 50 Q20 46 24 46 Z',
  WATCH: 'M42 16 L58 16 L58 84 L42 84 Z',
};

export function GarmentArt({ subType, color, pattern = 'SOLID', size = 80 }: Props) {
  const hex = COLOR[color].hex;
  const edge = shade(hex, COLOR[color].light > 0.6 ? -0.28 : -0.12);
  const detail = shade(hex, COLOR[color].light > 0.5 ? -0.2 : 0.18);
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      <Path d={SHAPES[subType]} fill={hex} stroke={edge} strokeWidth={1.4} strokeLinejoin="round" />
      {pattern === 'STRIPE' && ['TEE', 'KNIT', 'SHIRT'].includes(subType) &&
        [30, 42, 54, 66, 78].map((y) => <Rect key={y} x={32} y={y} width={36} height={3} fill={detail} opacity={0.7} />)}
      {pattern === 'CHECK' &&
        [36, 50, 64].map((x) => <Rect key={x} x={x} y={20} width={2.5} height={64} fill={detail} opacity={0.6} />)}
      {subType === 'SHIRT' && <Path d="M50 22 L50 86" stroke={edge} strokeWidth={1} />}
      {(subType === 'CARDIGAN' || subType === 'JACKET' || subType === 'COAT') && (
        <Path d={subType === 'COAT' ? 'M50 30 L50 94' : 'M50 36 L50 88'} stroke={edge} strokeWidth={1.2} />
      )}
      {subType === 'CARDIGAN' && [48, 60, 72].map((y) => <Circle key={y} cx={53} cy={y} r={1.6} fill={edge} />)}
      {(subType === 'SLACKS' || subType === 'WIDE' || subType === 'CHINO') && (
        <Path d="M34 18 L66 18" stroke={edge} strokeWidth={1.2} />
      )}
      {subType === 'TOTE' && <Path d="M36 40 Q36 18 50 18 Q64 18 64 40" stroke={edge} strokeWidth={3} fill="none" />}
      {subType === 'SHOULDER' && <Path d="M28 46 Q50 8 72 46" stroke={edge} strokeWidth={2} fill="none" />}
      {subType === 'SNEAKER' && <Path d="M14 68 L86 68" stroke={edge} strokeWidth={4} />}
      {subType === 'WATCH' && <Circle cx={50} cy={50} r={14} fill="#fff" stroke={edge} strokeWidth={3} />}
    </Svg>
  );
}
