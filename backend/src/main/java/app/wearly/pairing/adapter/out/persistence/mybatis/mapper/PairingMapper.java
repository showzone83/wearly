package app.wearly.pairing.adapter.out.persistence.mybatis.mapper;

import app.wearly.pairing.application.port.out.dto.ClaimedActionRowDto;
import app.wearly.pairing.application.port.out.dto.PairingRowDto;
import app.wearly.pairing.application.port.out.dto.ProxyActionRowDto;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 옷장 도우미 연결 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/pairing/PairingMapper.xml */
@Mapper
public interface PairingMapper {

  long countOpenAsOwner(
      @Param("ownerId") long ownerId, @Param("pendingSince") Instant pendingSince);

  long countOpenAsHelper(
      @Param("helperId") long helperId, @Param("pendingSince") Instant pendingSince);

  List<PairingRowDto> findOpen(
      @Param("userId") long userId, @Param("pendingSince") Instant pendingSince);

  String findUserName(@Param("userId") long userId);

  List<Long> findDueDigests(
      @Param("quietBefore") Instant quietBefore, @Param("waitedBefore") Instant waitedBefore);

  List<ClaimedActionRowDto> claimUnnotified(
      @Param("pairingId") long pairingId, @Param("upTo") Instant upTo, @Param("now") Instant now);

  List<ProxyActionRowDto> findActions(
      @Param("pairingId") long pairingId, @Param("limit") int limit);
}
