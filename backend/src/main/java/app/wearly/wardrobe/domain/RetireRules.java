package app.wearly.wardrobe.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.ArrayList;
import java.util.List;

/** 사용 중지 입력 규칙 (13 문서 3장): 이유는 필수이고 활성 RETIRE_REASON 코드, 메모는 앞뒤 공백을 지운 뒤 200자 이하(빈 메모는 없음으로 본다). */
public final class RetireRules {

  public static final int MEMO_MAX = 200;

  static final String REQUIRED = "필수 값이에요.";
  static final String MEMO_TOO_LONG = "메모는 200자까지 쓸 수 있어요.";

  private RetireRules() {}

  /** 코드 테이블 없이 볼 수 있는 불변식 (필수·길이). 필드 이름은 reason·memo */
  public static List<FieldError> validate(String reason, String memo) {
    List<FieldError> errors = new ArrayList<>();
    if (reason == null || reason.isBlank()) {
      errors.add(new FieldError("reason", REQUIRED));
    }
    String m = memo(memo);
    if (m != null && m.codePointCount(0, m.length()) > MEMO_MAX) {
      errors.add(new FieldError("memo", MEMO_TOO_LONG));
    }
    return errors;
  }

  /** 불변식 + 이유가 활성 RETIRE_REASON 코드인지 (비활성·모르는 코드는 400) */
  public static List<FieldError> validate(CodeSnapshot codes, String reason, String memo) {
    List<FieldError> errors = validate(reason, memo);
    if (reason != null && !reason.isBlank()) {
      CodeChecks.one(codes, CodeGroups.RETIRE_REASON, "reason", reason, null, errors);
    }
    return errors;
  }

  /** 저장할 메모: 앞뒤 공백을 지우고, 비면 null */
  public static String memo(String memo) {
    return memo == null || memo.isBlank() ? null : memo.strip();
  }
}
