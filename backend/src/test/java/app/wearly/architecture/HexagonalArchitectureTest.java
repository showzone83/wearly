package app.wearly.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;

/**
 * 08 문서 3장·7장 규칙을 테스트로 강제한다 (Spring 없이 바이트코드만 본다).
 *
 * <ul>
 *   <li>domain: 자바 표준, common.domain, 공유 커널(sharedkernel)만
 *   <li>application: 어댑터·JPA·MyBatis·Jackson·Spring Web을 모른다. Spring은 @Service·@Transactional만
 *   <li>adapter.in: 출력 포트·유스케이스 구현·출력 어댑터를 직접 부르지 않는다. adapter.out: 유스케이스 구현·입력 어댑터·API 오류를 모른다
 *   <li>컨텍스트 사이 의존은 상대 컨텍스트의 application.port.in만. common·sharedkernel은 컨텍스트를 모른다
 *   <li>7장: @Entity는 persistence.jpa, @Mapper는 persistence.mybatis.mapper, 검증·Jackson 어노테이션은
 *       adapter.in.web, 컨트롤러는 web.response 타입만 돌려준다, 포트 DTO는 어댑터를 모른다
 *   <li>오류 응답: BusinessException은 문장(String)을 받지 않고, 예외 메시지를 읽는 메서드는 오류 응답 객체를 만들지 않으며, ErrorCode
 *       설명 문장은 응답에 쓰지 않는다 (서버 내부 정보가 응답으로 나가지 않게)
 * </ul>
 */
