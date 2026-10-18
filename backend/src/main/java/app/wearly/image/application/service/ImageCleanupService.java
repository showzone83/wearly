package app.wearly.image.application.service;

import app.wearly.image.application.port.in.ImageCleanupUseCase;
import app.wearly.image.application.port.in.dto.CleanupResultDto;
import app.wearly.image.application.port.out.AfterCommitPort;
import app.wearly.image.application.port.out.ImageStoragePort;
import app.wearly.image.application.port.out.ItemImagePort;
import app.wearly.image.application.port.out.ItemImageQueryPort;
import app.wearly.image.application.port.out.dto.StaleImageDto;
import app.wearly.image.domain.ImageKeys;
import app.wearly.image.domain.ImageSettings;
import app.wearly.image.domain.ImageStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 사진 정리 (17 문서 3장). 오래된 PENDING(올리다 만 것), 아이템에 안 붙은 READY(올리고 등록하지 않은 것), DELETED(교체·삭제로 버린 것)를
 * 지운다. 먼저 행을 DELETED로 바꿔(그사이 아이템에 붙은 것은 빠진다) 붙이기와 겹치지 않게 하고, 객체를 지운 뒤 성공한 것만 행을 지운다 (실패하면 DELETED
 * 행이 남아 다음 번에 다시). 저장소 호출이 길어서 트랜잭션은 행을 바꾸고 지울 때만 쓴다.
 */
@Service
public class ImageCleanupService implements ImageCleanupUseCase {

  private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);
  private static final int BATCH = 200;
  private static final int MAX_BATCHES = 50;

  private final ItemImagePort images;
  private final ItemImageQueryPort queries;
  private final ImageStoragePort storage;
  private final AfterCommitPort afterCommit;
  private final ImageSettings settings;
  private final Clock clock;

  public ImageCleanupService(
      ItemImagePort images,
      ItemImageQueryPort queries,
      ImageStoragePort storage,
      AfterCommitPort afterCommit,
      ImageSettings settings,
      Clock clock) {
    this.images = images;
    this.queries = queries;
    this.storage = storage;
    this.afterCommit = afterCommit;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  public CleanupResultDto cleanup() {
    Instant before = clock.instant().minus(settings.staleAfter());
    int pending = 0;
    int unattached = 0;
    int deleted = 0;
    for (int batch = 0; batch < MAX_BATCHES; batch++) {
      List<StaleImageDto> stale = queries.findStale(before, BATCH);
      if (stale.isEmpty()) {
        break;
      }
      Map<UUID, StaleImageDto> rows = new LinkedHashMap<>();
      stale.forEach(row -> rows.put(UUID.fromString(row.imageId()), row));
      List<UUID> claimed = images.claimForCleanup(rows.keySet(), clock.instant());
      List<UUID> removable = new ArrayList<>(claimed.size());
      for (UUID id : claimed) {
        StaleImageDto row = rows.get(id);
        try {
          storage.delete(
              List.of(
                  ImageKeys.upload(row.userId(), id),
                  ImageKeys.original(row.userId(), id),
                  ImageKeys.thumb(row.userId(), id)));
        } catch (RuntimeException e) {
          log.warn("정리 작업: 사진 객체 삭제 실패, 다음에 다시 — {} {}", id, e.toString());
          continue;
        }
        removable.add(id);
        switch (ImageStatus.valueOf(row.status())) {
          case PENDING -> pending++;
          case READY -> unattached++;
          case DELETED -> deleted++;
          default -> throw new IllegalStateException(row.status());
        }
      }
      if (removable.isEmpty()) {
        break;
      }
      images.deleteAll(removable);
      if (stale.size() < BATCH) {
        break;
      }
    }
    CleanupResultDto result = new CleanupResultDto(pending, unattached, deleted);
    if (result.total() > 0) {
      log.info("사진 정리: PENDING {} · 안 붙은 READY {} · DELETED {}", pending, unattached, deleted);
    }
    return result;
  }

  @Override
  public void deleteAllOf(long userId) {
    List<String> prefixes = List.of(ImageKeys.userPrefix(userId), ImageKeys.uploadPrefix(userId));
    afterCommit.afterCommit(
        () -> {
          for (String prefix : prefixes) {
            try {
              storage.deletePrefix(prefix);
            } catch (RuntimeException e) {
              log.error("탈퇴 회원 사진 삭제 실패 (직접 지워야 한다): {} — {}", prefix, e.toString());
            }
          }
        });
  }
}
