// 마스코트 그림 모음 (doc/12_디자인_테마.md 4장). 마스코트 2종 × 자세 6개, viewBox는 모두 "0 0 200 200".
// 순수 TS (RN·Expo·`@/` import 금지) — 관리자 사이트가 가져다 쓴다.

import { CLOSETA } from './closeta';
import { NYANG } from './nyang';
import type { IMascotArt, TMascotId, TMascotPose } from './types';

export const MASCOTS: Record<TMascotId, Record<TMascotPose, IMascotArt>> = {
  nyang: NYANG,
  closeta: CLOSETA,
};

export const mascotArt = (id: TMascotId, pose: TMascotPose): IMascotArt => MASCOTS[id][pose];

export type { IMascotArt, IMascotShape, TMascotId, TMascotPose } from './types';
export { MASCOT_POSES } from './types';
