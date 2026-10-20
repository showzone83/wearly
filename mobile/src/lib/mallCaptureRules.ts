// 쇼핑몰 캡처로 옷 사진 등록 (doc/24) 순수 규칙. React Native·Expo·`@/` 값 import가 없다:
// node 단위 테스트(tests/mallCapture.test.mjs)가 그대로 읽는다. 화면은 src/app/item/mall.tsx.

/** 앱 목록에 보여 줄 쇼핑몰 (코드 그룹 MALL 한 줄) */
export interface IMall {
  code: string;
  label: string;
  /** 처음 열 주소 (https) */
  url: string;
}

/** 코드 한 줄에서 쓰는 칸만 (src/lib/codes.ts의 Code와 같은 모양) */
export interface IMallCodeLike {
  code: string;
  label: string;
  active: boolean;
  attrs: Record<string, unknown>;
}

export interface ISize {
  width: number;
  height: number;
}

export interface IRect extends ISize {
  x: number;
  y: number;
}

/** 쇼핑몰 화면의 정사각 틀이 화면 가장자리에서 떨어지는 거리 (논리 px) */
export const FRAME_MARGIN = 24;
/** 사용자가 줄일 수 있는 틀의 가장 작은 한 변 (논리 px) */
export const MIN_FRAME_SIDE = 120;

/** 틀 크기 손잡이가 있는 모서리: 위왼쪽·위오른쪽·아래왼쪽·아래오른쪽 */
export type TFrameCorner = 'tl' | 'tr' | 'bl' | 'br';

export const MALL_GUIDE_TEXT = '모서리로 크기, 가운데로 위치를 맞추고 캡처하세요';
export const MALL_NOTICE_TEXT = '캡처한 사진은 내 옷장 기록용으로만 써 주세요.';
export const MALL_EMPTY_TEXT = '등록된 쇼핑몰이 없어요. 서버에 연결되면 다시 열어 주세요.';

/** https 주소인지 (서버 CodeAttrs.isHttpsUrl과 같은 규칙: 호스트 있음, 사용자 정보 없음) */
export const isHttpsUrl = (value: unknown): value is string => {
  if (typeof value !== 'string') return false;
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !!u.hostname && !u.username && !u.password;
  } catch {
    return false;
  }
};

/** 고를 수 있는 쇼핑몰: 활성이고 attrs.url이 https인 코드만, 받은 순서대로 */
export const mallList = (codes: readonly IMallCodeLike[]): IMall[] =>
  codes
    .filter((c) => c.active && isHttpsUrl(c.attrs?.url))
    .map((c) => ({ code: c.code, label: c.label, url: c.attrs.url as string }));

/**
 * 앱 안 웹 화면에서 열어도 되는 주소. 웹 주소(http·https)와 페이지가 안에서 쓰는 about·data·blob만 허용한다.
 * intent:·market:·카카오톡 같은 앱 스킴, tel:·mailto: 등 다른 앱을 여는 주소는 막는다 (쇼핑몰 앱 설치 유도 등).
 */
export const isAllowedNavigation = (url: string): boolean => /^(https?|about|data|blob):/i.test(url.trim());

/** 화면 크기에서 가운데 정사각 틀 위치 (가장자리 margin, 화면이 작으면 0) */
export const frameRect = (view: ISize, margin = FRAME_MARGIN): IRect => {
  const side = Math.max(0, Math.min(view.width - margin * 2, view.height - margin * 2));
  return { x: (view.width - side) / 2, y: (view.height - side) / 2, width: side, height: side };
};

/**
 * 모서리 손잡이를 (dx, dy)만큼 끈 틀. 마주 보는 모서리를 고정하고 정사각을 유지한다.
 * 늘어나는 양은 모서리 바깥 방향으로 맞춘 dx·dy의 평균이고, 한 변은 min 이상·고정한 모서리에서 화면 끝까지 이하다
 * (화면이 min보다 작으면 들어가는 만큼만).
 */
export const resizeFrame = (
  start: IRect,
  corner: TFrameCorner,
  dx: number,
  dy: number,
  view: ISize,
  min = MIN_FRAME_SIDE,
): IRect => {
  const sx = corner === 'tr' || corner === 'br' ? 1 : -1;
  const sy = corner === 'bl' || corner === 'br' ? 1 : -1;
  // 고정하는 모서리
  const ax = sx > 0 ? start.x : start.x + start.width;
  const ay = sy > 0 ? start.y : start.y + start.height;
  const maxSide = Math.max(0, Math.min(sx > 0 ? view.width - ax : ax, sy > 0 ? view.height - ay : ay));
  const side = clamp(start.width + (sx * dx + sy * dy) / 2, Math.min(min, maxSide), maxSide);
  return { x: sx > 0 ? ax : ax - side, y: sy > 0 ? ay : ay - side, width: side, height: side };
};

/** 가운데 손잡이를 (dx, dy)만큼 끈 틀. 크기는 그대로, 화면 밖으로 나가지 않는다 */
export const moveFrame = (start: IRect, dx: number, dy: number, view: ISize): IRect => ({
  ...start,
  x: clamp(start.x + dx, 0, Math.max(0, view.width - start.width)),
  y: clamp(start.y + dy, 0, Math.max(0, view.height - start.height)),
});

/** 화면 크기가 바뀌었을 때(회전·머리 높이 등) 틀을 화면 안으로 넣는다. 한 변은 화면 짧은 변 이하 */
export const clampFrame = (frame: IRect, view: ISize): IRect => {
  const side = Math.max(0, Math.min(frame.width, view.width, view.height));
  return {
    x: clamp(frame.x, 0, view.width - side),
    y: clamp(frame.y, 0, view.height - side),
    width: side,
    height: side,
  };
};

/**
 * 화면(논리 px) 위 틀을 캡처 이미지(실제 px) 좌표로 바꾼다. 캡처는 기기 배율(PixelRatio)만큼 커지므로 가로·세로 배율을 따로 곱하고,
 * 이미지 밖으로 나가지 않게 자른다. 크기를 모르면(0) null
 */
export const cropRect = (frame: IRect, view: ISize, image: ISize): IRect | null => {
  if (view.width <= 0 || view.height <= 0 || image.width <= 0 || image.height <= 0) return null;
  const sx = image.width / view.width;
  const sy = image.height / view.height;
  const x = clamp(Math.round(frame.x * sx), 0, image.width - 1);
  const y = clamp(Math.round(frame.y * sy), 0, image.height - 1);
  const width = clamp(Math.round(frame.width * sx), 1, image.width - x);
  const height = clamp(Math.round(frame.height * sy), 1, image.height - y);
  return { x, y, width, height };
};

const clamp = (v: number, min: number, max: number): number => Math.min(max, Math.max(min, v));
