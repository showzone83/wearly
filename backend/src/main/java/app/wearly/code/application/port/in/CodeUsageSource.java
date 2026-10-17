package app.wearly.code.application.port.in;

import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import java.util.List;

/**
 * 코드를 쓰는 쪽(옷장 아이템·프로필)이 구현하는 "코드 사용처" 입력 포트 (H1). code 컨텍스트는 다른 컨텍스트의 테이블을 SQL로 읽지 않고 이 포트로 사용 수를
 * 묻는다. 구현은 각 컨텍스트의 입력 어댑터(adapter.in.code)가 자기 유스케이스를 불러서 한다. 그래서 의존은 wardrobe → code, user → code
 * 한 방향으로만 생긴다.
 *
 * <p>코드 값을 담는 컬럼을 새로 만들면 그 컨텍스트의 구현에 함께 넣는다 (삭제 가드가 이 포트로만 사용 여부를 본다).
 */
public interface CodeUsageSource {

  /** 사용처 종류. 삭제 거부 문구의 이름과 순서 (아이템 → 프로필) */
  enum Kind {
    ITEM("아이템"),
    PROFILE("프로필");

    private final String label;

    Kind(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  Kind kind();

  /** 코드별 사용 수 (쓰는 코드만, 그룹 전부) */
  List<CodeUsageCountDto> countByCode();

  /** 이 코드를 쓰는 개수 (이 사용처가 쓰지 않는 그룹이면 0) */
  long countUsing(String group, String code);
}
