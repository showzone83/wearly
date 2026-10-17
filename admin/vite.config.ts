import react from '@vitejs/plugin-react';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

// 옷 일러스트 모양은 앱(mobile/)의 순수 TS 데이터를 그대로 가져다 쓴다 (doc/07 5장).
// 앱 파일이 없으면(앱 작업 전 등) 관리자 사이트 안의 대체 파일로 빌드한다. tsconfig.app.json의 paths도 같은 순서다.
const mobileData = fileURLToPath(new URL('../mobile/src/data', import.meta.url));
const garmentShapes = fileURLToPath(new URL('../mobile/src/data/garmentShapes.ts', import.meta.url));
const garmentFallback = fileURLToPath(new URL('./src/lib/garmentShapesFallback.ts', import.meta.url));
// 앱 디자인 테마 값과 마스코트 그림도 같은 폴더의 순수 TS를 그대로 가져온다 (doc/12 2·4·6장). 대체 파일은 없다.
const appThemes = fileURLToPath(new URL('../mobile/src/data/themes.ts', import.meta.url));
const appMascots = fileURLToPath(new URL('../mobile/src/data/mascots/index.ts', import.meta.url));
// 마스코트 그림은 디자인 보드에서 잘라 온 PNG 폴더를 그대로 쓴다 (src/lib/appTheme.ts의 import.meta.glob)
const appMascotImages = fileURLToPath(new URL('../mobile/assets/mascots', import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@garment-shapes': existsSync(garmentShapes) ? garmentShapes : garmentFallback,
      '@app-themes': appThemes,
      '@app-mascots': appMascots,
      '@app-mascot-images': appMascotImages,
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    fs: {
      // 기본 허용 범위(admin/) 밖인 앱 데이터 폴더와 마스코트 그림 폴더를 읽을 수 있게 한다
      allow: ['.', mobileData, appMascotImages],
    },
  },
});
