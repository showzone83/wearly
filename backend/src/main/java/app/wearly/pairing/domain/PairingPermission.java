package app.wearly.pairing.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 돕는 사람의 권한 (tb_closet_pairing.permission, doc/26 D3). 누적 단계라 위 단계는 아래 단계를 모두 포함한다. 코디 추천·예정
 * 코디(PLAN)는 후속 이슈에서 더한다. API 값과 DB 값이 같다
 */
public enum PairingPermission {
  /** 주인 옷 보기만 */
  VIEW,
  /** 옷 등록·수정·사진 바꾸기·AI 분석·사용 중지·다시 사용 (완전 삭제는 주인만) */
  EDIT;

  /** 이 권한으로 required 단계의 일을 할 수 있는지 */
  public boolean allows(PairingPermission required) {
    return ordinal() >= required.ordinal();
  }

  public static List<String> names() {
    return Arrays.stream(values()).map(Enum::name).toList();
  }

  /** 대소문자는 가리지 않는다. 모르는 값이면 빈 값 */
  public static Optional<PairingPermission> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(p -> p.name().equals(v)).findFirst();
  }
}
