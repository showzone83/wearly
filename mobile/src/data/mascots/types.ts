// 마스코트 그림 형식 (doc/12_디자인_테마.md 4장). 순수 TS — 앱(expo-image)과 관리자 사이트(<img>)가
// 같은 파일 매핑으로 디자인 보드에서 잘라 온 PNG(mobile/assets/mascots/)를 그린다.

import type { TThemeId } from '../themes';

export type TMascotId = TThemeId;

/** 화면별 자세 (doc/12 4장 표) */
export type TMascotPose = 'splash' | 'hello' | 'style' | 'camera' | 'closet' | 'cheer';

export const MASCOT_POSES: TMascotPose[] = ['splash', 'hello', 'style', 'camera', 'closet', 'cheer'];

/** 자세 하나의 그림 파일 */
export interface IMascotImage {
  /** mobile/assets/mascots/ 아래 경로. 예: 'nyang/splash.png' */
  file: string;
  /**
   * true = 배경까지 있는 장면 그림(직사각형). 둥근 모서리 카드처럼 가로 폭에 맞춰 그린다(높이는 비율대로).
   * false = 배경을 지운 투명 그림. 정사각형 칸 안에 비율을 지켜 넣는다(contain).
   */
  scene: boolean;
  /** 원본 픽셀 크기 (장면 그림의 높이 계산용) */
  width: number;
  height: number;
}
