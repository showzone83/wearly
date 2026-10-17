package app.wearly.ai.domain;

import app.wearly.common.domain.FieldError;
import java.util.List;
import java.util.Optional;

/** 웹 입력 키 규칙과 키 고르기 (07 문서 6장). 오류 메시지·로그에는 키 값을 넣지 않는다. */
public final class ApiKeyRules {

  public static final int MIN_LENGTH = 20;
  public static final int MAX_LENGTH = 300;

  private ApiKeyRules() {}

  /** 앞뒤 공백을 지운 뒤 20~300자, 공백·줄바꿈 없음. @return 정리한 키 (틀리면 오류를 더하고 null) */
  public static String normalize(String raw, List<FieldError> errors) {
    String key = raw == null ? null : raw.strip();
    if (key == null || key.length() < MIN_LENGTH || key.length() > MAX_LENGTH) {
      errors.add(new FieldError("apiKey", MIN_LENGTH + "~" + MAX_LENGTH + "자로 입력해 주세요."));
      return null;
    }
    if (key.chars().anyMatch(Character::isWhitespace)) {
      errors.add(new FieldError("apiKey", "공백이나 줄바꿈이 들어 있어요."));
      return null;
    }
    return key;
  }

  /** 화면 표시용 끝 4자 (••••abcd) */
  public static String last4(String key) {
    return key.substring(key.length() - 4);
  }

  /** 고른 키: 값과 출처. 값은 메모리 안에서만 쓴다 */
  public record Choice(KeySource source, String value) {

    public static final Choice NONE = new Choice(KeySource.NONE, null);

    public boolean present() {
      return source != KeySource.NONE;
    }

    /** 로그·응답에 키가 섞이지 않게 값은 문자열로 내보내지 않는다 */
    @Override
    public String toString() {
      return "Choice[" + source + "]";
    }
  }

  /**
   * 키 고르기: 웹에서 넣은 키(DB)가 있고 복호화되면 그것, 없으면 api_key_env 환경변수, 둘 다 없으면 없음.
   *
   * @param storeEnabled 마스터 키가 있어 웹 키를 쓸 수 있는지. 없으면 DB 키는 보지 않는다(환경변수 키는 그대로 쓴다)
   * @param dbSecretExists DB에 그 공급자 키 행이 있는지
   * @param dbKey 복호화한 DB 키 (복호화에 실패했으면 빈 값)
   * @param envKey 환경변수 키
   */
  public static Choice choose(
      boolean storeEnabled,
      boolean dbSecretExists,
      Optional<String> dbKey,
      Optional<String> envKey) {
    if (storeEnabled && dbSecretExists) {
      // 넣어 둔 웹 키를 복호화하지 못하면 환경변수로 몰래 바꾸지 않고 쓸 수 없는 것으로 본다
      return dbKey.map(k -> new Choice(KeySource.DB, k)).orElse(Choice.NONE);
    }
    return envKey.map(k -> new Choice(KeySource.ENV, k)).orElse(Choice.NONE);
  }
}
