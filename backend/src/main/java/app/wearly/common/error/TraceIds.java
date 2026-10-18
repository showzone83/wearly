package app.wearly.common.error;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * 요청 추적 ID (traceId). 요청마다 X-Request-Id 헤더 값(형식이 맞을 때) 또는 새로 만든 값을 MDC에 넣고, 응답 헤더 X-Request-Id와 오류
 * 본문 traceId로 돌려준다. 로그 패턴(application.yml logging.pattern.level)도 같은 MDC 값을 찍는다.
 */
public final class TraceIds {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "traceId";
  public static final String USER_MDC_KEY = "userId";

  /** 받는 X-Request-Id: 영문·숫자·. _ - 8~64자 (그 밖이면 버리고 새로 만든다. 로그 주입 방지) */
  private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

  private TraceIds() {}

  public static boolean isValid(String value) {
    return value != null && VALID.matcher(value).matches();
  }

  /** 새 traceId: 하이픈 없는 UUID 32자 */
  public static String generate() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /** 지금 요청의 traceId (필터 밖이면 null) */
  public static String current() {
    return MDC.get(MDC_KEY);
  }

  /** 지금 요청의 회원 ID (로그인 전·필터 밖이면 null) */
  public static String currentUser() {
    return MDC.get(USER_MDC_KEY);
  }
}
