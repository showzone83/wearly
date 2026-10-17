-- 관리자 역할 · AI 공급자 · 변경 이력 (계약: doc/07_관리자_사이트.md 2~4장)

-- ---------------------------------------------------------------------------
-- 회원 역할: USER | ADMIN. 관리자는 서버 시작 시 WEARLY_ADMIN_* 로만 만든다 (가입 API로는 못 만든다)
-- ---------------------------------------------------------------------------
ALTER TABLE tb_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN'));

-- ---------------------------------------------------------------------------
-- AI 공급자. 키 값은 저장하지 않고 키가 들어 있는 환경변수 이름만 둔다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_ai_provider (
    provider_id  VARCHAR(30)  PRIMARY KEY,        -- 관리자가 정하는 이름 (claude, openai, gemini …)
    type         VARCHAR(20)  NOT NULL,           -- 구현 종류
    display_name VARCHAR(50)  NOT NULL,
    model        VARCHAR(100),                    -- 사용(enabled)하려면 필수
    api_key_env  VARCHAR(100) NOT NULL,           -- 키가 들어 있는 환경변수 이름
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    priority     INT          NOT NULL DEFAULT 100, -- 작을수록 먼저 쓴다
    options      JSONB        NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- MOCK 구현은 목업 실행(WEARLY_AI_PROVIDER=mock)에서만 쓰고 행으로 두지 않는다
    CONSTRAINT ck_ai_provider_type CHECK (type IN ('CLAUDE', 'OPENAI', 'GEMINI')),
    CONSTRAINT ck_ai_provider_enabled_model CHECK (NOT enabled OR (model IS NOT NULL AND model <> ''))
);

INSERT INTO tb_ai_provider (provider_id, type, display_name, model, api_key_env, enabled, priority, options) VALUES
    ('claude', 'CLAUDE', 'Claude', 'claude-opus-5-5', 'ANTHROPIC_API_KEY', TRUE, 1, '{"effort":"medium"}'::jsonb),
    ('openai', 'OPENAI', 'OpenAI', NULL, 'OPENAI_API_KEY', FALSE, 2, '{}'::jsonb),
    ('gemini', 'GEMINI', 'Gemini', NULL, 'GEMINI_API_KEY', FALSE, 3, '{}'::jsonb);

-- ---------------------------------------------------------------------------
-- AI 호출 기록: provider는 provider_id를 그대로 저장한다. 관리자 시험 분석은 feature='ADMIN_TEST'
-- ---------------------------------------------------------------------------
ALTER TABLE tb_ai_analysis_log
    DROP CONSTRAINT ck_ai_analysis_log_provider,
    DROP CONSTRAINT ck_ai_analysis_log_feature;
ALTER TABLE tb_ai_analysis_log
    ALTER COLUMN provider TYPE VARCHAR(30),
    ADD CONSTRAINT ck_ai_analysis_log_feature CHECK (feature IN ('ITEM_ANALYZE', 'ADMIN_TEST'));

-- 관리자 기록 조회·대시보드 집계용
CREATE INDEX ix_ai_analysis_log_created ON tb_ai_analysis_log (created_at DESC, ai_analysis_log_id DESC);
-- 일일 한도는 ITEM_ANALYZE 성공만 센다
DROP INDEX ix_ai_analysis_log_user_success;
CREATE INDEX ix_ai_analysis_log_user_success ON tb_ai_analysis_log (user_id, created_at)
    WHERE status = 'SUCCESS' AND feature = 'ITEM_ANALYZE';

-- ---------------------------------------------------------------------------
-- 관리자 변경 이력. 관리자가 탈퇴해도 이력은 남긴다 (admin_user_id는 NULL이 된다)
-- ---------------------------------------------------------------------------
CREATE TABLE tb_admin_audit_log (
    audit_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id BIGINT       REFERENCES tb_user (user_id) ON DELETE SET NULL,
    action        VARCHAR(50)  NOT NULL,
    target_type   VARCHAR(30)  NOT NULL,          -- CODE, AI_PROVIDER, USER
    target_id     VARCHAR(100) NOT NULL,
    before        JSONB,
    after         JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_audit_log_created ON tb_admin_audit_log (created_at DESC, audit_id DESC);
CREATE INDEX ix_admin_audit_log_target ON tb_admin_audit_log (target_type, created_at DESC);

CREATE INDEX ix_user_created ON tb_user (created_at);
