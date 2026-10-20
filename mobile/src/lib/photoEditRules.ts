// 옷 상세의 사진 바꾸기·빼기(이슈 #8) 순수 규칙. React Native·Expo·`@/` 값 import가 없다 (타입만 가져온다):
// node 단위 테스트(tests/photoEdit.test.mjs)가 그대로 읽는다. 흐름은 src/lib/itemPhoto.ts, 화면은 src/app/item/[id].tsx.
import type { ItemDraft } from '@/components/ItemForm';
import type { IItemImage } from '@/data/types';

// ── 작은 사진 안내 ──

/** 고른 사진의 긴 변이 이보다 작으면 "흐리게 보일 수 있어요" 안내를 띄운다 (막지는 않는다) */
export const SMALL_PHOTO_MIN_SIDE = 600;

export const SMALL_PHOTO_TEXT = '사진이 작아서 흐리게 보일 수 있어요. 더 큰 사진이 있으면 바꿔 주세요.';

/** 고른 사진이 작은지. 크기를 모르면(0·없음) 안내하지 않는다 */
export const isSmallPhoto = (width: number | null | undefined, height: number | null | undefined): boolean => {
  const w = Number(width) || 0;
  const h = Number(height) || 0;
  if (w <= 0 || h <= 0) return false;
  return Math.max(w, h) < SMALL_PHOTO_MIN_SIDE;
};

// ── 사진 시트 ──

/** mall: 쇼핑몰 화면에서 캡처 (doc/24) */
export type TPhotoAction = 'camera' | 'library' | 'mall' | 'remove';

export interface IPhotoSheetOption {
  action: TPhotoAction;
  label: string;
  /** 빨간 글씨 (사진 빼기) */
  danger?: boolean;
}

/** 사진이 있는지: 서버 사진이나 기기 사진(샘플 포함) */
export const hasItemPhoto = (item: { image?: IItemImage | null; imageUri?: string | null } | undefined): boolean =>
  !!(item?.image?.imageId || item?.imageUri);

/**
 * 사진 칸을 눌렀을 때 시트의 선택지 (취소는 시트가 따로 그린다). 사진 빼기는 사진이 있을 때만,
 * 쇼핑몰에서 가져오기는 고를 쇼핑몰이 있고 앱 안 웹 화면을 쓸 수 있을 때만(웹 체험판은 아님)
 */
export const photoSheetOptions = (hasPhoto: boolean, mallAvailable = false): IPhotoSheetOption[] => {
  const options: IPhotoSheetOption[] = [
    { action: 'camera', label: hasPhoto ? '카메라로 다시 찍기' : '카메라로 찍기' },
    { action: 'library', label: '앨범에서 고르기' },
  ];
  if (mallAvailable) options.push({ action: 'mall', label: '쇼핑몰에서 가져오기' });
  if (hasPhoto) options.push({ action: 'remove', label: '사진 빼기', danger: true });
  return options;
};

export const photoSheetTitle = (hasPhoto: boolean): string => (hasPhoto ? '사진 바꾸기' : '사진 추가');

export const REMOVE_PHOTO_TITLE = '사진을 뺄까요?';
export const REMOVE_PHOTO_MESSAGE = '옷 정보는 그대로 두고 사진만 지워요. 지운 사진은 되돌릴 수 없어요.';

// ── 바꾼 뒤 상태 ──

/** 사진을 바꾸기 전 아이템 상태 */
export interface IPhotoBefore {
  image?: IItemImage;
  /** 화면이 그리는 기기 사진 (계정 모드는 photos[id]와 같다) */
  imageUri?: string;
  /** 계정 모드: 아직 서버로 옮기지 못한 기기 사진 photos[id] */
  localPhoto?: string;
}

export type TPhotoChange =
  /** 계정 모드: 서버에 올려 붙인 새 사진 */
  | { kind: 'server'; image: IItemImage }
  /** 게스트, 또는 사진 기능이 없는 예전 서버: 기기에 둔 새 사진 */
  | { kind: 'local'; uri: string }
  /** 사진 빼기 */
  | { kind: 'removed' };

export interface IPhotoChangePlan {
  /** 아이템에 덮어쓸 사진 필드 (undefined = 지움) */
  item: { image: IItemImage | undefined; imageUri: string | undefined };
  /** 계정 모드 photos[id]의 새 값 (undefined = 지움). 게스트는 쓰지 않는다 */
  localPhoto: string | undefined;
  /** photoSync[id](예전 기기 사진 옮기기 기록)를 지운다. 예전 사진은 더 옮기지 않는다 */
  clearSync: boolean;
  /** 지울 기기 파일: 사진 폴더 안에 있던 예전 사진 (새 사진과 다른 파일만) */
  deleteFiles: string[];
}

/**
 * 사진을 바꾸거나 뺀 뒤 기기 상태를 어떻게 바꿀지.
 * - 서버 사진: 서버 사진만 남기고 기기 사진(옮기기 대기 포함)은 지운다
 * - 기기 사진: 서버 사진은 그대로(예전 서버면 원래 없다), 기기 사진을 새 파일로. 계정 모드는 다음 옮기기가 올린다
 * - 빼기: 둘 다 지운다
 * 예전 기기 파일은 앱 사진 폴더(photoDirUri) 안에 있을 때만 지운다 (선택기 캐시·샘플·웹 주소는 건드리지 않는다).
 */
