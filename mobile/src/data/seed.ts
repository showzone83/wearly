import { addDays, todayKey } from '@/lib/date';
import { purchaseFields } from '@/lib/itemAttrs';
import { samplePhotoUri, type TSamplePhoto } from '@/lib/samplePhotos';
import type { Profile, WardrobeItem } from './types';

type SeedItem = Omit<WardrobeItem, 'createdAt' | 'lastWornAt' | 'imageUri' | 'purchasedAt' | 'purchasedAtEstimated'> & {
  wornDaysAgo?: number;
  /** 구입일(오늘에서 며칠 전). 없으면 모름 → 등록일 */
  boughtDaysAgo?: number;
  /** 디자인 보드 옷장 격자에서 잘라 온 사진 (lib/samplePhotos). 없으면 일러스트로 보인다 */
  photo?: TSamplePhoto;
};

const ALL_SEASONS: WardrobeItem['seasons'] = ['SPRING', 'SUMMER', 'FALL', 'WINTER'];

// 샘플 옷장: 디자인 보드(mobile/design/*.png) 옷장 격자의 옷 사진 18장 + 출근 코디가 돌도록 더한 사진 없는 옷 6벌.
// 앞의 9벌은 냥깨비 보드, 다음 9벌은 Closeta 보드의 옷장 순서다.
// 값은 코드 초기값(doc/06_코드_초기값.json)에 있는 코드만 쓴다. 서버 계정이면 bulk 등록 때 서버가 코드를 검증한다.
// 줄무늬 옷은 바탕색을 보조 색(colors[1])으로 넣어 여러 색 옷을 보여 준다.
const SEED: SeedItem[] = [
  // ── 냥깨비 보드 ──
  { id: 's-top-1', photo: 'nyang_cream_knit', name: '크림 니트', category: 'TOP', subType: 'KNIT', color: 'ivory', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['MINIMAL', 'CASUAL'], formality: 2, status: 'OWNED', wornCount: 5, wornDaysAgo: 3 },
  { id: 's-top-2', photo: 'nyang_stripe_knit', name: '네이비 스트라이프 니트', category: 'TOP', subType: 'KNIT', color: 'navy', colors: ['navy', 'white'], pattern: 'STRIPE', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['CASUAL', 'CLASSIC'], formality: 1, status: 'OWNED', wornCount: 3, wornDaysAgo: 9 },
  { id: 's-out-1', photo: 'nyang_olive_jacket', name: '카키 테일러드 재킷', category: 'OUTER', subType: 'JACKET', color: 'khaki', pattern: 'SOLID', seasons: ['SPRING', 'FALL'], styles: ['CLASSIC', 'NEAT'], formality: 2, status: 'OWNED', wornCount: 4, wornDaysAgo: 5 },
  { id: 's-bot-1', photo: 'nyang_jeans', name: '진청 스트레이트 청바지', category: 'BOTTOM', subType: 'JEANS', color: 'denim', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['CASUAL', 'MINIMAL'], formality: 1, status: 'OWNED', wornCount: 6, wornDaysAgo: 4 },
  { id: 's-bot-2', photo: 'nyang_black_skirt', name: '블랙 플레어 스커트', category: 'BOTTOM', subType: 'SKIRT', color: 'black', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['FEMININE', 'CLASSIC'], formality: 2, status: 'OWNED', wornCount: 2, wornDaysAgo: 12 },
  { id: 's-out-2', photo: 'nyang_beige_coat', name: '베이지 더블 코트', category: 'OUTER', subType: 'COAT', color: 'beige', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['CLASSIC', 'MINIMAL'], formality: 3, status: 'OWNED', wornCount: 2, wornDaysAgo: 200, boughtDaysAgo: 400 },
  { id: 's-bag-1', photo: 'nyang_beige_bag', name: '베이지 핸드백', category: 'BAG', subType: 'TOTE', color: 'beige', pattern: 'SOLID', seasons: ALL_SEASONS, styles: ['CLASSIC', 'FEMININE'], formality: 2, status: 'OWNED', wornCount: 10, wornDaysAgo: 2 },
  { id: 's-sho-1', photo: 'nyang_beige_sneakers', name: '베이지 캔버스 스니커즈', category: 'SHOES', subType: 'SNEAKER', color: 'beige', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['CASUAL'], formality: 1, status: 'OWNED', wornCount: 3, wornDaysAgo: 8 },
  { id: 's-sho-2', photo: 'nyang_white_sneakers', name: '화이트 스니커즈', category: 'SHOES', subType: 'SNEAKER', color: 'white', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['CASUAL', 'MINIMAL'], formality: 1, status: 'OWNED', wornCount: 9, wornDaysAgo: 4 },

  // ── Closeta 보드 ──
  { id: 's-top-3', photo: 'closeta_cable_knit', name: '오트밀 꽈배기 니트', category: 'TOP', subType: 'KNIT', color: 'beige', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['CASUAL', 'MINIMAL'], formality: 1, status: 'OWNED', wornCount: 1, wornDaysAgo: 30 },
  { id: 's-top-4', photo: 'closeta_lightblue_shirt', name: '라이트블루 셔츠', category: 'TOP', subType: 'SHIRT', color: 'lightblue', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['NEAT', 'CLASSIC'], formality: 2, status: 'OWNED', wornCount: 7, wornDaysAgo: 6 },
  { id: 's-out-3', photo: 'closeta_cream_coat', name: '아이보리 싱글 코트', category: 'OUTER', subType: 'COAT', color: 'ivory', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['MINIMAL', 'CLASSIC'], formality: 3, status: 'OWNED', wornCount: 0 },
  { id: 's-bot-3', photo: 'closeta_jeans_light', name: '중청 스트레이트 청바지', category: 'BOTTOM', subType: 'JEANS', color: 'denim', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['CASUAL', 'STREET'], formality: 1, status: 'OWNED', wornCount: 4, wornDaysAgo: 11 },
  { id: 's-bot-4', photo: 'closeta_cream_skirt', name: '아이보리 플리츠 롱스커트', category: 'BOTTOM', subType: 'LONG_SKIRT', color: 'ivory', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['FEMININE', 'MINIMAL'], formality: 2, status: 'OWNED', wornCount: 1, wornDaysAgo: 40 },
  { id: 's-top-5', photo: 'closeta_stripe_tee', name: '네이비 스트라이프 긴소매 티', category: 'TOP', subType: 'LONG_TEE', color: 'navy', colors: ['navy', 'ivory'], pattern: 'STRIPE', seasons: ['SPRING', 'FALL'], styles: ['CASUAL', 'MINIMAL'], formality: 1, status: 'LAUNDRY', wornCount: 6, wornDaysAgo: 1 },
  // 보드 사진은 니트와 함께 놓인 세트 차림이지만 옷장에는 하의(연청 쇼츠)로 둔다. 여름 옷이라 가을·겨울 추천에는 잘 안 나온다
  { id: 's-bot-5', photo: 'closeta_knit_shorts_set', name: '연청 데님 쇼츠', category: 'BOTTOM', subType: 'SHORTS', color: 'lightblue', pattern: 'SOLID', seasons: ['SUMMER'], styles: ['CASUAL'], formality: 1, status: 'OWNED', wornCount: 2, wornDaysAgo: 70 },
  { id: 's-bot-6', photo: 'closeta_brown_skirt', name: '브라운 플레어 스커트', category: 'BOTTOM', subType: 'SKIRT', color: 'brown', pattern: 'SOLID', seasons: ['FALL', 'WINTER'], styles: ['CLASSIC', 'FEMININE'], formality: 2, status: 'OWNED', wornCount: 0 },
  { id: 's-dre-1', photo: 'closeta_gray_dress', name: '차콜 롱 원피스', category: 'DRESS', subType: 'DRESS', color: 'charcoal', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['MINIMAL', 'CLASSIC'], formality: 2, status: 'OWNED', wornCount: 1, wornDaysAgo: 21 },

  // ── 사진 없는 옷: 출근 코디(단정·격식)가 돌도록 셔츠·슬랙스·블레이저·구두·숄더백·시계 ──
  { id: 's-top-6', name: '화이트 옥스포드 셔츠', category: 'TOP', subType: 'SHIRT', color: 'white', pattern: 'SOLID', seasons: ['SPRING', 'SUMMER', 'FALL'], styles: ['NEAT', 'CLASSIC'], formality: 3, status: 'OWNED', wornCount: 8, wornDaysAgo: 2 },
  { id: 's-bot-7', name: '베이지 슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'beige', pattern: 'SOLID', seasons: ['SPRING', 'FALL'], styles: ['NEAT', 'MINIMAL'], formality: 2, status: 'OWNED', wornCount: 7, wornDaysAgo: 3 },
  { id: 's-out-4', name: '네이비 블레이저', category: 'OUTER', subType: 'BLAZER', color: 'navy', pattern: 'SOLID', seasons: ['SPRING', 'FALL', 'WINTER'], styles: ['CLASSIC', 'NEAT'], formality: 3, status: 'OWNED', wornCount: 3, wornDaysAgo: 10 },
  { id: 's-sho-3', name: '블랙 로퍼', category: 'SHOES', subType: 'LOAFER', color: 'black', pattern: 'SOLID', seasons: ALL_SEASONS, styles: ['NEAT', 'CLASSIC'], formality: 3, status: 'OWNED', wornCount: 12, wornDaysAgo: 2 },
  { id: 's-bag-2', name: '블랙 숄더백', category: 'BAG', subType: 'SHOULDER', color: 'black', pattern: 'SOLID', seasons: ALL_SEASONS, styles: ['MINIMAL', 'NEAT'], formality: 2, status: 'OWNED', wornCount: 7, wornDaysAgo: 5 },
  { id: 's-acc-1', name: '실버 메탈 시계', category: 'ACC', subType: 'WATCH', color: 'gray', pattern: 'SOLID', seasons: ALL_SEASONS, styles: ['CLASSIC', 'MINIMAL'], formality: 2, status: 'OWNED', wornCount: 14, wornDaysAgo: 2 },
];

/** 샘플 옷장 벌 수 (온보딩 안내 문구) */
export const SAMPLE_ITEM_COUNT = SEED.length;

export function seedItems(): WardrobeItem[] {
  const today = todayKey();
  const createdAt = addDays(today, -60);
  return SEED.map(({ wornDaysAgo, boughtDaysAgo, photo, ...rest }) => ({
    ...rest,
    imageUri: photo ? samplePhotoUri(photo) : undefined,
    lastWornAt: wornDaysAgo !== undefined && rest.wornCount > 0 ? addDays(today, -wornDaysAgo) : undefined,
    createdAt,
    // 구입일을 모르는 옷은 등록일 (서버 계정이면 bulk 등록 때 서버가 등록일로 다시 정한다)
    ...purchaseFields(boughtDaysAgo !== undefined ? addDays(today, -boughtDaysAgo) : undefined, createdAt),
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

