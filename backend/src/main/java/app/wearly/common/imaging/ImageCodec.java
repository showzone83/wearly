package app.wearly.common.imaging;

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
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * 자바 ImageIO로 사진을 읽고 줄이고 JPEG로 다시 쓰는 기술 도구 (출력 어댑터들이 같이 쓴다: image 컨텍스트의 저장용 처리, ai 컨텍스트의 분석용 축소).
 * 컨텍스트 규칙(예외 종류·크기 기준)은 모르고, 실패는 IOException으로만 알린다.
 *
 * <p>형식 이름은 ImageIO 이름("jpeg"·"png")이다. 다시 쓴 JPEG에는 원본의 EXIF(위치 정보 포함)·ICC·XMP 같은 메타데이터가 없다.
 */
public final class ImageCodec {

  /** ImageIO 형식 이름 */
  public static final String JPEG = "jpeg";

  public static final String PNG = "png";

  static {
    // 디코딩 중 디스크 임시 파일을 쓰지 않는다
    ImageIO.setUseCache(false);
  }

  private ImageCodec() {}

  /** 헤더의 가로·세로 */
  public record Size(int width, int height) {

    public int longSide() {
      return Math.max(width, height);
    }

    public long pixels() {
      return (long) width * height;
    }
  }

  /** 디코딩 전에 막은 사진: 헤더의 가로×세로가 상한을 넘는다 */
  public static final class TooManyPixelsException extends IOException {

    public TooManyPixelsException(Size size) {
      super("픽셀 수 상한 초과: " + size.width() + "x" + size.height());
    }
  }

  /** 디코딩하지 않고 헤더에서 가로·세로만 읽는다 */
  public static Size size(byte[] bytes, String format) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      ImageReader reader = reader(format);
      try {
        reader.setInput(in, true, true);
        return checked(new Size(reader.getWidth(0), reader.getHeight(0)));
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * 디코딩한다. 헤더의 가로×세로가 maxPixels를 넘으면 디코딩 전에 TooManyPixelsException. 긴 변이 targetMaxSide의 2배를 넘으면
   * 서브샘플링으로 줄여 읽어 메모리를 아낀다 (최종 크기는 fit으로 맞춘다).
   */
  public static BufferedImage decode(byte[] bytes, String format, int targetMaxSide, long maxPixels)
      throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      ImageReader reader = reader(format);
      try {
        // 메타데이터는 읽지 않는다 (방향은 metadata-extractor로 따로 읽는다)
        reader.setInput(in, true, true);
        Size size = checked(new Size(reader.getWidth(0), reader.getHeight(0)));
        if (size.pixels() > maxPixels) {
          throw new TooManyPixelsException(size);
        }
        ImageReadParam param = reader.getDefaultReadParam();
        int step = size.longSide() / (targetMaxSide * 2);
        if (step >= 2) {
          param.setSourceSubsampling(step, step, 0, 0);
        }
        BufferedImage image = reader.read(0, param);
        if (image == null) {
          throw new IOException("디코딩 결과가 없어요");
        }
        return image;
      } finally {
        reader.dispose();
      }
    }
  }

  private static Size checked(Size size) throws IOException {
    if (size.width() <= 0 || size.height() <= 0) {
      throw new IOException("가로·세로가 0 이하예요");
    }
    return size;
  }

  private static ImageReader reader(String format) {
    String name = PNG.equals(format) ? PNG : JPEG;
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(name);
    if (!readers.hasNext()) {
      throw new IllegalStateException("ImageIO " + name + " 리더가 없어요");
    }
    return readers.next();
  }

  /** EXIF Orientation (1~8). 없거나 읽지 못하면 1 */
  public static int orientation(byte[] bytes) {
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
  public static BufferedImage fit(BufferedImage src, int maxSide) {
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
  public static BufferedImage orient(BufferedImage src, int orientation) {
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

  /** 메타데이터 없이 JPEG로 쓴다 (ImageIO 기본 JFIF 헤더만, EXIF 없음). quality는 0~1 */
  public static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(JPEG);
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
