package app.wearly.auth.domain;

/** 같은 이메일의 자격 증명이 이미 있을 때 (동시 가입으로 고유 제약에 걸린 경우). 서비스가 409 EMAIL_TAKEN으로 바꾼다 */
public class EmailTakenException extends RuntimeException {

  public EmailTakenException(String email) {
    super("이미 가입된 이메일: " + email);
  }
}
