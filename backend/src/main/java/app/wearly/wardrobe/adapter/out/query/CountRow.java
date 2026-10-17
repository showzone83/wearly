package app.wearly.wardrobe.adapter.out.query;

/** 집계 결과 (키, 개수) */
public class CountRow {

  private String key;
  private long count;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }
}
