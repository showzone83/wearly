import { addDays, todayKey } from '@/lib/date';
import type { Profile, WardrobeItem } from './types';

type SeedItem = Omit<WardrobeItem, 'createdAt' | 'lastWornAt'> & { wornDaysAgo?: number };

// 기획서 목업(p.40 내 옷장)의 옷장 구성을 바탕으로 한 샘플 데이터
const SEED: SeedItem[] = [
  { id: 's-top-1', name: '네이비 니트', category: 'TOP', subType: 'KNIT', color: 'navy', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['NEAT', 'MINIMAL'], formality: 2, status: 'OWNED', wornCount: 5, wornDaysAgo: 2 },
  { id: 's-top-2', name: '화이트 옥스포드 셔츠', category: 'TOP', subType: 'SHIRT', color: 'white', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'SUMMER'], styles: ['NEAT', 'CLASSIC'], formality: 3, status: 'OWNED', wornCount: 8, wornDaysAgo: 4 },
  { id: 's-top-3', name: '라이트블루 셔츠', category: 'TOP', subType: 'SHIRT', color: 'lightblue', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['NEAT', 'CLASSIC'], formality: 2, status: 'OWNED', wornCount: 2, wornDaysAgo: 16 },
  { id: 's-top-4', name: '아이보리 니트', category: 'TOP', subType: 'KNIT', color: 'ivory', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['MINIMAL', 'CASUAL'], formality: 2, status: 'OWNED', wornCount: 3, wornDaysAgo: 9 },
  { id: 's-top-5', name: '그레이 티셔츠', category: 'TOP', subType: 'TEE', color: 'gray', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['CASUAL', 'MINIMAL'], formality: 1, status: 'LAUNDRY', wornCount: 6, wornDaysAgo: 1 },
  { id: 's-top-6', name: '네이비 스트라이프 셔츠', category: 'TOP', subType: 'SHIRT', color: 'navy', pattern: 'STRIPE', seasons: ['SPRING', 'FALL'], styles: ['CLASSIC', 'CASUAL'], formality: 2, status: 'OWNED', wornCount: 0 },
  { id: 's-bot-1', name: '베이지 슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'beige', pattern: 'SOLID', seasons: ['SPRING', 'FALL'], styles: ['NEAT', 'MINIMAL'], formality: 2, status: 'OWNED', wornCount: 7, wornDaysAgo: 3 },
  { id: 's-bot-2', name: '차콜 와이드 슬랙스', category: 'BOTTOM', subType: 'WIDE', color: 'charcoal', pattern: 'SOLID', seasons: ['FALL', 'WINTER', 'SPRING'], styles: ['MINIMAL', 'NEAT'], formality: 2, status: 'OWNED', wornCount: 4, wornDaysAgo: 6 },
  { id: 's-bot-3', name: '블랙 슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'black', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['NEAT', 'CLASSIC'], formality: 3, status: 'OWNED', wornCount: 5, wornDaysAgo: 8 },
  { id: 's-bot-4', name: '카키 치노 팬츠', category: 'BOTTOM', subType: 'CHINO', color: 'khaki', pattern: 'SOLID', seasons: ['SPRING', 'FALL'], styles: ['CASUAL'], formality: 1, status: 'OWNED', wornCount: 1, wornDaysAgo: 34 },
  { id: 's-out-1', name: '베이지 재킷', category: 'OUTER', subType: 'JACKET', color: 'beige', pattern: 'SOLID', seasons: ['SPRING', 'FALL'], styles: ['CLASSIC', 'NEAT'], formality: 2, status: 'OWNED', wornCount: 4, wornDaysAgo: 5 },
  { id: 's-out-2', name: '그레이 가디건', category: 'OUTER', subType: 'CARDIGAN', color: 'gray', pattern: 'SOLID', seasons: ['SPRING', 'FALL'], styles: ['MINIMAL', 'CASUAL'], formality: 1, status: 'OWNED', wornCount: 0 },
  { id: 's-out-3', name: '네이비 블레이저', category: 'OUTER', subType: 'JACKET', color: 'navy', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['CLASSIC', 'NEAT'], formality: 3, status: 'OWNED', wornCount: 3, wornDaysAgo: 12 },
  { id: 's-out-4', name: '카멜 코트', category: 'OUTER', subType: 'COAT', color: 'camel', pattern: 'SOLID', seasons: ['WINTER', 'FALL'], styles: ['CLASSIC', 'MINIMAL'], formality: 3, status: 'OWNED', wornCount: 2, wornDaysAgo: 200 },
  { id: 's-sho-1', name: '블랙 로퍼', category: 'SHOES', subType: 'LOAFER', color: 'black', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL', 'WINTER'], styles: ['NEAT', 'CLASSIC'], formality: 3, status: 'OWNED', wornCount: 12, wornDaysAgo: 2 },
  { id: 's-sho-2', name: '화이트 스니커즈', category: 'SHOES', subType: 'SNEAKER', color: 'white', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['CASUAL', 'MINIMAL'], formality: 1, status: 'OWNED', wornCount: 9, wornDaysAgo: 4 },
  { id: 's-sho-3', name: '브라운 더비 로퍼', category: 'SHOES', subType: 'LOAFER', color: 'brown', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['CLASSIC'], formality: 2, status: 'OWNED', wornCount: 3, wornDaysAgo: 10 },
  { id: 's-bag-1', name: '브라운 토트백', category: 'BAG', subType: 'TOTE', color: 'brown', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL', 'WINTER'], styles: ['CLASSIC', 'MINIMAL'], formality: 2, status: 'OWNED', wornCount: 10, wornDaysAgo: 2 },
  { id: 's-bag-2', name: '블랙 숄더백', category: 'BAG', subType: 'SHOULDER', color: 'black', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL', 'WINTER'], styles: ['MINIMAL', 'NEAT'], formality: 2, status: 'OWNED', wornCount: 7, wornDaysAgo: 5 },
];

export function seedItems(): WardrobeItem[] {
  const today = todayKey();
  return SEED.map(({ wornDaysAgo, ...rest }) => ({
    ...rest,
    lastWornAt: wornDaysAgo !== undefined && rest.wornCount > 0 ? addDays(today, -wornDaysAgo) : undefined,
    createdAt: addDays(today, -60),
  }));
}

export const DEFAULT_PROFILE: Profile = {
  name: '웨어리 사용자',
  dressCodes: ['BIZ_CASUAL'],
  moods: ['NEAT', 'MINIMAL'],
  colors: ['navy', 'beige', 'white'],
  workType: 'WEEKDAY',
  workDays: [1, 2, 3, 4, 5],
  workRule: true,
  offRule: true,
  notify: { morning: true, evening: true, dMinus1: true },
};

