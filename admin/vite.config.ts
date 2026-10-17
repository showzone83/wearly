import react from '@vitejs/plugin-react';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

// 옷 일러스트 모양은 앱(mobile/)의 순수 TS 데이터를 그대로 가져다 쓴다 (doc/07 5장).
// 앱 파일이 없으면(앱 작업 전 등) 관리자 사이트 안의 대체 파일로 빌드한다. tsconfig.app.json의 paths도 같은 순서다.
const mobileData = fileURLToPath(new URL('../mobile/src/data', import.meta.url));
const garmentShapes = fileURLToPath(new URL('../mobile/src/data/garmentShapes.ts', import.meta.url));
const garmentFallback = fileURLToPath(new URL('./src/lib/garmentShapesFallback.ts', import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@garment-shapes': existsSync(garmentShapes) ? garmentShapes : garmentFallback,
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    fs: {
      // 기본 허용 범위(admin/) 밖인 앱 데이터 폴더를 읽을 수 있게 한다
      allow: ['.', mobileData],
    },
  },
});
