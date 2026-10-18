package app.wearly.common.error;

import java.sql.SQLException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 업무 예외가 아닌 예외의 분류. 의미가 분명한 DB 예외(낙관적 잠금 실패, 고유 제약 위반)는 409 CONFLICT, 일시적 의존성 장애(DB 연결·Valkey 연결·시간
 * 초과)는 503 SERVICE_UNAVAILABLE, 그 밖은 500 INTERNAL_ERROR.
 */
final class SystemErrors {

  /** PostgreSQL unique_violation */
  private static final String UNIQUE_VIOLATION = "23505";

  private SystemErrors() {}

  static ErrorCode classify(Throwable e) {
    if (find(e, OptimisticLockingFailureException.class) != null) {
      return ErrorCode.CONFLICT;
    }
    if (find(e, DataIntegrityViolationException.class) != null) {
      return UNIQUE_VIOLATION.equals(sqlState(e)) ? ErrorCode.CONFLICT : ErrorCode.INTERNAL_ERROR;
    }
    if (find(e, TransientDataAccessException.class) != null
        || find(e, DataAccessResourceFailureException.class) != null
        || find(e, CannotCreateTransactionException.class) != null) {
      return ErrorCode.SERVICE_UNAVAILABLE;
    }
    return ErrorCode.INTERNAL_ERROR;
  }

  /** CONFLICT처럼 요청 쪽 사정으로 보는 분류인지 (스택 없이 WARN으로 남긴다) */
  static boolean expected(ErrorCode code) {
    return code == ErrorCode.CONFLICT;
  }

  /** 로그용: 원인 사슬의 SQLState (없으면 null) */
  static String sqlState(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof SQLException s && s.getSQLState() != null) {
        return s.getSQLState();
      }
    }
    return null;
  }

  private static <T extends Throwable> T find(Throwable e, Class<T> type) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return type.cast(t);
      }
    }
    return null;
  }
}
