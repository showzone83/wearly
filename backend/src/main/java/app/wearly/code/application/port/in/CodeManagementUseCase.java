package app.wearly.code.application.port.in;

import app.wearly.common.domain.PatchBody;
import java.util.List;
import java.util.Map;

/**
 * 관리자 코드 관리 (07 문서 4장). 바꾸면 커밋 뒤 코드 캐시를 다시 읽는다(/codes version이 바뀜). 변경 이력은 부르는 쪽(관리자 컨텍스트)이 같은
 * 트랜잭션에서 남기도록 바뀌기 전후 모습을 돌려준다.
 */
public interface CodeManagementUseCase {

  /** 아이템이 쓰는 코드별 개수. 키는 "GROUP:CODE" */
  Map<String, Long> itemUsage();

  /** 그룹이 없으면 404, 같은 코드가 있으면 409 CODE_EXISTS */
  CodeInfo create(String group, CreateCodeCommand command);

  /** 보낸 필드만 바꾼다. attrs를 보내면 attrs 전체를 바꾼다. 코드 문자열은 못 바꾼다 */
  Change patch(String group, String code, PatchBody body);

  /** 보낸 순서대로 sortOrder를 10, 20, …으로. @return 바뀌기 전 코드 순서 */
  List<String> reorder(String group, List<String> order);

  /** 쓰는 아이템·프로필(·하위 세부 종류)이 있으면 409 CODE_IN_USE. @return 지우기 전 모습 */
  CodeInfo delete(String group, String code);

  record Change(CodeInfo before, CodeInfo after) {}
}
