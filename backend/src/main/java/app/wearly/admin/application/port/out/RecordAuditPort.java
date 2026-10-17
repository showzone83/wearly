package app.wearly.admin.application.port.out;

/** 관리자 변경 이력 남기기 (tb_admin_audit_log). 변경과 같은 트랜잭션에 쓴다 (변경이 롤백되면 이력도 남지 않는다) */
public interface RecordAuditPort {

  /** before·after는 응답 모양 객체나 Map. JSON 객체로 바꿔 저장한다 (null이면 비움) */
  void record(
      Long adminUserId,
      String action,
      String targetType,
      String targetId,
      Object before,
      Object after);
}
