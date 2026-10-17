package app.wearly.wardrobe.application.port.in;

import app.wearly.wardrobe.application.port.in.dto.WardrobeStatsDto;

/** /wardrobe/stats 집계. 날짜 기준은 Asia/Seoul */
public interface WardrobeStatsUseCase {

  WardrobeStatsDto stats(long userId);
}
