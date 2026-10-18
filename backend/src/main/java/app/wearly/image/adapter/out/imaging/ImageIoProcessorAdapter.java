package app.wearly.image.adapter.out.imaging;

import app.wearly.common.config.WearlyProperties;
import app.wearly.image.application.port.out.ImageProcessorPort;
import app.wearly.image.domain.ImageFormats;
import app.wearly.image.domain.ImageSettings;
import app.wearly.image.domain.InvalidImageException;
import app.wearly.image.domain.ProcessedImage;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 자바 ImageIO로 사진을 검증하고 다시 인코딩한다 (18 문서 3장).
 *
 * <ol>
 *   <li>실제 바이트로 형식 판별 (JPEG·PNG만)
 *   <li>헤더의 가로·세로로 픽셀 수 상한(wearly.storage.max-pixels) 확인 — 디코딩 전에 막아 메모리 폭주를 피한다
 *   <li>디코딩 (긴 변이 목표의 2배를 넘으면 서브샘플링으로 줄여 읽는다)
 *   <li>EXIF Orientation(metadata-extractor로 읽음) 적용
 *   <li>흰 배경 RGB로 바꿔 긴 변 2048(original)·400(thumb)으로 줄이고, ImageIO JPEG 인코더로 다시 쓴다. 원본의 EXIF(위치 정보
 *       포함)·ICC·XMP 등 메타데이터는 옮기지 않는다
 * </ol>
 *
 * 동시에 처리하는 사진 수를 wearly.storage.processing-concurrency로 제한한다.
 */
@Component
class ImageIoProcessorAdapter implements ImageProcessorPort {

  static final float ORIGINAL_QUALITY = 0.85f;
  static final float THUMB_QUALITY = 0.8f;

  private final long maxPixels;
  private final Semaphore permits;

  @Autowired
  ImageIoProcessorAdapter(WearlyProperties properties) {
    this(properties.storage().maxPixels(), properties.storage().processingConcurrency());
  }

  ImageIoProcessorAdapter(long maxPixels, int concurrency) {
    this.maxPixels = maxPixels;
    this.permits = new Semaphore(concurrency, true);
    // 디코딩 중 디스크 임시 파일을 쓰지 않는다
    ImageIO.setUseCache(false);
  }

  @Override
  public ProcessedImage process(byte[] bytes) {
    String format =
        ImageFormats.sniff(bytes)
            .orElseThrow(() -> new InvalidImageException("JPEG·PNG 사진만 올릴 수 있어요."));
    permits.acquireUninterruptibly();
    try {
      BufferedImage decoded = decode(bytes, format);
      BufferedImage original = fit(decoded, ImageSettings.ORIGINAL_MAX_SIDE);
      decoded = null;
      original = orient(original, orientation(bytes));
      BufferedImage thumb = fit(original, ImageSettings.THUMB_MAX_SIDE);
      return new ProcessedImage(
          encodeJpeg(original, ORIGINAL_QUALITY),
          original.getWidth(),
          original.getHeight(),
          encodeJpeg(thumb, THUMB_QUALITY));
    } catch (IOException | RuntimeException e) {
      if (e instanceof InvalidImageException invalid) {
        throw invalid;
      }
      throw new InvalidImageException("사진을 읽을 수 없어요.", e);
    } finally {
      permits.release();
    }
  }

  private BufferedImage decode(byte[] bytes, String format) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      ImageReader reader = reader(format);
      try {
        // 메타데이터는 읽지 않는다 (방향은 metadata-extractor로 따로 읽는다)
        reader.setInput(in, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0) {
          throw new InvalidImageException("사진을 읽을 수 없어요.");
        }
        if ((long) width * height > maxPixels) {
          throw new InvalidImageException(
              "사진 해상도가 너무 커요. " + (maxPixels / 1_000_000) + "백만 화소 이하로 올려 주세요.");
        }
        ImageReadParam param = reader.getDefaultReadParam();
        int step = Math.max(width, height) / (ImageSettings.ORIGINAL_MAX_SIDE * 2);
        if (step >= 2) {
          param.setSourceSubsampling(step, step, 0, 0);
        }
        BufferedImage image = reader.read(0, param);
        if (image == null) {
          throw new InvalidImageException("사진을 읽을 수 없어요.");
        }
        return image;
      } finally {
        reader.dispose();
      }
    }
  }

  private static ImageReader reader(String format) {
    String name = ImageFormats.PNG.equals(format) ? "png" : "jpeg";
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(name);
    if (!readers.hasNext()) {
      throw new IllegalStateException("ImageIO " + name + " 리더가 없어요");
    }
    return readers.next();
  }

  /** EXIF Orientation (1~8). 없거나 읽지 못하면 1 */
  static int orientation(byte[] bytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
      for (ExifIFD0Directory dir : metadata.getDirectoriesOfType(ExifIFD0Directory.class)) {
        if (dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
          int value = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
          return value >= 1 && value <= 8 ? value : 1;
        }
      }
    } catch (Exception e) {
      // 방향을 못 읽으면 그대로 둔다
      return 1;
    }
    return 1;
  }

  /**
   * 긴 변이 maxSide 이하가 되게 줄이면서 흰 배경 RGB(TYPE_INT_RGB)로 바꾼다 (투명 PNG는 흰 배경). 반씩 여러 번 줄여 품질을 지킨다. 이미 작으면
   * 크기는 그대로 두고 형식만 바꾼다.
   */
  static BufferedImage fit(BufferedImage src, int maxSide) {
    int w = src.getWidth();
    int h = src.getHeight();
    double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
    int targetW = Math.max(1, (int) Math.round(w * scale));
    int targetH = Math.max(1, (int) Math.round(h * scale));
    BufferedImage current = src;
    int cw = w;
    int ch = h;
    do {
      int nw = Math.max(targetW, cw / 2);
      int nh = Math.max(targetH, ch / 2);
      BufferedImage next = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = next.createGraphics();
      try {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(current, 0, 0, nw, nh, null);
      } finally {
        g.dispose();
      }
      current = next;
      cw = nw;
      ch = nh;
    } while (cw != targetW || ch != targetH);
    return current;
  }

  /** EXIF Orientation을 적용해 똑바로 세운다 (5~8은 가로·세로가 바뀐다) */
  static BufferedImage orient(BufferedImage src, int orientation) {
    if (orientation <= 1 || orientation > 8) {
      return src;
    }
    int w = src.getWidth();
    int h = src.getHeight();
    boolean swap = orientation >= 5;
    BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int rgb = src.getRGB(x, y);
        switch (orientation) {
          case 2 -> out.setRGB(w - 1 - x, y, rgb);
          case 3 -> out.setRGB(w - 1 - x, h - 1 - y, rgb);
          case 4 -> out.setRGB(x, h - 1 - y, rgb);
          case 5 -> out.setRGB(y, x, rgb);
          case 6 -> out.setRGB(h - 1 - y, x, rgb);
          case 7 -> out.setRGB(h - 1 - y, w - 1 - x, rgb);
          case 8 -> out.setRGB(y, w - 1 - x, rgb);
          default -> out.setRGB(x, y, rgb);
        }
      }
    }
    return out;
  }

  /** 메타데이터 없이 JPEG로 쓴다 (ImageIO 기본 JFIF 헤더만, EXIF 없음) */
  static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IllegalStateException("ImageIO jpeg 라이터가 없어요");
    }
    ImageWriter writer = writers.next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }
}
