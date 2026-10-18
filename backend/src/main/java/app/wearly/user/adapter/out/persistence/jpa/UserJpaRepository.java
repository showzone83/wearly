package app.wearly.user.adapter.out.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

  @Query(value = "SELECT count(*) FROM tb_user WHERE role = 'ADMIN'", nativeQuery = true)
  long countAdmins();

  /** 동시에 두 관리자를 해제해도 한쪽이 기다렸다가 바뀐 행을 다시 본다 (PostgreSQL READ COMMITTED + FOR UPDATE) */
  @Query(
      value =
          "SELECT user_id FROM tb_user WHERE role = 'ADMIN' AND status = 'ACTIVE'"
              + " ORDER BY user_id FOR UPDATE",
      nativeQuery = true)
  List<Long> lockActiveAdminIds();
}
