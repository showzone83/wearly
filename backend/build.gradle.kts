plugins {
    java
    checkstyle
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    // 08 문서 5장 코드 스타일: google-java-format으로 포맷 검사 (./gradlew spotlessApply로 고침)
    id("com.diffplug.spotless") version "8.10.4"
}

group = "app.wearly"
version = "0.0.1-SNAPSHOT"
description = "Wearly 백엔드 API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot BOM이 관리하지 않는 라이브러리 버전
val mybatisStarterVersion = "4.1.0"
val anthropicJavaVersion = "2.73.0"
val archunitVersion = "1.5.1"
// 08 문서 5장 코드 스타일 도구
val googleJavaFormatVersion = "1.36.1"
val checkstyleVersion = "14.3.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 로그인 세션·Refresh 토큰 저장소 (Valkey, Redis 호환. 16 문서). 클라이언트는 Lettuce
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisStarterVersion")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.anthropic:anthropic-java:$anthropicJavaVersion")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // 08 문서 3장 헥사고날 의존 규칙 검사
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// 08 문서 5장: Google Java Style. check가 spotlessCheck(포맷)와 checkstyleMain·checkstyleTest(이름 규칙 등)를 함께 돌린다
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(googleJavaFormatVersion)
        // google-java-format이 하지 않는 것: 와일드카드 import 금지 (쓰지 않는 import는 위가 지운다)
        forbidWildcardImports()
    }
}

checkstyle {
    toolVersion = checkstyleVersion
    configFile = file("config/checkstyle/google_checks.xml")
    // 배포본 google_checks.xml의 기본 심각도는 warning이다. error로 올려 위반하면 빌드가 실패하게 하고,
    // Javadoc 관련 모듈만 설정 파일에서 warning으로 둔다
    configProperties = mapOf(
        "org.checkstyle.google.severity" to "error",
        // 한국어 테스트 이름 예외 (테스트 소스만)
        "org.checkstyle.google.suppressionfilter.config" to file("config/checkstyle/checkstyle-suppressions.xml").path,
    )
    maxWarnings = Int.MAX_VALUE
    isIgnoreFailures = false
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 외부 DB·Valkey로 테스트할 때 쓰는 환경변수를 테스트 JVM에 그대로 넘긴다
    listOf(
        "WEARLY_TEST_DB_URL", "WEARLY_TEST_DB_USERNAME", "WEARLY_TEST_DB_PASSWORD",
        "WEARLY_TEST_VALKEY_HOST", "WEARLY_TEST_VALKEY_PORT",
    ).forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
    // 개발 PC의 config/application-local.yml(실제 AI 키·관리자 계정·마스터 키)이 테스트에 섞이지 않게
    // application.yml의 spring.config.import 경로를 없는 파일로 돌린다 (optional이라 그냥 건너뛴다)
    environment("WEARLY_LOCAL_CONFIG", layout.buildDirectory.file("no-local-config/application-local.yml").get().asFile.path)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// 기본 ./gradlew bootRun은 관리자 화면의 AI 공급자 설정(tb_ai_provider)을 우선순위 순으로 쓴다.
// 쓸 수 있는 공급자가 없으면 시작은 하되 WARN을 남기고, 사진 분석은 502 AI_UNAVAILABLE (목업으로 대신하지 않음).
// 목업 공급자는 이 태스크로만 켠다: ./gradlew bootRunMock
tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bootRunMock") {
    group = "application"
    description = "AI 공급자를 목업으로 바꿔 서버를 띄운다 (WEARLY_AI_PROVIDER=mock)"
    // bootRun 태스크를 거치지 않고 같은 클래스패스·메인 클래스를 쓴다 (bootRun에 의존하면 bootRun이 먼저 실행된다)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.wearly.WearlyApplication")
    workingDir = projectDir
    environment("WEARLY_AI_PROVIDER", "mock")
}
