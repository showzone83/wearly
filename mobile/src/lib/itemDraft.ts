// 옷 등록 폼의 첫 값. 내 옷장 등록(item/new)과 옷장 도우미의 대리 등록(helper/new)이 함께 쓴다.
import { defaultName, type ItemDraft } from '@/components/ItemForm';
import type { IItemImage } from '@/data/types';
import { activeCodes, defaultStatus, isActiveCode, subTypesOf } from './codes';

/** 원하는 코드가 활성이면 그것, 아니면 첫 활성 코드 */
export function pickCode(options: string[], preferred: string): string {
  return options.includes(preferred) ? preferred : (options[0] ?? preferred);
}

/** 직접 입력의 첫 값: 네이비 니트. 코드가 비활성이면 각 그룹의 첫 활성 값으로 */
export function manualDraft(photo?: { uri: string; image?: IItemImage }): ItemDraft {
  const category = isActiveCode('CATEGORY', 'TOP') ? 'TOP' : pickCode(activeCodes('CATEGORY'), 'TOP');
  const subType = pickCode(subTypesOf(category), 'KNIT');
  const color = pickCode(activeCodes('COLOR'), 'navy');
  return {
    name: defaultName({ color, subType }),
    category,
    subType,
    color,
    colors: [color],
    pattern: pickCode(activeCodes('PATTERN'), 'SOLID'),
    seasons: ['FALL', 'WINTER'].filter((x) => isActiveCode('SEASON', x)),
    styles: ['NEAT'].filter((x) => isActiveCode('STYLE', x)),
    formality: 2,
    status: defaultStatus(),
    imageUri: photo?.uri,
    image: photo?.image,
  };
}
