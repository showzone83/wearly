# syntax=docker/dockerfile:1
# Wearly 백엔드 이미지. 저장소 루트에서 docker compose up -d --build (docker-compose.yml의 backend 서비스)
# 비밀 값은 이미지에 넣지 않는다: config/application-local.yml은 .dockerignore로 빼고, 실행 시 /app/config로 마운트한다.

# ── 빌드: Gradle wrapper로 bootJar (테스트는 Mac에서 ./gradlew test로 따로 돌린다) ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
ENV GRADLE_USER_HOME=/gradle-home
# 의존성만 먼저 받아 두면 소스만 바뀌었을 때 이 층을 다시 쓴다. Gradle 캐시는 BuildKit 캐시 마운트에 둔다
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/gradle-home \
    ./gradlew --no-daemon --console=plain dependencies --configuration runtimeClasspath > /dev/null
COPY src src
RUN --mount=type=cache,target=/gradle-home \
    ./gradlew --no-daemon --console=plain bootJar -x test \
    && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /src/app.jar

# ── 실행: JRE + 비루트 사용자 ──
FROM eclipse-temurin:21-jre
RUN groupadd --system wearly && useradd --system --gid wearly --home-dir /app --no-create-home wearly
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
# 작업 디렉터리 /app 기준으로 ./config/application-local.yml을 읽는다 (compose가 backend/config를 읽기 전용 마운트)
USER wearly
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
