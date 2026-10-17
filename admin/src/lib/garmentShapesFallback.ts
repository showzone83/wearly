// 앱의 일러스트 모양 데이터(mobile/src/data/garmentShapes.ts)가 없을 때만 쓰는 대체 파일.
// vite.config.ts와 tsconfig.app.json의 '@garment-shapes'가 앱 파일을 먼저 찾고, 없으면 이 파일을 쓴다.
// export 이름·모양은 앱 파일과 같게 두되 모양은 비워 두고, 모든 옷을 단순한 사각형으로 그린다
// (화면에 '앱 일러스트 데이터 없음' 안내가 뜬다).

export const GARMENT_SHAPES: Record<string, string> = {};
export const GARMENT_DETAILS: Record<string, string> = {};
export const GARMENT_DETAIL_WIDTH: Record<string, number> = {};
export const GARMENT_BUTTONS: Record<string, [number, number][]> = {};
export const GARMENT_LIGHT: Record<string, string> = {};
export const GARMENT_FOLDS: Record<string, string> = {};
export const GARMENT_DETAIL_DEFAULT_WIDTH = 1.1;
export const GARMENT_FOLD_WIDTH = 0.9;
export const GARMENT_FOLD_OPACITY = 0.38;
export const DEFAULT_SHAPE_BY_CATEGORY: Record<string, string> = {};
export const FALLBACK_SHAPE = 'BOX';

export const resolveShape = (shape: string | null | undefined, category?: string | null): string => {
  if (shape && GARMENT_SHAPES[shape]) return shape;
  const byCategory = category ? DEFAULT_SHAPE_BY_CATEGORY[category] : undefined;
  return byCategory && GARMENT_SHAPES[byCategory] ? byCategory : FALLBACK_SHAPE;
};

export const shapePath = (shape: string | null | undefined, category?: string | null): string => {
  return GARMENT_SHAPES[resolveShape(shape, category)] ?? 'M30 20 L70 20 L70 80 L30 80 Z';
};

/** 대체 파일은 그라데이션 없이 단색으로 칠한다 */
export const garmentTones = (hex: string, _light: number) => {
  void _light;
  return { top: hex, fill: hex, bottom: hex, edge: '#8A8F98', line: '#8A8F98' };
};

export const garmentShadow = (_shape: string | null | undefined, _category?: string | null) => {
  void _shape;
  void _category;
  return { cx: 50, cy: 83, rx: 18, ry: 3 };
};
