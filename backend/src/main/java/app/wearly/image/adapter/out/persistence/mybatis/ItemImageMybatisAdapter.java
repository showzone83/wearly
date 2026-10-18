package app.wearly.image.adapter.out.persistence.mybatis;

import app.wearly.image.adapter.out.persistence.mybatis.mapper.ItemImageMapper;
import app.wearly.image.application.port.out.ItemImageQueryPort;
import app.wearly.image.application.port.out.dto.StaleImageDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** ItemImageQueryPort 구현 (MyBatis) */
@Component
class ItemImageMybatisAdapter implements ItemImageQueryPort {

  private final ItemImageMapper mapper;

  ItemImageMybatisAdapter(ItemImageMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void lockUploads(long userId) {
    mapper.lockUploads(userId);
  }

  @Override
  public long countCreatedSince(long userId, Instant since) {
    return mapper.countCreatedSince(userId, since);
  }

  @Override
  public List<StaleImageDto> findStale(Instant before, int limit) {
    return mapper.findStale(before, limit);
  }
}
