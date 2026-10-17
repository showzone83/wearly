package app.wearly.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** LIKE 패턴 이스케이프 (L3, Spring 없이) */
class LikePatternsTest {

  @Test
  void 특수문자를_이스케이프해_포함_패턴을_만든다() {
    assertThat(LikePatterns.contains("셔츠")).isEqualTo("%셔츠%");
    assertThat(LikePatterns.contains("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    assertThat(LikePatterns.contains(null)).isNull();
  }
}
