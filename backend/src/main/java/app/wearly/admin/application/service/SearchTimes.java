package app.wearly.admin.application.service;

import app.wearly.admin.domain.SearchText;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 관리자 목록의 기간 조건(쿼리 문자열) → 시각. YYYY-MM-DD는 한국 날짜, 그 밖은 ISO-8601 시각 */
final class SearchTimes {

  private SearchTimes() {}

  /**
   * @param end true면 날짜의 다음 날 0시 (기간 끝은 [from, to) 로 쓴다)
   * @return 비었으면 null. 형식이 틀리면 400 VALIDATION_FAILED
   */
  static Instant parse(String field, String raw, boolean end) {
    String v = SearchText.blankToNull(raw);
    if (v == null) {
      return null;
    }
    try {
      if (v.length() == 10) {
        LocalDate d = LocalDate.parse(v);
        return Zones.startOf(end ? d.plusDays(1) : d);
      }
      return Instant.parse(v);
    } catch (DateTimeParseException e) {
      throw ApiException.validation(field, "YYYY-MM-DD 또는 ISO-8601 시각이어야 해요.");
    }
  }

  /**
   * 날짜만 받는 조건 (YYYY-MM-DD, 한국 날짜).
   *
   * @return 비었으면 null. 형식이 틀리면 400 VALIDATION_FAILED
   */
  static LocalDate date(String field, String raw) {
    String v = SearchText.blankToNull(raw);
    if (v == null) {
      return null;
    }
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException e) {
      throw ApiException.validation(field, "YYYY-MM-DD 형식이어야 해요.");
    }
  }
}
