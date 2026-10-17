plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
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

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisStarterVersion")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.anthropic:anthropic-java:$anthropicJavaVersion")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 외부 DB로 테스트할 때 쓰는 환경변수를 테스트 JVM에 그대로 넘긴다
    listOf("WEARLY_TEST_DB_URL", "WEARLY_TEST_DB_USERNAME", "WEARLY_TEST_DB_PASSWORD").forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// 기본 ./gradlew bootRun은 실제 Claude만 쓴다 (ANTHROPIC_API_KEY 없으면 시작 실패).
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
