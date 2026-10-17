// 마스코트 그림 데이터 형식 (doc/12_디자인_테마.md 4장). 순수 TS — 앱(react-native-svg)과
// 관리자 사이트(DOM SVG)가 같은 데이터로 그린다.

import type { TThemeId } from '../themes';

export type TMascotId = TThemeId;

/** 화면별 자세 (doc/12 4장 표) */
export type TMascotPose = 'splash' | 'hello' | 'style' | 'camera' | 'closet' | 'cheer';

export const MASCOT_POSES: TMascotPose[] = ['splash', 'hello', 'style', 'camera', 'closet', 'cheer'];

/** 그림 한 조각. 위에서부터 순서대로 그린다 (뒤에 오는 것이 위에 그려진다) */
export interface IMascotShape {
  type: 'path' | 'circle' | 'ellipse' | 'rect';
  /** path */
  d?: string;
  /** circle·ellipse */
  cx?: number;
  cy?: number;
  r?: number;
  rx?: number;
  ry?: number;
  /** rect */
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  strokeLinecap?: 'round' | 'butt' | 'square';
  strokeLinejoin?: 'round' | 'miter' | 'bevel';
  opacity?: number;
  /** SVG transform 문자열 (예: "rotate(-8 50 50)") */
  transform?: string;
}

export interface IMascotArt {
  /** 예: "0 0 200 200" */
  viewBox: string;
  shapes: IMascotShape[];
}
