package app.wearly.user.application.port.out;

import app.wearly.user.domain.Withdrawal;
import java.util.Optional;

/** 탈퇴 기록 저장·조회 (tb_user_withdrawal, JPA) */
public interface WithdrawalPort {

  /** 새 기록이면 만들고(id 채움), 있으면 닫힌 시각·닫은 쪽을 바꾼다 */
  Withdrawal save(Withdrawal withdrawal);

  /** 회원의 열린(취소·삭제 안 된) 기록. 하나뿐이다 (부분 고유 인덱스) */
  Optional<Withdrawal> findOpen(long userId);
}
