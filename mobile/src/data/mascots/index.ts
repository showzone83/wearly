// 마스코트 그림 모음 (doc/12_디자인_테마.md 4장). 마스코트 2종 × 자세 6개 → 그림 파일.
// 그림은 디자인 보드(mobile/design/*.png)에서 잘라 온 PNG를 고치지 않고 그대로 쓴다(mobile/assets/mascots/).
// 순수 TS (RN·Expo·`@/` import 금지) — 관리자 사이트가 가져다 쓴다.
// 파일 경로 → 실제 이미지 연결은 쓰는 쪽이 한다: 앱은 components/mascotSources.ts(정적 require),
// 관리자 사이트는 src/lib/appTheme.ts(import.meta.glob).

import type { IMascotImage, TMascotId, TMascotPose } from './types';

const img = (file: string, width: number, height: number, scene = false): IMascotImage => ({ file, scene, width, height });

// 보드에 Closeta 그림은 두 장면(스플래시, 손 흔드는 모습)뿐이라 나머지 자세는 인사(hello) 그림을 쓴다.
const CLOSETA_HELLO = img('closeta/hello.png', 249, 200);

export const MASCOT_IMAGES: Record<TMascotId, Record<TMascotPose, IMascotImage>> = {
  nyang: {
    splash: img('nyang/splash.png', 708, 678, true),
    hello: img('nyang/hello.png', 249, 251),
    style: img('nyang/style.png', 247, 208),
    camera: img('nyang/camera.png', 266, 222),
    closet: img('nyang/closet.png', 828, 690, true),
    cheer: img('nyang/cheer.png', 423, 261),
  },
  closeta: {
    splash: img('closeta/splash.png', 750, 630, true),
    hello: CLOSETA_HELLO,
    style: CLOSETA_HELLO,
    camera: CLOSETA_HELLO,
    closet: CLOSETA_HELLO,
    cheer: CLOSETA_HELLO,
  },
};

export const mascotImage = (id: TMascotId, pose: TMascotPose): IMascotImage => MASCOT_IMAGES[id][pose];

/** 매핑에 쓰인 그림 파일 전체 (중복 없이) */
export const MASCOT_FILES: string[] = [
  ...new Set(Object.values(MASCOT_IMAGES).flatMap((poses) => Object.values(poses).map((i) => i.file))),
];

export type { IMascotImage, TMascotId, TMascotPose } from './types';
export { MASCOT_POSES } from './types';
