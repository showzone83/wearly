package app.wearly.ai.application.port.in.dto;

/** 형식(과 verify면 공급자) 확인을 마친 키. 키 값은 메모리 안에서만 쓰고 문자열로 내보내지 않는다 */
public record VerifiedKeyDto(String providerId, String key) {

  @Override
  public String toString() {
    return "VerifiedKeyDto[providerId=" + providerId + ", key=***]";
  }
}
