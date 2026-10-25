package app.wearly.admin.adapter.out.persistence.mybatis.mapper;

import app.wearly.admin.application.port.out.AdminPairingQueryPort.PairingFilter;
import app.wearly.admin.application.port.out.dto.AdminPairingActionRowDto;
import app.wearly.admin.application.port.out.dto.AdminPairingRowDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 옷장 도우미 연결 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/admin/AdminPairingMapper.xml */
@Mapper
public interface AdminPairingMapper {

  List<AdminPairingRowDto> findPairings(
      @Param("f") PairingFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countPairings(@Param("f") PairingFilter filter);

  AdminPairingRowDto findPairing(@Param("pairingId") long pairingId);

  List<AdminPairingActionRowDto> findActions(
      @Param("pairingId") long pairingId, @Param("limit") int limit);
}
