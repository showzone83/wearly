// 마스코트 그림 파일(data/mascots의 file) → 번들 이미지. Metro는 정적 require만 묶으므로 파일마다 한 줄씩 적는다.
// 그림을 더하면 data/mascots/index.ts의 매핑과 여기를 함께 고친다.
export const MASCOT_SOURCES: Record<string, number> = {
  'nyang/splash.png': require('../../assets/mascots/nyang/splash.png'),
  'nyang/hello.png': require('../../assets/mascots/nyang/hello.png'),
  'nyang/style.png': require('../../assets/mascots/nyang/style.png'),
  'nyang/camera.png': require('../../assets/mascots/nyang/camera.png'),
  'nyang/closet.png': require('../../assets/mascots/nyang/closet.png'),
  'nyang/cheer.png': require('../../assets/mascots/nyang/cheer.png'),
  'closeta/splash.png': require('../../assets/mascots/closeta/splash.png'),
  'closeta/hello.png': require('../../assets/mascots/closeta/hello.png'),
};
