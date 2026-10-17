// 앱의 옷 일러스트 모양 데이터를 가져온다 (vite.config.ts의 '@garment-shapes' 별칭 → mobile/src/data/garmentShapes.ts).
import {
  DEFAULT_SHAPE_BY_CATEGORY,
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
  shapePath,
} from '@garment-shapes';

export { DEFAULT_SHAPE_BY_CATEGORY, GARMENT_FOLD_OPACITY, GARMENT_FOLD_WIDTH, GARMENT_SHAPES, garmentTones, resolveShape };

/** 앱 데이터를 찾았는지 (대체 파일이면 false) */
export const HAS_APP_SHAPES = Object.keys(GARMENT_SHAPES).length > 0;

/** 모양 키 목록 (선택 목록용, 알파벳 순) */
export const SHAPE_KEYS: string[] = Object.keys(GARMENT_SHAPES).sort();

/** 앱 GarmentArt와 같은 층: 그림자 → 몸판(그라데이션) → 주름 → 밝은 면 → 세부선 → 단추 */
export interface IGarmentLayers {
  /** 실제로 그린 모양 키 (모르는 키면 종류 기본 모양) */
  key: string;
  shadow: { cx: number; cy: number; rx: number; ry: number };
  body: string;
  folds?: string;
  light?: string;
  details?: string;
  detailWidth: number;
  buttons: [number, number][];
}

export const garmentLayers = (
  shape: string | null | undefined,
  category: string | null | undefined,
): IGarmentLayers => {
  const key = resolveShape(shape ?? '', category ?? '');
  return {
    key,
    shadow: garmentShadow(shape ?? '', category ?? ''),
    body: shapePath(shape ?? '', category ?? ''),
    folds: GARMENT_FOLDS[key],
    light: GARMENT_LIGHT[key],
    details: GARMENT_DETAILS[key],
    detailWidth: GARMENT_DETAIL_WIDTH[key] ?? GARMENT_DETAIL_DEFAULT_WIDTH,
    buttons: GARMENT_BUTTONS[key] ?? [],
  };
};

/** 이 모양 키를 앱이 아는지 */
export const isKnownShape = (shape: string | null | undefined): boolean => {
  return !!shape && Object.prototype.hasOwnProperty.call(GARMENT_SHAPES, shape);
};

/** hex의 밝기(0~1, 소수 둘째 자리). COLOR attrs.light 제안값 */
export const lightnessOf = (hex: string): number | null => {
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
};
