-- AI 키 웹 입력 (쓰기 전용 · 암호화 저장) (계약: doc/07_관리자_사이트.md 6장)

-- ---------------------------------------------------------------------------
-- 공급자 키. AES-256-GCM 암호문만 둔다. 마스터 키(WEARLY_SECRET_KEY)는 DB에 두지 않는다.
-- AAD에 provider_id를 넣어 암호화하므로 다른 공급자 행에 옮겨 붙이면 복호화되지 않는다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_ai_provider_secret (
    provider_id VARCHAR(30) PRIMARY KEY REFERENCES tb_ai_provider (provider_id) ON DELETE CASCADE,
    ciphertext  BYTEA       NOT NULL,             -- 암호문 + GCM 태그(16바이트)
    nonce       BYTEA       NOT NULL,             -- 저장할 때마다 새 무작위 12바이트
    key_version SMALLINT    NOT NULL DEFAULT 1,   -- 마스터 키 버전 (나중에 KMS·키 교체용, 지금 1)
    last4       VARCHAR(4)  NOT NULL,             -- 화면 표시용 키 끝 4자
    updated_by  BIGINT      REFERENCES tb_user (user_id) ON DELETE SET NULL, -- 넣은 관리자 (탈퇴 시 NULL)
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_provider_secret_nonce CHECK (octet_length(nonce) = 12)
);
