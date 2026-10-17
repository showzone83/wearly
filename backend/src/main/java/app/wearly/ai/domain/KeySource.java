package app.wearly.ai.domain;

/** 공급자가 지금 실제로 쓰는 키의 출처 (07 문서 6장) */
public enum KeySource {
    /** 관리자 화면에서 넣은 키 (tb_ai_provider_secret, 암호화 저장) */
    DB,
    /** api_key_env가 가리키는 환경변수 */
    ENV,
    /** 쓸 수 있는 키가 없음 */
    NONE
}
