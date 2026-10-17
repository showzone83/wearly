package app.wearly.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** 컨트롤러 인자에 토큰의 userId(Jwt sub)를 넣는다: {@code @CurrentUser long userId} */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).parseLong(subject)")
public @interface CurrentUser {}
