package app.wearly.code.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 코드 한 줄 (관리자 쓰기용 도메인 모델). 코드 문자열과 그룹은 만든 뒤 바꾸지 않는다 */
public class Code {

  private final Long id;
  private final String groupCode;
  private final String code;
  private String label;
  private String parentCode;
  private int sortOrder;
  private boolean active;
  private Map<String, Object> attrs;

  /** 내용이 같아도 updated_at을 올릴지 (순서 바꾸기·pairs 반영 — 버전 문자열이 확실히 바뀌게) */
  private boolean touched;

  public Code(
      Long id,
      String groupCode,
      String code,
      String label,
      String parentCode,
      int sortOrder,
      boolean active,
      Map<String, Object> attrs) {
    this.id = id;
    this.groupCode = groupCode;
    this.code = code;
    this.label = label;
    this.parentCode = parentCode;
    this.sortOrder = sortOrder;
    this.active = active;
    this.attrs = attrs == null ? Map.of() : attrs;
  }

  public static Code newCode(
      String groupCode,
      String code,
      String label,
      String parentCode,
      int sortOrder,
      boolean active,
      Map<String, Object> attrs) {
    return new Code(
        null, groupCode, code, label, parentCode, sortOrder, active, new LinkedHashMap<>(attrs));
  }

  public void update(
      String label, String parentCode, int sortOrder, boolean active, Map<String, Object> attrs) {
    this.label = label;
    this.parentCode = parentCode;
    this.sortOrder = sortOrder;
    this.active = active;
    this.attrs = new LinkedHashMap<>(attrs);
  }

  public void moveTo(int sortOrder) {
    this.sortOrder = sortOrder;
    this.touched = true;
  }

  public void replaceAttrs(Map<String, Object> attrs) {
    this.attrs = new LinkedHashMap<>(attrs);
  }

  public Long id() {
    return id;
  }

  public String groupCode() {
    return groupCode;
  }

  public String code() {
    return code;
  }

  public String label() {
    return label;
  }

  public String parentCode() {
    return parentCode;
  }

  public int sortOrder() {
    return sortOrder;
  }

  public boolean active() {
    return active;
  }

  public Map<String, Object> attrs() {
    return attrs;
  }

  public boolean touched() {
    return touched;
  }
}
