# syntax=docker/dockerfile:1
# Wearly 관리자 사이트 이미지. 빌드 컨텍스트는 저장소 루트다 (docker-compose.yml의 admin 서비스):
# vite 별칭 '@garment-shapes'가 ../mobile/src/data/garmentShapes.ts를 가져오므로 그 파일도 함께 복사한다.
# 무엇을 컨텍스트에 넣을지는 루트 .dockerignore가 정한다 (node_modules·dist 제외).

# ── 빌드: tsc -b && vite build ──
FROM node:22 AS build
WORKDIR /repo/admin
COPY admin/package.json admin/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY admin/ ./
COPY mobile/src/data/garmentShapes.ts /repo/mobile/src/data/garmentShapes.ts
# 브라우저가 부를 백엔드 주소. 빌드할 때 번들에 들어간다 (/api/v1은 붙이지 않음)
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ── 실행: nginx가 dist를 서빙, 없는 경로는 index.html (SPA fallback) ──
FROM nginx:alpine
COPY admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/admin/dist /usr/share/nginx/html
EXPOSE 80
