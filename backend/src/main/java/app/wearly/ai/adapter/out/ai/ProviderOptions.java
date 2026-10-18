package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.common.config.WearlyProperties;
import java.time.Duration;
import java.util.Map;

/**
 * 공급자 행의 options를 클라이언트 설정 값으로 읽는다. timeoutSeconds가 없으면 WEARLY_AI_TIMEOUT, baseUrl은
 * WEARLY_AI_ALLOW_BASE_URL=true일 때만 쓴다. promptCache는 wearly.ai.prompt-cache.enabled (Claude만 쓴다).
 */
public record ProviderOptions(
    Map<String, Object> raw, Duration timeout, String baseUrl, boolean promptCache) {

  /** 프롬프트 캐싱 켜짐(기본값)으로 만든다 */
  public ProviderOptions(Map<String, Object> raw, Duration timeout, String baseUrl) {
    this(raw, timeout, baseUrl, true);
  }

  public static ProviderOptions of(AiProviderSetting s, WearlyProperties.Ai props) {
    Map<String, Object> o = s.options();
    Duration timeout =
        o.get("timeoutSeconds") instanceof Number n
            ? Duration.ofSeconds(n.longValue())
            : props.timeout();
    String baseUrl = props.allowBaseUrl() && o.get("baseUrl") instanceof String u ? u : null;
    return new ProviderOptions(o, timeout, baseUrl, props.promptCache().enabled());
  }

  public Integer integer(String name) {
    return raw.get(name) instanceof Number n ? n.intValue() : null;
  }

  public Double number(String name) {
    return raw.get(name) instanceof Number n ? n.doubleValue() : null;
  }

  public String string(String name) {
    return raw.get(name) instanceof String s ? s : null;
  }

  public String baseUrlOr(String fallback) {
    return baseUrl == null ? fallback : baseUrl;
  }
}
