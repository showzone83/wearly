package app.wearly.code.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** 관리자 코드 입력 규칙 (07 문서 4장): 코드 형식, 라벨, parent */
public final class CodeRules {

  /** COLOR는 영문 소문자(앱 ColorKey와 같은 모양), 그 밖은 영문 대문자. 숫자·밑줄 허용, 1~30자 */
  public static final Pattern UPPER_CODE = Pattern.compile("^[A-Z0-9_]{1,30}$");

  public static final Pattern LOWER_CODE = Pattern.compile("^[a-z0-9_]{1,30}$");

  public static final int LABEL_MAX = 50;

  private CodeRules() {}

  /**
   * @return 앞뒤 공백을 지운 코드 (형식이 틀리면 오류를 더한다)
   */
  public static String code(String group, String raw, List<FieldError> errors) {
    String code = raw == null ? null : raw.strip();
    Pattern format = CodeGroups.COLOR.equals(group) ? LOWER_CODE : UPPER_CODE;
    if (code == null || !format.matcher(code).matches()) {
      // COLOR는 영문 소문자, 그 밖은 영문 대문자 (숫자·밑줄 포함 1~30자)
      errors.add(
          FieldError.invalidFormat("code", CodeGroups.COLOR.equals(group) ? "lower" : "upper"));
    }
    return code;
  }

  public static String label(String raw, List<FieldError> errors) {
    String label = raw == null ? null : raw.strip();
    if (label == null || label.isEmpty()) {
      errors.add(FieldError.required("label"));
    } else if (label.length() > LABEL_MAX) {
      errors.add(FieldError.tooLong("label", LABEL_MAX));
    }
    return label;
  }

  /** SUB_TYPE은 parent(CATEGORY 코드) 필수, 다른 그룹은 parent를 둘 수 없다 */
  public static String parent(
      String group, String raw, CodeSnapshot snapshot, List<FieldError> errors) {
    String parent = raw == null || raw.isBlank() ? null : raw.strip();
    if (CodeGroups.SUB_TYPE.equals(group)) {
      if (parent == null) {
        // 세부 종류는 상위 종류(CATEGORY 코드)가 필요하다
        errors.add(FieldError.required("parent"));
      } else if (!snapshot.known(CodeGroups.CATEGORY, parent)) {
        errors.add(FieldError.notAllowed("parent"));
      }
    } else if (parent != null) {
      // 세부 종류(SUB_TYPE)만 상위 코드를 가질 수 있다
      errors.add(FieldError.notAllowed("parent"));
    }
    return parent;
  }

  /** 순서 바꾸기: 그룹의 코드 전부를 한 번씩 보내야 한다 */
  public static boolean isCompleteOrder(List<String> existing, List<String> order) {
    return order != null
        && order.size() == existing.size()
        && new HashSet<>(order).size() == order.size()
        && new HashSet<>(existing).equals(new HashSet<>(order));
  }

  /** 변경 이력·사용 수의 키 "GROUP:CODE" */
  public static String target(String group, String code) {
    return group + ":" + code;
  }
}
