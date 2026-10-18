package app.wearly.image.adapter.out.storage;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 서명 URL을 만들 S3Presigner 고르기. SigV4는 호스트를 서명에 넣으므로 서명 URL은 앱이 실제로 접속하는 주소로 만들어야 한다.
 *
 * <ul>
 *   <li>wearly.storage.public-endpoint가 있으면 언제나 그 주소
 *   <li>없고 endpoint도 없으면(AWS S3) AWS 기본 주소
 *   <li>없고 endpoint가 있으면 지금 요청의 Host 이름 + endpoint의 스킴·포트 (docker에서 백엔드는 minio:9000으로 붙지만 시뮬레이터는
 *       localhost:9000, 실제 휴대폰은 Mac LAN IP:9000으로 받는다). 요청 밖(정리 작업 등)이면 endpoint 그대로
 * </ul>
 *
 * 호스트별 presigner는 캐시한다 (Host 헤더는 요청이 정하므로 개수를 제한하고, 넘으면 캐시하지 않고 쓰고 닫는다).
 */
class StoragePresigners implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(StoragePresigners.class);
  static final int MAX_CACHED = 32;

  private final Region region;
  private final AwsCredentialsProvider credentials;
  private final boolean pathStyle;
  private final URI endpoint;
  private final S3Presigner fixed;
  private final Map<String, S3Presigner> byHost = new ConcurrentHashMap<>();

  StoragePresigners(
      Region region,
      AwsCredentialsProvider credentials,
      boolean pathStyle,
      URI endpoint,
      URI publicEndpoint) {
    this.region = region;
    this.credentials = credentials;
    this.pathStyle = pathStyle;
    this.endpoint = endpoint;
    if (publicEndpoint != null) {
      this.fixed = build(publicEndpoint);
    } else if (endpoint == null) {
      this.fixed = build(null);
    } else {
      this.fixed = null;
    }
  }

  /** 서명할 presigner를 빌려 쓴다. 캐시하지 않은 것은 쓴 뒤 닫는다 */
  <T> T with(Function<S3Presigner, T> action) {
    if (fixed != null) {
      return action.apply(fixed);
    }
    URI target = forRequest();
    String key = target.toString();
    S3Presigner cached = byHost.get(key);
    if (cached != null) {
      return action.apply(cached);
    }
    if (byHost.size() < MAX_CACHED) {
      return action.apply(byHost.computeIfAbsent(key, k -> build(target)));
    }
    log.debug("서명 호스트 캐시가 가득 차 임시 presigner를 씀: {}", key);
    try (S3Presigner temp = build(target)) {
      return action.apply(temp);
    }
  }

  /** 지금 요청의 Host 이름 + endpoint 스킴·포트. 요청 밖이면 endpoint */
  URI forRequest() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servlet)) {
      return endpoint;
    }
    HttpServletRequest request = servlet.getRequest();
    String host = request.getServerName();
    if (host == null || host.isBlank()) {
      return endpoint;
    }
    return withHost(endpoint, host);
  }

  static URI withHost(URI endpoint, String host) {
    String h = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    String port = endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "";
    return URI.create(endpoint.getScheme() + "://" + h + port);
  }

  private S3Presigner build(URI uri) {
    S3Presigner.Builder b =
        S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
    if (uri != null) {
      b.endpointOverride(uri);
    }
    return b.build();
  }

  @Override
  public void close() {
    if (fixed != null) {
      fixed.close();
    }
    byHost.values().forEach(S3Presigner::close);
    byHost.clear();
  }
}
