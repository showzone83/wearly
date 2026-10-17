package app.wearly.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 08 문서 3장 규칙을 테스트로 강제한다 (Spring 없이 바이트코드만 본다). - domain: 자바 표준과 common.domain만 - application:
 * 어댑터·JPA·MyBatis·Jackson·Spring Web을 모른다. Spring은 @Service·@Transactional만 - adapter.in: 출력
 * 포트·유스케이스 구현·출력 어댑터를 직접 부르지 않는다 - adapter.out: 유스케이스 구현·입력 어댑터를 부르지 않는다 - 컨텍스트 사이 의존은 상대 컨텍스트의
 * application.port.in만
 */
@AnalyzeClasses(packages = "app.wearly", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  static final List<String> CONTEXTS =
      List.of("auth", "user", "wardrobe", "code", "ai", "admin", "outfit");
  static final Pattern CONTEXT_CLASS =
      Pattern.compile("^app\\.wearly\\.(" + String.join("|", CONTEXTS) + ")\\.(.*)$");

  @ArchTest
  static final ArchRule 도메인은_자바_표준과_common_domain만_쓴다 =
      classes()
          .that()
          .resideInAPackage("app.wearly..domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "app.wearly.common.domain..", "app.wearly.*.domain..")
          .because("도메인 모델·규칙은 순수 자바다 (Spring·JPA·MyBatis·Jackson 어노테이션 없음)");

  @ArchTest
  static final ArchRule 유스케이스는_어댑터와_기술_프레임워크를_모른다 =
      noClasses()
          .that()
          .resideInAPackage("app.wearly.*.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "app.wearly.*.adapter..",
              "app.wearly.common.config..",
              "app.wearly.common.web..",
              "app.wearly.common.mybatis..",
              "jakarta.persistence..",
              "org.hibernate..",
              "org.apache.ibatis..",
              "org.mybatis..",
              "org.springframework.data..",
              "org.springframework.web..",
              "org.springframework.http..",
              "jakarta.servlet..",
              "org.springframework.security..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "jakarta.validation..",
              "com.anthropic..");

  @ArchTest
  static final ArchRule 유스케이스가_쓰는_Spring은_Service와_Transactional뿐이다 =
      classes()
          .that()
          .resideInAPackage("app.wearly.*.application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "java..",
              "org.slf4j..",
              "app.wearly..",
              "org.springframework.stereotype..",
              "org.springframework.transaction.annotation..");

  @ArchTest
  static final ArchRule 입력_어댑터는_입력_포트만_부른다 =
      noClasses()
          .that()
          .resideInAPackage("app.wearly.*.adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "app.wearly.*.application.port.out..",
              "app.wearly.*.application.service..",
              "app.wearly.*.adapter.out..");

  @ArchTest
  static final ArchRule 출력_어댑터는_유스케이스_구현과_입력_어댑터를_모른다 =
      noClasses()
          .that()
          .resideInAPackage("app.wearly.*.adapter.out..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("app.wearly.*.application.service..", "app.wearly.*.adapter.in..");

  @ArchTest
  static final ArchRule 컨텍스트_사이에는_입력_포트로만_의존한다 =
      classes()
          .that()
          .resideInAnyPackage(
              CONTEXTS.stream().map(c -> "app.wearly." + c + "..").toArray(String[]::new))
          .should(onlyUseOtherContextsThroughInputPorts());

  @ArchTest
  static final ArchRule 공용_도메인은_컨텍스트를_모른다 =
      classes()
          .that()
          .resideInAPackage("app.wearly.common.domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "app.wearly.common.domain..");

  @ArchTest
  static final ArchRule JPA_엔티티는_영속성_어댑터에만 =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .should()
          .resideInAPackage("app.wearly.*.adapter.out.persistence..");

  @ArchTest
  static final ArchRule MyBatis_매퍼는_MyBatis_어댑터의_mapper에만 =
      classes()
          .that()
          .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
          .should()
          .resideInAPackage("app.wearly.*.adapter.out.persistence.mybatis.mapper..");

  @ArchTest
  static final ArchRule 컨트롤러는_웹_어댑터에만 =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .should()
          .resideInAPackage("app.wearly.*.adapter.in.web..");

  @ArchTest
  static final ArchRule 유스케이스_구현은_application_service에 =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.stereotype.Service")
          .should()
          .resideInAPackage("app.wearly.*.application.service..");

  @ArchTest
  static final ArchRule 출력_포트_구현은_출력_어댑터에 =
      classes()
          .that()
          .implement(
              com.tngtech.archunit.base.DescribedPredicate.describe(
                  "출력 포트",
                  (JavaClass c) ->
                      c.getPackageName().matches("app\\.wearly\\.\\w+\\.application\\.port\\.out.*")
                          && c.isInterface()))
          .should()
          .resideInAPackage("app.wearly.*.adapter.out..");

  private static ArchCondition<JavaClass> onlyUseOtherContextsThroughInputPorts() {
    return new ArchCondition<>("다른 컨텍스트는 application.port.in으로만 쓴다") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        Optional<String> from = contextOf(origin.getName());
        if (from.isEmpty()) {
          return;
        }
        for (Dependency d : origin.getDirectDependenciesFromSelf()) {
          String target = d.getTargetClass().getName();
          Optional<String> to = contextOf(target);
          if (to.isEmpty() || to.get().equals(from.get())) {
            continue;
          }
          if (!target.startsWith("app.wearly." + to.get() + ".application.port.in.")) {
            events.add(SimpleConditionEvent.violated(d, d.getDescription()));
          }
        }
      }
    };
  }

  private static Optional<String> contextOf(String className) {
    Matcher m = CONTEXT_CLASS.matcher(className);
    return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
  }
}