@AnalyzeClasses(packages = "app.wearly", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  static final List<String> CONTEXTS =
      List.of(
          "auth",
          "user",
          "wardrobe",
          "code",
          "ai",
          "admin",
          "outfit",
          "appsetting",
          "image",
          "weather",
          "plan",
          "notify",
          "invite",
          "pairing");
  static final Pattern CONTEXT_CLASS =
      Pattern.compile("^app\\.wearly\\.(" + String.join("|", CONTEXTS) + ")\\.(.*)$");

  @ArchTest
  static final ArchRule 도메인은_자바_표준과_common_domain만_쓴다 =
      classes()
          .that()
          .resideInAPackage("app.wearly..domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "java..",
              "app.wearly.common.domain..",
              "app.wearly.sharedkernel..",
              "app.wearly.*.domain..")
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
  static final ArchRule 출력_어댑터는_API_오류를_모른다 =
      noClasses()
          .that()
          .resideInAPackage("app.wearly.*.adapter.out..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("app.wearly.common.error..")
          .because("저장·외부 호출 실패는 도메인·애플리케이션 예외로 던지고 서비스가 API 오류로 바꾼다 (L1)");

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
  static final ArchRule 공유_커널은_자바_표준과_common_domain만_쓴다 =
      classes()
          .that()
          .resideInAPackage("app.wearly.sharedkernel..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "app.wearly.common.domain..", "app.wearly.sharedkernel..")
          .because("공유 커널은 불변 값·조회 메서드·공유 정책만 둔다 (M8)");

  @ArchTest
  static final ArchRule common은_컨텍스트를_모른다 =
      noClasses()
          .that()
          .resideInAnyPackage("app.wearly.common..", "app.wearly.sharedkernel..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              CONTEXTS.stream().map(c -> "app.wearly." + c + "..").toArray(String[]::new))
          .because("컨텍스트에 속한 설정(보안·AI·코디)은 각 컨텍스트의 config 패키지에 둔다 (M8)");

  @ArchTest
  static final ArchRule JPA_엔티티는_영속성_어댑터에만 =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .should()
          .resideInAPackage("app.wearly.*.adapter.out.persistence.jpa..");

  @ArchTest
  static final ArchRule 검증_Jackson_어노테이션은_웹_어댑터에만 =
      noClasses()
          .that()
          .resideOutsideOfPackages("app.wearly.*.adapter.in.web..", "app.wearly.common.config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "jakarta.validation.constraints..", "com.fasterxml.jackson.annotation..")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("jakarta.validation.Valid")
          .because(
              "요청·응답 JSON 모양과 입력 검증은 웹 어댑터의 일이다 (08 문서 7장)."
                  + " 저장용 JSON 변환은 영속성 어댑터가 JsonMapper로 한다."
                  + " 예외: 시작 설정 값 검증(common.config.WearlyProperties)");

  @ArchTest
  static final ArchRule 컨트롤러는_응답_타입만_돌려준다 =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .and()
          .arePublic()
          .should(returnWebResponseTypes());

  @ArchTest
  static final ArchRule 포트_DTO는_어댑터를_모른다 =
      noClasses()
          .that()
          .resideInAnyPackage(
              "app.wearly.*.application.port.in.dto..", "app.wearly.*.application.port.out.dto..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("app.wearly.*.adapter..");

  @ArchTest
  static final ArchRule 출력_포트_DTO는_입력_포트_DTO를_모른다 =
      noClasses()
          .that()
          .resideInAPackage("app.wearly.*.application.port.out.dto..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("app.wearly.*.application.port.in.dto..");

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

  @ArchTest
  static final ArchRule BusinessException은_문장을_받지_않는다 =
      constructors()
          .that()
          .areDeclaredIn(BusinessException.class)
          .should(haveNoStringParameter())
          .because("응답 문구는 ErrorCode·필드 오류 코드와 params뿐이다. 동적 문자열(예외 메시지 등)이 응답으로 나가지 않게 한다");

  @ArchTest
  static final ArchRule 예외_메시지를_읽는_메서드는_오류_응답을_만들지_않는다 =
      codeUnits()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("app.wearly..")
          .should(notBuildErrorsFromExceptionMessages())
          .because(
              "Throwable.getMessage()는 로그에만 쓴다. 같은 메서드에서"
                  + " BusinessException·FieldError·ErrorResponse를 만들면"
                  + " 예외 메시지가 응답에 섞일 수 있다 (로그는 따로 뺀 메서드에서 남긴다)");

  @ArchTest
  static final ArchRule ErrorCode_설명은_응답에_쓰지_않는다 =
      noClasses()
          .that()
          .resideInAPackage("app.wearly..")
          .should()
          .callMethod(ErrorCode.class, "description")
          .because("오류 응답은 code만 보낸다. 설명 문장은 문서·카탈로그(error-codes.json)용이다");

  private static ArchCondition<JavaConstructor> haveNoStringParameter() {
    return new ArchCondition<>("String 인자가 없다") {
      @Override
      public void check(JavaConstructor c, ConditionEvents events) {
        if (c.getRawParameterTypes().stream().anyMatch(t -> t.isEquivalentTo(String.class))) {
          events.add(SimpleConditionEvent.violated(c, c.getFullName() + "이(가) String을 받는다"));
        }
      }
    };
  }

  private static ArchCondition<JavaCodeUnit> notBuildErrorsFromExceptionMessages() {
    Set<String> errorTypes =
        Set.of(
            BusinessException.class.getName(),
            FieldError.class.getName(),
            ErrorResponse.class.getName(),
            DomainValidationException.class.getName());
    return new ArchCondition<>("예외 메시지를 읽으면서 오류 응답 객체를 만들지 않는다") {
      @Override
      public void check(JavaCodeUnit unit, ConditionEvents events) {
        boolean readsMessage =
            unit.getMethodCallsFromSelf().stream()
                .anyMatch(
                    call ->
                        (call.getName().equals("getMessage")
                                || call.getName().equals("getLocalizedMessage"))
                            && call.getTargetOwner().isAssignableTo(Throwable.class));
        if (!readsMessage) {
          return;
        }
        List<JavaAccess<?>> builds = new java.util.ArrayList<>();
        unit.getConstructorCallsFromSelf().stream()
            .filter(call -> errorTypes.contains(call.getTargetOwner().getName()))
            .forEach(builds::add);
        unit.getMethodCallsFromSelf().stream()
            .filter(call -> errorTypes.contains(call.getTargetOwner().getName()))
            .forEach(builds::add);
        builds.forEach(
            b ->
                events.add(
                    SimpleConditionEvent.violated(
                        b, unit.getFullName() + "이(가) 예외 메시지를 읽고 " + b.getDescription())));
      }
    };
  }

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

  /** 반환 타입이 adapter.in.web.response 타입, 그 컬렉션·ResponseEntity, 또는 void */
  private static ArchCondition<JavaMethod> returnWebResponseTypes() {
    return new ArchCondition<>("adapter.in.web.response 타입(또는 그 컬렉션·ResponseEntity·void)을 돌려준다") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        JavaType type = method.getReturnType();
        JavaClass raw = type.toErasure();
        if (raw.getName().equals("void")) {
          return;
        }
        if (raw.isAssignableTo(Collection.class) || raw.isEquivalentTo(ResponseEntity.class)) {
          if (!(type instanceof JavaParameterizedType p) || p.getActualTypeArguments().isEmpty()) {
            events.add(SimpleConditionEvent.violated(method, method.getFullName() + " 타입 인자가 없다"));
            return;
          }
          JavaClass arg = p.getActualTypeArguments().getFirst().toErasure();
          if (raw.isEquivalentTo(ResponseEntity.class)
              && arg.getName().equals(Void.class.getName())) {
            return;
          }
          raw = arg;
        }
        if (!raw.getPackageName()
            .matches("app\\.wearly\\.\\w+\\.adapter\\.in\\.web\\.response.*")) {
          events.add(
              SimpleConditionEvent.violated(
                  method, method.getFullName() + "이(가) " + raw.getName() + "을(를) 돌려준다"));
        }
      }
    };
  }

  private static Optional<String> contextOf(String className) {
    Matcher m = CONTEXT_CLASS.matcher(className);
    return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
  }
}
