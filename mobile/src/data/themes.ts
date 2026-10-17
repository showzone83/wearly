// 앱 디자인 테마 (doc/12_디자인_테마.md). React Native·Expo·`@/` import가 없는 순수 TS라
// 관리자 사이트도 그대로 가져다 미리보기에 쓴다.
// 색은 mobile/design의 두 보드에서 뽑았다. 값을 바꿀 때는 두 보드와 나란히 놓고 확인한다.

export type TThemeId = 'nyang' | 'closeta';

export interface IThemeColors {
  /** 화면 바탕 */
  bg: string;
  /** 카드·시트·입력칸 바탕 */
  surface: string;
  /** 연한 강조 바탕 (팁 상자, 비선택 영역) */
  surfaceAlt: string;
  /** 테두리·구분선 */
  border: string;
  /** 주 버튼·선택 칩·활성 탭 */
  primary: string;
  /** 주 버튼 눌림 */
  primaryPressed: string;
  /** 주 색의 연한 바탕 (배지, 선택된 카드 테두리 안쪽) */
  primarySoft: string;
  /** 주 색 위 글자 */
  onPrimary: string;
  /** 제목·본문 */
  text: string;
  /** 보조 글자 */
  textSub: string;
  /** 흐린 글자·비활성 */
  textMuted: string;
  /** 포인트 (하트, 반짝이, 배지) */
  accent: string;
  accentSoft: string;
  /** 비선택 칩 바탕과 테두리 */
  chip: string;
  chipBorder: string;
  /** 하단 탭 바 */
  tabBar: string;
  danger: string;
  /** 그림자 색 */
  shadow: string;
}

export interface ITheme {
  id: TThemeId;
  /** 관리자 화면에 보이는 이름 */
  name: string;
  /** 한 줄 설명 */
  description: string;
  /** 마스코트 (data/mascots) */
  mascot: TThemeId;
  colors: IThemeColors;
  radius: { sm: number; md: number; lg: number; xl: number; pill: number };
}

const RADIUS = { sm: 10, md: 16, lg: 22, xl: 28, pill: 999 };

export const THEMES: Record<TThemeId, ITheme> = {
  nyang: {
    id: 'nyang',
    name: '냥깨비',
    description: '따뜻한 크림색 바탕에 짙은 초록, 장롱 속 옷을 깨우는 고양이 도깨비 마스코트',
    mascot: 'nyang',
    colors: {
      bg: '#FBF3E7',
      surface: '#FFFCF6',
      surfaceAlt: '#F6ECDF',
      border: '#EADFD1',
      primary: '#2E6248',
      primaryPressed: '#214A36',
      primarySoft: '#E3EEE6',
      onPrimary: '#FFFFFF',
      text: '#24312A',
      textSub: '#5E6B63',
      textMuted: '#9AA39C',
      accent: '#F2A541',
      accentSoft: '#FCE9CF',
      chip: '#FFFFFF',
      chipBorder: '#E7DCCD',
      tabBar: '#FFFBF4',
      danger: '#C8553D',
      shadow: '#5A4325',
    },
    radius: RADIUS,
  },
  closeta: {
    id: 'closeta',
    name: 'Closeta',
    description: '밝은 아이보리 바탕에 세이지 초록, 초록 모자를 쓴 하얀 캐릭터',
    mascot: 'closeta',
    colors: {
      bg: '#FBF9F5',
      surface: '#FFFFFF',
      surfaceAlt: '#F1F5F0',
      border: '#E6E2DA',
      primary: '#2F7058',
      primaryPressed: '#235946',
      primarySoft: '#E4EFE8',
      onPrimary: '#FFFFFF',
      text: '#163F3D',
      textSub: '#5C6B69',
      textMuted: '#9AA5A3',
      accent: '#E9B949',
      accentSoft: '#FBF1D3',
      chip: '#FFFFFF',
      chipBorder: '#E2E5E0',
      tabBar: '#FFFFFF',
      danger: '#C8553D',
      shadow: '#33433F',
    },
    radius: RADIUS,
  },
};

export const THEME_IDS = Object.keys(THEMES) as TThemeId[];
export const DEFAULT_THEME_ID: TThemeId = 'nyang';

export const isThemeId = (v: unknown): v is TThemeId => typeof v === 'string' && v in THEMES;
