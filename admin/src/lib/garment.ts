// 앱의 옷 일러스트 모양 데이터를 가져온다 (vite.config.ts의 '@garment-shapes' 별칭 → mobile/src/data/garmentShapes.ts).
import {
  DEFAULT_SHAPE_BY_CATEGORY,
  GARMENT_BUTTONS,
  GARMENT_DETAIL_WIDTH,
  GARMENT_DETAILS,
  GARMENT_LIGHT,
  GARMENT_SHAPES,
  resolveShape,
  shapePath,
} from '@garment-shapes';

export { DEFAULT_SHAPE_BY_CATEGORY, GARMENT_SHAPES, resolveShape };

/** 앱 데이터를 찾았는지 (대체 파일이면 false) */
export const HAS_APP_SHAPES = Object.keys(GARMENT_SHAPES).length > 0;

/** 모양 키 목록 (선택 목록용, 알파벳 순) */
export const SHAPE_KEYS: string[] = Object.keys(GARMENT_SHAPES).sort();

/** 앱 GarmentArt와 같은 층: 몸판 → 밝은 면 → 세부선 → 단추 */
export interface GarmentLayers {
  /** 실제로 그린 모양 키 (모르는 키면 종류 기본 모양) */
  key: string;
  body: string;
  light?: string;
  details?: string;
  detailWidth: number;
  buttons: [number, number][];
}

export function garmentLayers(shape: string | null | undefined, category: string | null | undefined): GarmentLayers {
  const key = resolveShape(shape ?? '', category ?? '');
  return {
    key,
    body: shapePath(shape ?? '', category ?? ''),
    light: GARMENT_LIGHT[key],
    details: GARMENT_DETAILS[key],
    detailWidth: GARMENT_DETAIL_WIDTH[key] ?? 1.2,
    buttons: GARMENT_BUTTONS[key] ?? [],
  };
}

/** 이 모양 키를 앱이 아는지 */
export function isKnownShape(shape: string | null | undefined): boolean {
  return !!shape && Object.prototype.hasOwnProperty.call(GARMENT_SHAPES, shape);
}

/** 진하게(음수)·연하게(양수) 조정한 색 */
export function shade(hex: string, amount: number): string {
  const n = parseInt(hex.replace('#', ''), 16);
  if (Number.isNaN(n)) return hex;
  const ch = (v: number) => Math.max(0, Math.min(255, Math.round(v + amount * 255)));
  const r = ch(n >> 16);
  const g = ch((n >> 8) & 0xff);
  const b = ch(n & 0xff);
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

/** hex의 밝기(0~1, 소수 둘째 자리). COLOR attrs.light 제안값 */
export function lightnessOf(hex: string): number | null {
  if (!/^#[0-9a-fA-F]{6}$/.test(hex)) return null;
  const n = parseInt(hex.slice(1), 16);
  const lin = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  const y = 0.2126 * lin(n >> 16) + 0.7152 * lin((n >> 8) & 0xff) + 0.0722 * lin(n & 0xff);
  // 상대 휘도를 사람이 느끼는 밝기에 가깝게 (L* / 100)
  const l = y > 0.008856 ? 116 * Math.cbrt(y) - 16 : 903.3 * y;
  return Math.round((l / 100) * 100) / 100;
}
