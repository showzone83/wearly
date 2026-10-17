-- 관리자 역할, AI 공급자 관리, 관리자 변경 이력 (계약: doc/07_관리자_사이트.md)

-- ---------------------------------------------------------------------------
-- 관리자 역할
-- ---------------------------------------------------------------------------
ALTER TABLE tb_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE tb_user ADD CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN'));
CREATE INDEX ix_user_created ON tb_user (created_at DESC, user_id DESC);

-- ---------------------------------------------------------------------------
-- AI 공급자. 키 값은 저장하지 않고 키가 든 환경변수 이름만 둔다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_ai_provider (
    provider_id  VARCHAR(30)  PRIMARY KEY,
    type         VARCHAR(20)  NOT NULL,
    display_name VARCHAR(50)  NOT NULL,
    model        VARCHAR(100),
    api_key_env  VARCHAR(100) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    priority     INT          NOT NULL DEFAULT 100,
    options      JSONB        NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_provider_type CHECK (type IN ('CLAUDE', 'OPENAI', 'GEMINI', 'MOCK')),
    -- 사용하려면 모델이 있어야 한다
    CONSTRAINT ck_ai_provider_enabled_model CHECK (NOT enabled OR (model IS NOT NULL AND model <> ''))
);

INSERT INTO tb_ai_provider (provider_id, type, display_name, model, api_key_env, enabled, priority, options) VALUES
    ('claude', 'CLAUDE', 'Claude', 'claude-opus-5-5', 'ANTHROPIC_API_KEY', TRUE, 1, '{"effort": "medium"}'),
    ('openai', 'OPENAI', 'OpenAI', NULL, 'OPENAI_API_KEY', FALSE, 2, '{}'),
    ('gemini', 'GEMINI', 'Gemini', NULL, 'GEMINI_API_KEY', FALSE, 3, '{}');

-- ---------------------------------------------------------------------------
-- AI 호출 기록: provider는 이제 tb_ai_provider.provider_id(또는 mock)를 그대로 저장한다.
-- 공급자 행을 지워도 기록은 남도록 FK는 두지 않는다. 관리자 시험 분석(ADMIN_TEST)도 남긴다
-- ---------------------------------------------------------------------------
ALTER TABLE tb_ai_analysis_log DROP CONSTRAINT ck_ai_analysis_log_provider;
ALTER TABLE tb_ai_analysis_log ALTER COLUMN provider TYPE VARCHAR(30);
ALTER TABLE tb_ai_analysis_log DROP CONSTRAINT ck_ai_analysis_log_feature;
ALTER TABLE tb_ai_analysis_log ADD CONSTRAINT ck_ai_analysis_log_feature
    CHECK (feature IN ('ITEM_ANALYZE', 'ADMIN_TEST'));
-- 관리자 기록 검색·대시보드 집계용
CREATE INDEX ix_ai_analysis_log_created ON tb_ai_analysis_log (created_at DESC, ai_analysis_log_id DESC);
CREATE INDEX ix_ai_analysis_log_provider_created ON tb_ai_analysis_log (provider, created_at);

-- ---------------------------------------------------------------------------
-- 관리자 변경 이력 (코드·AI 공급자·회원). 관리자 계정이 지워져도 이력은 남긴다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_admin_audit_log (
    audit_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id BIGINT       REFERENCES tb_user (user_id) ON DELETE SET NULL, -- 시작 시 자동 처리는 NULL
    action        VARCHAR(40)  NOT NULL,
    target_type   VARCHAR(30)  NOT NULL,
    target_id     VARCHAR(100) NOT NULL,
    before        JSONB,
    after         JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_audit_log_created ON tb_admin_audit_log (created_at DESC, audit_id DESC);
CREATE INDEX ix_admin_audit_log_target ON tb_admin_audit_log (target_type, created_at DESC);
