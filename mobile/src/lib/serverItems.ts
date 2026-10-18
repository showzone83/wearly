// 서버 DTO ↔ 앱 모델 변환. 서버 DTO는 @/api/types, 앱 모델은 @/data/types.
import type * as Api from '@/api/types';
import type { ItemDraft } from '@/components/ItemForm';
import type * as App from '@/data/types';
import { activeCodes, defaultStatus, isActiveCode, parentOf, subTypesOf } from './codes';

// 두 곳에 따로 정의한 타입이 어긋나면 여기서 타입 오류가 난다 (옷 속성은 양쪽 모두 코드 문자열)
type Same<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
const _sameEnums: [
  Same<Api.Category, App.Category>,
  Same<Api.SubType, App.SubType>,
  Same<Api.ColorKey, App.ColorKey>,
  Same<Api.Pattern, App.Pattern>,
  Same<Api.Season, App.Season>,
  Same<Api.StyleTag, App.StyleTag>,
  Same<Api.ItemStatus, App.ItemStatus>,
  Same<Api.RetireReason, App.RetireReason>,
  Same<Api.DressCode, App.Formality>,
  Same<Api.WorkType, App.WorkType>,
] = [true, true, true, true, true, true, true, true, true, true];
void _sameEnums;

/** @param imageUri 기기 사진 (photos[id]). 서버 사진은 dto.image에서 온다 */
export function itemFromDto(dto: Api.ItemDto, imageUri?: string): App.WardrobeItem {
  return {
    id: dto.id,
    name: dto.name,
    category: dto.category,
    subType: dto.subType,
    color: dto.color,
    pattern: dto.pattern,
    seasons: dto.seasons ?? [],
    styles: dto.styles ?? [],
    formality: dto.formality,
    status: dto.status,
    brand: dto.brand ?? undefined,
    wornCount: dto.wornCount ?? 0,
    lastWornAt: dto.lastWornAt ?? undefined,
    createdAt: dto.createdAt,
    imageUri,
    image: imageFromDto(dto.image),
    ...retireFromDto(dto),
  };
}

/** 서버 사진 참조. 예전 서버(필드 없음)·사진 없음(null)·주소가 빈 응답은 undefined */
export const imageFromDto = (image: Api.ImageRef | null | undefined): App.IItemImage | undefined =>
  image?.imageId && (image.url || image.thumbUrl)
    ? { imageId: image.imageId, url: image.url, thumbUrl: image.thumbUrl, expiresAt: image.expiresAt }
    : undefined;

/** 사용 중지 필드만 (예전 서버 응답에는 없다 → 사용 중) */
export const retireFromDto = (
  dto: Pick<Api.ItemDto, 'retiredAt' | 'retireReason' | 'retireMemo'>,
): Pick<App.WardrobeItem, 'retiredAt' | 'retireReason' | 'retireMemo'> => ({
  retiredAt: dto.retiredAt || undefined,
  retireReason: dto.retiredAt ? dto.retireReason || undefined : undefined,
  retireMemo: dto.retiredAt ? dto.retireMemo || undefined : undefined,
});

/**
 * 등록·수정 폼 값 → 서버 입력. 사진은 넣지 않는다: 기기 사진(imageUri)은 서버로 가지 않고,
 * 서버 사진(imageId)은 바꿀 때만 부르는 쪽(account.createItem·saveItem)이 따로 넣는다
 */
export function inputFromDraft(d: ItemDraft): Api.ItemInput {
  return {
    name: d.name,
    category: d.category,
    subType: d.subType,
    color: d.color,
    pattern: d.pattern,
    seasons: d.seasons,
    styles: d.styles,
    formality: d.formality,
    status: d.status,
    brand: d.brand?.trim() || null,
  };
}

/** 샘플 옷장(착용 횟수·마지막 착용일 포함) → 서버 입력 */
export function inputFromItem(i: App.WardrobeItem): Api.ItemInput {
  return { ...inputFromDraft(i), wornCount: i.wornCount, lastWornAt: i.lastWornAt ?? null };
}

export function profileFromDto(dto: Api.ProfileDto): App.Profile {
  return {
    name: dto.name,
    dressCodes: dto.dressCodes,
    moods: dto.moods,
    colors: dto.colors,
    workType: dto.workType,
    workDays: dto.workDays,
    workRule: dto.workRule,
    offRule: dto.offRule,
    notify: { morning: !!dto.notify?.morning, evening: !!dto.notify?.evening, dMinus1: !!dto.notify?.dMinus1 },
    ageRange: dto.ageRange ?? null,
    bodyType: dto.bodyType ?? null,
    heightCm: dto.heightCm ?? null,
    fitPreference: dto.fitPreference ?? null,
  };
}

export function profileToDto(p: App.Profile, onboarded: boolean): Api.ProfileDto {
  return {
    name: p.name,
    dressCodes: p.dressCodes,
    moods: p.moods,
    colors: p.colors,
    workType: p.workType,
    workDays: p.workDays,
    workRule: p.workRule,
    offRule: p.offRule,
    notify: { ...p.notify },
    ageRange: p.ageRange ?? null,
    bodyType: p.bodyType ?? null,
    heightCm: p.heightCm ?? null,
    fitPreference: p.fitPreference ?? null,
    onboarded,
  };
}

/**
 * AI 분석 제안의 종류·세부 종류 짝을 앱 코드 기준으로 맞춘다.
 * 1. 세부 종류가 아는 활성 코드면 그 상위 종류를 따른다 (세부 종류가 더 구체적이다)
 * 2. 아니면 종류가 아는 활성 코드일 때 그 종류의 첫 세부 종류
 * 3. 둘 다 모르면 첫 활성 종류와 그 첫 세부 종류
 * 서버가 앱 캐시보다 새 코드를 줬다면(캐시에 없는 코드) 1·2에서 걸러져 아는 값으로 바뀐다.
 */
export function fixCategoryPair(category: string, subType: string): { category: string; subType: string } {
  const parent = isActiveCode('SUB_TYPE', subType) ? parentOf(subType) : null;
  if (parent && isActiveCode('CATEGORY', parent)) return { category: parent, subType };
  const cat = isActiveCode('CATEGORY', category) ? category : (activeCodes('CATEGORY').find((c) => subTypesOf(c).length > 0) ?? category);
  return { category: cat, subType: subTypesOf(cat)[0] ?? subType };
}

/** AI 분석 제안 → 등록 폼 값. 종류·세부 종류 짝은 코드 기준으로 맞춘다 */
export function draftFromSuggestion(s: Api.AnalyzeSuggestion, imageUri: string, image?: App.IItemImage): ItemDraft {
  const { category, subType } = fixCategoryPair(s.category, s.subType);
  return {
    name: s.name,
    category,
    subType,
    color: s.color,
    pattern: s.pattern,
    seasons: s.seasons,
    styles: s.styles,
    formality: s.formality,
    status: defaultStatus(),
    imageUri,
    image,
  };
}
