package app.wearly.wardrobe.application.port.in;

/** /wardrobe/stats 집계. 날짜 기준은 Asia/Seoul */
public interface WardrobeStatsUseCase {

  WardrobeStats stats(long userId);
}
