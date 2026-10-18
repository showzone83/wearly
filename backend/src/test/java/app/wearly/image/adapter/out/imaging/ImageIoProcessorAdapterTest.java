package app.wearly.image.adapter.out.imaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.image.domain.ImageFormats;
import app.wearly.image.domain.InvalidImageException;
import app.wearly.image.domain.ProcessedImage;
import app.wearly.support.TestImages;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 사진 검증·다시 인코딩 (18 문서 3장): 형식 판별, EXIF 제거, 방향 적용, 축소, 픽셀 수 상한 */
class ImageIoProcessorAdapterTest {

  final ImageIoProcessorAdapter processor = new ImageIoProcessorAdapter(40_000_000L, 2);

  @Test
  void 큰_PNG는_긴_변_2048_original과_400_thumb_JPEG로_줄인다() throws Exception {
    ProcessedImage p = processor.process(TestImages.png(3000, 1000));

    assertThat(ImageFormats.sniff(p.original())).contains(ImageFormats.JPEG);
    assertThat(ImageFormats.sniff(p.thumb())).contains(ImageFormats.JPEG);
    assertThat(p.width()).isEqualTo(2048);
    assertThat(p.height()).isEqualTo(683);
    BufferedImage original = ImageIO.read(new ByteArrayInputStream(p.original()));
    assertThat(original.getWidth()).isEqualTo(2048);
    assertThat(original.getHeight()).isEqualTo(683);
    BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(p.thumb()));
    assertThat(thumb.getWidth()).isEqualTo(400);
    assertThat(thumb.getHeight()).isEqualTo(133);
  }

  @Test
  void 작은_사진은_크기를_그대로_두고_JPEG로_바꾼다() throws Exception {
    ProcessedImage p = processor.process(TestImages.png(300, 200));

    assertThat(p.width()).isEqualTo(300);
    assertThat(p.height()).isEqualTo(200);
    BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(p.thumb()));
    assertThat(thumb.getWidth()).isEqualTo(300);
    assertThat(thumb.getHeight()).isEqualTo(200);
  }

  @Test
  void 투명_PNG는_흰_배경으로_채운다() throws Exception {
    BufferedImage clear = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    ProcessedImage p = processor.process(TestImages.encode(clear, "png"));

    BufferedImage out = ImageIO.read(new ByteArrayInputStream(p.original()));
    Color c = new Color(out.getRGB(20, 20));
    assertThat(c.getRed()).isGreaterThan(240);
    assertThat(c.getGreen()).isGreaterThan(240);
    assertThat(c.getBlue()).isGreaterThan(240);
  }

  @Test
  void EXIF_위치정보와_방향_태그를_지운다() throws Exception {
    byte[] input = TestImages.withExif(TestImages.jpeg(60, 30), 1);
    Metadata before = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
    assertThat(before.getFirstDirectoryOfType(GpsDirectory.class)).isNotNull();
    assertThat(before.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNotNull();

    ProcessedImage p = processor.process(input);

    for (byte[] out : new byte[][] {p.original(), p.thumb()}) {
      Metadata after = ImageMetadataReader.readMetadata(new ByteArrayInputStream(out));
      assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
      assertThat(after.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
      assertThat(new String(out, StandardCharsets.ISO_8859_1)).doesNotContain("Exif\0\0");
    }
  }

  @Test
  void EXIF_방향_6이면_시계방향으로_세운다() throws Exception {
    // 가로 60 · 세로 30, 왼쪽 빨강 · 오른쪽 파랑. 방향 6(시계방향 90도 돌려 보기) → 세로 사진, 위 빨강 · 아래 파랑
    ProcessedImage p = processor.process(TestImages.withExif(TestImages.jpeg(60, 30), 6));

    assertThat(p.width()).isEqualTo(30);
    assertThat(p.height()).isEqualTo(60);
    BufferedImage out = ImageIO.read(new ByteArrayInputStream(p.original()));
    assertThat(isRed(out.getRGB(15, 10))).isTrue();
    assertThat(isBlue(out.getRGB(15, 50))).isTrue();
  }

  @Test
  void EXIF_방향_8이면_반시계방향으로_세운다() throws Exception {
    // 방향 8(반시계 90도) → 위 파랑 · 아래 빨강
    ProcessedImage p = processor.process(TestImages.withExif(TestImages.jpeg(60, 30), 8));

    assertThat(p.width()).isEqualTo(30);
    assertThat(p.height()).isEqualTo(60);
    BufferedImage out = ImageIO.read(new ByteArrayInputStream(p.original()));
    assertThat(isBlue(out.getRGB(15, 10))).isTrue();
    assertThat(isRed(out.getRGB(15, 50))).isTrue();
  }

  @Test
  void 방향_변환_8가지() {
    BufferedImage src = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    src.setRGB(0, 0, 0xFF0000);
    // 원본 (0,0)에 있던 점이 가는 자리
    int[][] expected = {
      {0, 0}, {2, 0}, {2, 1}, {0, 1}, {0, 0}, {1, 0}, {1, 2}, {0, 2},
    };
    for (int o = 1; o <= 8; o++) {
      BufferedImage out = ImageIoProcessorAdapter.orient(src, o);
      assertThat(out.getWidth()).as("방향 %d 가로", o).isEqualTo(o >= 5 ? 2 : 3);
      int[] at = expected[o - 1];
      assertThat(out.getRGB(at[0], at[1]) & 0xFFFFFF).as("방향 %d", o).isEqualTo(0xFF0000);
    }
  }

  @Test
  void 가짜_JPEG와_JPEG_PNG가_아닌_파일은_거부한다() {
    assertThatThrownBy(() -> processor.process(TestImages.fakeJpeg()))
        .isInstanceOf(InvalidImageException.class);
    assertThatThrownBy(() -> processor.process("hello".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            InvalidImageException.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidImageException.Reason.FORMAT));
    byte[] gif = TestImages.encode(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "gif");
    assertThatThrownBy(() -> processor.process(gif)).isInstanceOf(InvalidImageException.class);
  }

  @Test
  void 픽셀_수_상한을_넘으면_디코딩_전에_거부한다() {
    ImageIoProcessorAdapter small = new ImageIoProcessorAdapter(100L, 1);

    assertThatThrownBy(() -> small.process(TestImages.png(20, 20)))
        .isInstanceOfSatisfying(
            InvalidImageException.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidImageException.Reason.PIXELS));
    assertThat(small.process(TestImages.png(10, 10)).width()).isEqualTo(10);
  }

  private static boolean isRed(int rgb) {
    Color c = new Color(rgb);
    return c.getRed() > 200 && c.getBlue() < 60;
  }

  private static boolean isBlue(int rgb) {
    Color c = new Color(rgb);
    return c.getBlue() > 200 && c.getRed() < 60;
  }
}
