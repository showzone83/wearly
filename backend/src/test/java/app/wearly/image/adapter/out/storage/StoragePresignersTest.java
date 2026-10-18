package app.wearly.image.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** 서명 URL 호스트: 요청 Host 이름 + 저장소 스킴·포트 */
class StoragePresignersTest {

  @Test
  void 요청_Host_이름에_저장소_포트를_붙인다() {
    URI minio = URI.create("http://minio:9000");
    assertThat(StoragePresigners.withHost(minio, "localhost"))
        .isEqualTo(URI.create("http://localhost:9000"));
    assertThat(StoragePresigners.withHost(minio, "192.168.0.12"))
        .isEqualTo(URI.create("http://192.168.0.12:9000"));
    assertThat(StoragePresigners.withHost(minio, "::1")).isEqualTo(URI.create("http://[::1]:9000"));
    assertThat(StoragePresigners.withHost(URI.create("https://s3.example.com"), "api.example.com"))
        .isEqualTo(URI.create("https://api.example.com"));
  }
}
