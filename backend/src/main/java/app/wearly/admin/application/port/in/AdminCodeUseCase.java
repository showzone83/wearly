package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminCodesDto;
import app.wearly.admin.application.port.in.dto.AdminCodesDto.AdminCodeDto;
import app.wearly.code.application.port.in.dto.CreateCodeDto;
import app.wearly.code.application.port.in.dto.PatchCodeDto;
import java.util.List;

/** 관리자 코드 API (07 문서 4장 "코드"). 실제 변경은 code 컨텍스트가 하고, 변경 이력을 같은 트랜잭션에 남긴다 */
public interface AdminCodeUseCase {

  /** /codes 모양 + 그룹 description, 코드별 usageCount */
  AdminCodesDto list();

  AdminCodeDto create(long adminId, String group, CreateCodeDto command);

  /** 보낸 순서대로 sortOrder 10, 20, …. 응답(GET /admin/codes 모양)은 커밋 뒤 다시 읽은 list()로 만든다 */
  void reorder(long adminId, String group, List<String> codes);

  AdminCodeDto patch(long adminId, String group, String code, PatchCodeDto patch);

  void delete(long adminId, String group, String code);
}
