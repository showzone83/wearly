package app.wearly.admin.application.port.out;

import java.util.Map;

/** 관리자 변경 이력 남기기 (tb_admin_audit_log). 변경과 같은 트랜잭션에 쓴다 (변경이 롤백되면 이력도 남지 않는다) */
public interface RecordAuditPort {

  /**
   * before·after는 바뀌기 전후 모습 (JSON 객체로 저장, null이면 비움). 값은 JSON으로 바꿀 수 있는 자바 값이고 Instant는 ISO-8601
   * UTC(밀리초) 문자열로 저장한다.
   */
  void record(
      Long adminUserId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> before,
      Map<String, Object> after);
}
