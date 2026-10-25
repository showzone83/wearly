package app.wearly.invite.domain;

import app.wearly.sharedkernel.shortcode.ShortCodes;

/**
 * 초대 코드 (doc/25). 8자, 헷갈리는 0·1·I·L·O를 뺀 대문자·숫자 31자에서 고른다 (약 8.5×10^11가지, 글자 규칙은 {@link
 * ShortCodes}). DB에는 정리한 8자로 두고 화면에는 {@code ABCD-EFGH}로 보인다. 입력은 대소문자·공백·하이픈을 무시한다.
 */
public final class InviteCodes {

  public static final int LENGTH = 8;

  /** 가입 이력에 남기는 입력 코드 최대 길이 (tb_invite_signup.entered_code) */
  public static final int ENTERED_MAX = 20;

  private InviteCodes() {}

  /** 새 코드 (정리한 8자) */
  public static String generate() {
    return ShortCodes.generate(LENGTH);
  }

  /** 대소문자·공백·하이픈을 무시한다. null이면 빈 문자열 */
  public static String normalize(String code) {
    return ShortCodes.normalize(code);
  }

  /** 정리했을 때 8자이고 모두 쓰는 글자인지 */
  public static boolean isFormat(String code) {
    return ShortCodes.isFormat(code, LENGTH);
  }

  /** 가입 이력에 남길 입력 코드: 정리한 값을 {@link #ENTERED_MAX}자까지 */
  public static String entered(String code) {
    String n = normalize(code);
    return n.length() > ENTERED_MAX ? n.substring(0, ENTERED_MAX) : n;
  }
}
