package app.wearly.pairing.domain;

import app.wearly.sharedkernel.shortcode.ShortCodes;

/**
 * 연결 코드 (doc/26 4.1). 8자, 글자 규칙은 {@link ShortCodes}. 1회용이고 정해진 시간(기본 24시간)이 지나면 사라진다. 코드는 옷장 주인만
 * 만들고(#18) 회원은 코드를 하나만 가진다.
 *
 * @param issuerId 코드를 만든 회원
 * @param issuerRole 코드를 만든 회원의 자리. OWNER: 입력한 사람이 돕는 사람이 되고 주인 승인을 기다린다. HELPER는 #18 전에 만든 코드로,
 *     입력하면 없는 코드로 본다
 */
public record PairingCode(long issuerId, PairingRole issuerRole) {

  public static final int LENGTH = 8;

  public static String generate() {
    return ShortCodes.generate(LENGTH);
  }

  public static String normalize(String code) {
    return ShortCodes.normalize(code);
  }

  public static boolean isFormat(String code) {
    return ShortCodes.isFormat(code, LENGTH);
  }
}
