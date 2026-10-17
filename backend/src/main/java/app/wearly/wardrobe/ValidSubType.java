package app.wearly.wardrobe;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** category와 subType의 짝 검증 (앱 constants.ts SUBTYPE). 오류는 subType 필드에 붙는다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidSubTypeValidator.class)
public @interface ValidSubType {

    String message() default "카테고리와 맞지 않는 세부 종류예요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
