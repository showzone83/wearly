-- 로그인 세션을 Valkey로 (계약: doc/16_세션_Valkey.md 3장)
-- Refresh 토큰·세션은 Valkey(wearly:rt:*, wearly:session:* …)로 옮기므로 DB 표를 지운다.
-- 로컬 개발 단계라 기존 로그인은 모두 끊긴다 (서버를 올리면 다시 로그인).
DROP TABLE IF EXISTS tb_refresh_token;

-- 회원별 로그인 이력: 로그인·로그아웃·재사용 감지·강제 로그아웃 (관리자 회원 상세의 "최근 로그인 이력")
-- 관리자가 누가 끊었는지는 tb_admin_audit_log(SESSION_REVOKE · USER_SESSIONS_REVOKE)에 따로 남는다.
CREATE TABLE tb_auth_event (
    auth_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    type          VARCHAR(20)  NOT NULL,
    session_id    VARCHAR(36),                    -- 로그인 세션 ID (UUID 문자열)
    client        VARCHAR(10),                    -- APP · ADMIN · WEB
    ip            VARCHAR(45),
    user_agent    VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_event_type CHECK (type IN ('LOGIN', 'LOGOUT', 'REFRESH_REUSED', 'FORCED_LOGOUT')),
    CONSTRAINT ck_auth_event_client CHECK (client IS NULL OR client IN ('APP', 'ADMIN', 'WEB'))
);
CREATE INDEX ix_auth_event_user_created ON tb_auth_event (user_id, created_at DESC, auth_event_id DESC);