export const planPhotoChange = (before: IPhotoBefore, change: TPhotoChange, photoDirUri: string): IPhotoChangePlan => {
  const nextUri = change.kind === 'local' ? change.uri : undefined;
  const item =
    change.kind === 'server'
      ? { image: change.image, imageUri: undefined }
      : change.kind === 'local'
        ? { image: before.image, imageUri: change.uri }
        : { image: undefined, imageUri: undefined };
  const dir = photoDirUri ? (photoDirUri.endsWith('/') ? photoDirUri : `${photoDirUri}/`) : '';
  const deleteFiles: string[] = [];
  for (const uri of [before.imageUri, before.localPhoto]) {
    if (!uri || !dir || !uri.startsWith(dir) || uri === nextUri || deleteFiles.includes(uri)) continue;
    deleteFiles.push(uri);
  }
  return { item, localPhoto: nextUri, clearSync: true, deleteFiles };
};

/** 기기에 새로 복사하는 사진 파일 이름에 붙일 버전. 같은 이름을 덮어쓰면 화면(expo-image)이 예전 사진을 계속 보여 준다 */
export const photoVersion = (now: number): string => now.toString(36);

// ── AI 다시 분석 ──

/** 새 사진을 올린 뒤 AI 다시 분석을 제안할지. 계정 모드는 서버 사진이 붙었을 때, 목업 실행은 게스트도 (기기 목업 분석) */
export const canOfferReanalysis = (o: { accountMode: boolean; hasServerImage: boolean; mockMode: boolean }): boolean =>
  o.accountMode ? o.hasServerImage : o.mockMode;

/** 다시 분석으로 바뀔 수 있는 항목. kind = 종류·세부 종류 한 쌍 */
export type TReanalysisField = 'kind' | 'colors' | 'pattern' | 'seasons' | 'styles' | 'formality';

export const REANALYSIS_FIELDS: TReanalysisField[] = ['kind', 'colors', 'pattern', 'seasons', 'styles', 'formality'];

type TAttrs = Pick<ItemDraft, 'category' | 'subType' | 'color' | 'colors' | 'pattern' | 'seasons' | 'styles' | 'formality'>;

const colorList = (d: Pick<TAttrs, 'color' | 'colors'>): string[] => (d.colors && d.colors.length > 0 ? d.colors : [d.color]);

/** 순서 없이 같은 목록인지 (계절·스타일) */
const sameSet = (a: readonly string[], b: readonly string[]): boolean => {
  const sa = new Set(a);
  const sb = new Set(b);
  return sa.size === sb.size && [...sa].every((x) => sb.has(x));
};

/** 순서까지 같은 목록인지 (색: 첫 번째가 주 색상) */
const sameList = (a: readonly string[], b: readonly string[]): boolean => a.length === b.length && a.every((x, i) => x === b[i]);

/** 지금 폼 값과 AI 제안을 비교해 달라지는 항목만 (이름·보유 상태·구입일·브랜드는 비교하지 않는다) */
export const reanalysisChanges = (current: TAttrs, suggested: TAttrs): TReanalysisField[] =>
  REANALYSIS_FIELDS.filter((f) => {
    switch (f) {
      case 'kind':
        return current.category !== suggested.category || current.subType !== suggested.subType;
      case 'colors':
        return !sameList(colorList(current), colorList(suggested));
      case 'pattern':
        return current.pattern !== suggested.pattern;
      case 'seasons':
        return !sameSet(current.seasons ?? [], suggested.seasons ?? []);
      case 'styles':
        return !sameSet(current.styles ?? [], suggested.styles ?? []);
      case 'formality':
        return current.formality !== suggested.formality;
    }
  });

/** 고른 항목만 제안 값으로 바꾼 폼 값. 이름은 부르는 쪽이 정한다 (자동 이름일 때만 새 종류·색으로) */
export const applyReanalysis = <T extends TAttrs>(current: T, suggested: TAttrs, fields: readonly TReanalysisField[]): T => {
  const next: T = { ...current };
  for (const f of fields) {
    if (f === 'kind') {
      next.category = suggested.category;
      next.subType = suggested.subType;
    } else if (f === 'colors') {
      const colors = colorList(suggested);
      next.colors = [...colors];
      next.color = colors[0];
    } else if (f === 'pattern') next.pattern = suggested.pattern;
    else if (f === 'seasons') next.seasons = [...suggested.seasons];
    else if (f === 'styles') next.styles = [...suggested.styles];
    else next.formality = suggested.formality;
  }
  return next;
};

/** 적용한 항목 → 폼의 "AI" 표시 칸 */
export const aiBadgeFields = (fields: readonly TReanalysisField[]): ('category' | 'color' | 'pattern')[] => {
  const out: ('category' | 'color' | 'pattern')[] = [];
  if (fields.includes('kind')) out.push('category');
  if (fields.includes('colors')) out.push('color');
  if (fields.includes('pattern')) out.push('pattern');
  return out;
};
