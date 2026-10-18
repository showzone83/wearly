package app.wearly.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** 테스트 사진 만들기 (외부 파일 없이): 색 띠 PNG·JPEG, EXIF(방향·GPS)를 넣은 JPEG */
public final class TestImages {

  private TestImages() {}

  /** 왼쪽 절반 left 색, 오른쪽 절반 right 색인 그림 */
  public static BufferedImage halves(int width, int height, Color left, Color right, int type) {
    BufferedImage image = new BufferedImage(width, height, type);
    Graphics2D g = image.createGraphics();
    g.setColor(left);
    g.fillRect(0, 0, width / 2, height);
    g.setColor(right);
    g.fillRect(width / 2, 0, width - width / 2, height);
    g.dispose();
    return image;
  }

  public static byte[] png(int width, int height) {
    return encode(halves(width, height, Color.RED, Color.BLUE, BufferedImage.TYPE_INT_ARGB), "png");
  }

  public static byte[] jpeg(int width, int height) {
    return encode(halves(width, height, Color.RED, Color.BLUE, BufferedImage.TYPE_INT_RGB), "jpeg");
  }

  public static byte[] encode(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, format, out)) {
        throw new IllegalStateException("인코더가 없어요: " + format);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * JPEG의 SOI 바로 뒤에 EXIF APP1(IFD0: Orientation, GPSInfo → GPS IFD: GPSLatitudeRef "N")을 끼워 넣는다.
   * 휴대폰 사진처럼 방향과 위치 정보가 든 JPEG를 흉내 낸다.
   */
  public static byte[] withExif(byte[] jpeg, int orientation) {
    ByteBuffer tiff = ByteBuffer.allocate(64);
    // TIFF 헤더 (빅 엔디언 MM), IFD0은 8번 바이트부터
    tiff.put(new byte[] {'M', 'M', 0, 0x2A}).putInt(8);
    // IFD0: 항목 2개
    tiff.putShort((short) 2);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);
    tiff.putShort((short) orientation).putShort((short) 0);
    int gpsOffset = 8 + 2 + 2 * 12 + 4;
    tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsOffset);
    tiff.putInt(0);
    // GPS IFD: GPSLatitudeRef = "N"
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2);
    tiff.put(new byte[] {'N', 0, 0, 0});
    tiff.putInt(0);
    byte[] tiffBytes = new byte[tiff.position()];
    tiff.flip().get(tiffBytes);

    byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
    int length = 2 + exifHeader.length + tiffBytes.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xFF);
    out.write(0xE1);
    out.write((length >> 8) & 0xFF);
    out.write(length & 0xFF);
    out.writeBytes(exifHeader);
    out.writeBytes(tiffBytes);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  /** 무작위 색 점으로 채운 JPEG (압축이 잘 안 돼 품질에 따라 크기가 달라진다). seed가 같으면 같은 그림 */
  public static byte[] noisyJpeg(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    java.util.Random random = new java.util.Random(width * 31L + height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, random.nextInt(0xFFFFFF));
      }
    }
    return encode(image, "jpeg");
  }

  /** JPEG 시작 바이트 뒤가 쓰레기인 가짜 JPEG */
  public static byte[] fakeJpeg() {
    byte[] b = new byte[2048];
    b[0] = (byte) 0xFF;
    b[1] = (byte) 0xD8;
    b[2] = (byte) 0xFF;
    for (int i = 3; i < b.length; i++) {
      b[i] = (byte) (i * 31);
    }
    return b;
  }
}
