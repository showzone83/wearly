package app.wearly.common.web;

import app.wearly.common.error.ApiException;

/** 경로의 ID를 숫자로 바꾼다. 숫자가 아니거나 0 이하면 없는 리소스로 본다 (404). */
public final class PathIds {

  private PathIds() {}

  public static long parse(String id) {
    try {
      long value = Long.parseLong(id);
      if (value <= 0) {
        throw ApiException.notFound();
      }
      return value;
    } catch (NumberFormatException e) {
      throw ApiException.notFound();
    }
  }
}
