package app.wearly.ai.adapter.out.imaging;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.domain.ImageTypes;
import app.wearly.ai.domain.ResizedImage;
import app.wearly.support.TestImages;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** AI 분석 사진 줄이기: 큰 사진만 긴 변 1024 JPEG로, 작은 사진·읽을 수 없는 사진은 그대로, 설정 반영 */
class ImageIoAiImageResizerTest {

  final ImageIoAiImageResizer resizer = new ImageIoAiImageResizer(1024, 0.8f, 40_000_000L, 2);

  @Test
  void 큰_JPEG는_긴_변_1024_JPEG로_줄이고_메타데이터를_뺀다() throws Exception {
    byte[] photo = TestImages.withExif(TestImages.jpeg(3000, 1500), 1);

    ResizedImage r = resizer.shrink(photo).orElseThrow();

    assertThat(ImageTypes.sniff(r.bytes())).contains("image/jpeg");
    assertThat(r.width()).isEqualTo(1024);
    assertThat(r.height()).isEqualTo(512);
    BufferedImage out = ImageIO.read(new ByteArrayInputStream(r.bytes()));
    assertThat(out.getWidth()).isEqualTo(1024);
    assertThat(out.getHeight()).isEqualTo(512);
    assertThat(
            ImageMetadataReader.readMetadata(new ByteArrayInputStream(r.bytes()))
                .getFirstDirectoryOfType(GpsDirectory.class))
        .isNull();
  }

  @Test
  void 큰_PNG도_JPEG로_줄인다() {
    ResizedImage r = resizer.shrink(TestImages.png(1000, 2500)).orElseThrow();

    assertThat(ImageTypes.sniff(r.bytes())).contains("image/jpeg");
    assertThat(r.width()).isEqualTo(410);
    assertThat(r.height()).isEqualTo(1024);
  }

  @Test
  void EXIF_방향을_적용해_세운다() {
    // 가로 3000 · 세로 1500, 방향 6 → 세로 사진
    ResizedImage r =
        resizer.shrink(TestImages.withExif(TestImages.jpeg(3000, 1500), 6)).orElseThrow();

    assertThat(r.width()).isEqualTo(512);
    assertThat(r.height()).isEqualTo(1024);
  }

  @Test
  void 긴_변이_1024_이하면_다시_인코딩하지_않는다() {
    assertThat(resizer.shrink(TestImages.jpeg(1024, 700))).isEmpty();
    assertThat(resizer.shrink(TestImages.png(300, 200))).isEmpty();
  }

  @Test
  void JPEG_PNG가_아니거나_읽을_수_없으면_그대로_보낸다() {
    assertThat(resizer.shrink("hello".getBytes(StandardCharsets.UTF_8))).isEmpty();
    assertThat(resizer.shrink(TestImages.fakeJpeg())).isEmpty();
    byte[] webp = new byte[64];
    System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
    System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
    assertThat(resizer.shrink(webp)).isEmpty();
  }

  @Test
  void 픽셀_수_상한을_넘으면_디코딩하지_않고_그대로_보낸다() {
    ImageIoAiImageResizer small = new ImageIoAiImageResizer(1024, 0.8f, 1_000_000L, 1);

    assertThat(small.shrink(TestImages.png(2000, 1000))).isEmpty();
  }

  @Test
  void 설정한_긴_변과_품질을_쓴다() {
    byte[] photo = TestImages.noisyJpeg(2000, 1000);

    Optional<ResizedImage> low = new ImageIoAiImageResizer(512, 0.3f, 40_000_000L, 1).shrink(photo);
    Optional<ResizedImage> high =
        new ImageIoAiImageResizer(512, 0.95f, 40_000_000L, 1).shrink(photo);

    assertThat(low.orElseThrow().width()).isEqualTo(512);
    assertThat(low.orElseThrow().height()).isEqualTo(256);
    assertThat(high.orElseThrow().width()).isEqualTo(512);
    assertThat(low.orElseThrow().bytes().length).isLessThan(high.orElseThrow().bytes().length);
    // 긴 변이 설정 값과 같으면 줄이지 않는다
    assertThat(new ImageIoAiImageResizer(2000, 0.8f, 40_000_000L, 1).shrink(photo)).isEmpty();
  }
}
