// 서버 DTO ↔ 앱 모델 변환. 서버 DTO는 @/api/types, 앱 모델은 @/data/types.
import type * as Api from '@/api/types';
import type { ItemDraft } from '@/components/ItemForm';
import { SUBTYPES_BY_CATEGORY } from '@/data/constants';
import type * as App from '@/data/types';

// 두 곳에 따로 정의한 열거형 문자열이 어긋나면 여기서 타입 오류가 난다
type Same<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
const _sameEnums: [
  Same<Api.Category, App.Category>,
  Same<Api.SubType, App.SubType>,
  Same<Api.ColorKey, App.ColorKey>,
  Same<Api.Pattern, App.Pattern>,
  Same<Api.Season, App.Season>,
  Same<Api.StyleTag, App.StyleTag>,
  Same<Api.ItemStatus, App.ItemStatus>,
  Same<Api.DressCode, App.Formality>,
  Same<Api.WorkType, App.WorkType>,
] = [true, true, true, true, true, true, true, true, true];
void _sameEnums;

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
  };
}

/** 등록·수정 폼 값 → 서버 입력. 사진(imageUri)은 서버에 보내지 않는다 */
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
    onboarded,
  };
}

/** AI 분석 제안 → 등록 폼 값. 종류·세부 종류 짝이 어긋나면 그 종류의 첫 세부 종류로 맞춘다 */
export function draftFromSuggestion(s: Api.AnalyzeSuggestion, imageUri: string): ItemDraft {
  const subs = SUBTYPES_BY_CATEGORY[s.category] ?? SUBTYPES_BY_CATEGORY.TOP;
  const category = SUBTYPES_BY_CATEGORY[s.category] ? s.category : 'TOP';
  return {
    name: s.name,
    category,
    subType: subs.includes(s.subType) ? s.subType : subs[0],
    color: s.color,
    pattern: s.pattern,
    seasons: s.seasons,
    styles: s.styles,
    formality: s.formality,
    status: 'OWNED',
    imageUri,
  };
}
