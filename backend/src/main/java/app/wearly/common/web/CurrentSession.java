package app.wearly.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러 인자에 토큰의 로그인 세션 ID(Jwt {@code sid} 클레임, 16 문서 3장)를 넣는다: {@code @CurrentSession String
 * sessionId}. 인증 필터가 sid 없는 토큰을 막으므로 인증된 요청에서는 비어 있지 않다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "getClaimAsString('sid')")
public @interface CurrentSession {}
