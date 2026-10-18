// 목업 AI 분석: 실제 모델 대신 사진마다 고정된 제안값을 만든다.
// 목업 실행(npm run start:mock)의 게스트 모드와, 그때 서버에 닿지 못한 경우에만 쓴다 (옷 등록·옷 상세 사진 다시 분석).
import type { ItemDraft } from '@/components/ItemForm';
import type { Category, ColorKey, SubType } from '@/data/types';
import { activeCodes, colorLabel, defaultStatus, subTypeLabel, subTypeMeta, subTypesOf } from './codes';

const hash = (str: string): number => {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h;
};

/** 원하는 코드가 활성이면 그것, 아니면 첫 활성 코드 */
const pick = (options: string[], preferred: string): string =>
  options.includes(preferred) ? preferred : (options[0] ?? preferred);

export const mockAnalyze = (uri: string): ItemDraft => {
  const h = hash(uri);
  // 활성 코드에서 고른다. 상의는 두 번 넣어 자주 나오게 한다
  const cats: Category[] = ['TOP', 'TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES', 'BAG'].filter((c) => subTypesOf(c).length > 0);
  if (cats.length === 0) cats.push(...activeCodes('CATEGORY').filter((c) => subTypesOf(c).length > 0));
  const category = cats[h % cats.length];
  const subs = subTypesOf(category);
  // h는 32비트 부호 없는 값이라 >>(부호 있는 시프트)를 쓰면 음수 인덱스가 나온다
  const subType: SubType = subs[(h >>> 3) % subs.length];
  const palette: ColorKey[] = activeCodes('COLOR').slice(0, 8);
  const color = palette[(h >>> 7) % palette.length];
  return {
    // ItemForm.defaultName과 같은 규칙 ("네이비 니트")
    name: `${colorLabel(color)} ${subTypeLabel(subType)}`,
    category,
    subType,
    color,
    colors: [color],
    pattern: pick(activeCodes('PATTERN'), 'SOLID'),
    seasons: subTypeMeta(subType).warmth >= 2 ? ['FALL', 'WINTER'] : ['SPRING', 'FALL'],
    styles: [pick(activeCodes('STYLE'), 'MINIMAL')],
    formality: 2,
    status: defaultStatus(),
    imageUri: uri,
  };
};
