-- 관리자 2단계 인증 TOTP (RFC 6238) (계약: doc/22_관리자_계정_관리.md 4.1)
-- 비밀은 AI 키와 같은 AES-256-GCM 마스터 키(wearly.secret-key)로 암호화한 값만 둔다. AAD = "mfa:{user_id}"라
-- 다른 회원 행에 옮겨 붙이면 복호화되지 않는다. 마스터 키를 바꾸거나 잃으면 복구 코드로 들어와 다시 등록한다.

CREATE TABLE tb_user_mfa (
    user_id        BIGINT      PRIMARY KEY REFERENCES tb_user (user_id) ON DELETE CASCADE,
    secret_cipher  BYTEA       NOT NULL,             -- 암호문 + GCM 태그(16바이트)
    secret_nonce   BYTEA       NOT NULL,             -- 저장할 때마다 새 무작위 12바이트
    key_version    SMALLINT    NOT NULL DEFAULT 1,   -- 마스터 키 버전 (지금 1)
    last_used_step BIGINT,                           -- 마지막으로 받은 30초 단계 (같은 코드 재사용 막기)
    enrolled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_mfa_nonce CHECK (octet_length(secret_nonce) = 12)
);

-- 복구 코드 10개 (등록할 때 한 번만 보여 준다). 원문은 두지 않고 SHA-256 hex("{user_id}:{정규화한 코드}")만 둔다
CREATE TABLE tb_user_mfa_recovery_code (
    recovery_code_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    code_hash        VARCHAR(64) NOT NULL,
    used_at          TIMESTAMPTZ,                    -- 쓴 코드는 다시 쓸 수 없다
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_mfa_recovery_code UNIQUE (user_id, code_hash)
);
