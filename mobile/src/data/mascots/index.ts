// 마스코트 그림 모음. 지금은 자리 표시용 임시 그림이고, 마스코트 작업이 실제 그림으로 바꾼다.
// 순수 TS (RN·Expo·`@/` import 금지) — 관리자 사이트가 가져다 쓴다.

import type { IMascotArt, TMascotId, TMascotPose } from './types';

const placeholder = (body: string, accent: string): IMascotArt => ({
  viewBox: '0 0 200 200',
  shapes: [
    { type: 'ellipse', cx: 100, cy: 120, rx: 62, ry: 56, fill: body },
    { type: 'circle', cx: 80, cy: 112, r: 7, fill: '#24312A' },
    { type: 'circle', cx: 120, cy: 112, r: 7, fill: '#24312A' },
    { type: 'circle', cx: 100, cy: 60, r: 10, fill: accent },
  ],
});

const all = (art: IMascotArt): Record<TMascotPose, IMascotArt> => ({
  splash: art,
  hello: art,
  style: art,
  camera: art,
  closet: art,
  cheer: art,
});

export const MASCOTS: Record<TMascotId, Record<TMascotPose, IMascotArt>> = {
  nyang: all(placeholder('#6E9A7E', '#F2A541')),
  closeta: all(placeholder('#FFFFFF', '#667E63')),
};

export const mascotArt = (id: TMascotId, pose: TMascotPose): IMascotArt => MASCOTS[id][pose];

export type { IMascotArt, IMascotShape, TMascotId, TMascotPose } from './types';
export { MASCOT_POSES } from './types';
