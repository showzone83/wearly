package app.wearly.pairing.adapter.out.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tb_closet_pairing 리포지토리 */
public interface PairingJpaRepository extends JpaRepository<PairingEntity, Long> {

  /** 두 회원 사이의 끝나지 않은 연결 (부분 고유 인덱스 uq_closet_pairing_unended로 하나뿐) */
  @Query(
      "SELECT p FROM PairingEntity p WHERE p.ownerId = :ownerId AND p.helperId = :helperId"
          + " AND p.status <> 'ENDED'")
  Optional<PairingEntity> findUnended(
      @Param("ownerId") long ownerId, @Param("helperId") long helperId);

  /** 이 회원이 어느 쪽이든 끝나지 않은 연결 */
  @Query(
      "SELECT p FROM PairingEntity p WHERE (p.ownerId = :userId OR p.helperId = :userId)"
          + " AND p.status <> 'ENDED' ORDER BY p.id")
  List<PairingEntity> findUnendedOf(@Param("userId") long userId);
}
