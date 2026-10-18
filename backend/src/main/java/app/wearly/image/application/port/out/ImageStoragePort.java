package app.wearly.image.application.port.out;

import app.wearly.image.domain.PresignedUrl;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * 사진 파일 저장소 (S3 호환 오브젝트 스토리지, 17 문서 1장). 서비스는 S3를 모른다. 실패(네트워크·권한)는 런타임 예외로 던진다. 서명 URL의 호스트는 어댑터가
 * 정한다 (설정 public-endpoint, 없으면 지금 요청의 Host 이름 + 저장소 포트).
 */
public interface ImageStoragePort {

  /** 앱이 바로 올릴 PUT 서명 URL. contentType은 서명에 들어가므로 앱이 같은 Content-Type으로 보내야 한다 */
  PresignedUrl presignPut(String key, String contentType, Duration ttl);

  /** 앱이 바로 받을 GET 서명 URL */
  PresignedUrl presignGet(String key, Duration ttl);

  /** 객체 크기. 없으면 비어 있다 */
  Optional<Long> size(String key);

  byte[] read(String key);

  void write(String key, byte[] bytes, String contentType);

  /** 없는 키는 건너뛴다 */
  void delete(Collection<String> keys);

  /** 접두사 아래 객체를 모두 지운다 (회원 탈퇴) */
  void deletePrefix(String prefix);
}
