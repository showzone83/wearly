-- 관리자 사이트 SSO: 범용 OIDC 로그인 (계약: doc/22_관리자_계정_관리.md 4.2 2단계)
-- 회사 IdP(Keycloak·Entra ID·Google Workspace 등) 계정과 회원을 잇는다. IdP 계정은 발급자(iss) + subject(sub)로 정한다.
-- 고객 소셜 로그인(tb_user_social: 카카오·애플·구글 앱 로그인용)과 섞지 않도록 따로 둔다 (직원·고객 계정 분리, doc/22 1장).

CREATE TABLE tb_user_sso (
    user_sso_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    provider      VARCHAR(50)  NOT NULL,             -- 연결할 때의 설정 공급자 ID (wearly.sso.providers.<id>). 표시·기록용
    issuer        VARCHAR(500) NOT NULL,             -- ID 토큰 iss (설정의 issuer-uri와 같은 값)
    subject       VARCHAR(255) NOT NULL,             -- ID 토큰 sub (그 발급자 안에서 바뀌지 않는 계정 ID)
    email         VARCHAR(255),                      -- 마지막 로그인 때의 이메일 (참고용, 소문자)
    connected_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_user_sso_subject UNIQUE (issuer, subject)
);
CREATE INDEX ix_user_sso_user ON tb_user_sso (user_id);

-- 로그인 이력에 SSO 로그인·거부를 더한다
--   SSO_LOGIN: 회사 계정으로 관리자 사이트 로그인 (새 세션)
--   SSO_DENIED: IdP 인증은 됐지만 관리자 그룹이 아니라 거부 (이미 연결된 회원만 남긴다)
ALTER TABLE tb_auth_event
    ADD COLUMN sso_provider VARCHAR(50),             -- SSO 공급자 ID (비밀번호 로그인 등은 NULL)
    ADD COLUMN sso_amr      VARCHAR(100);            -- IdP가 알려 준 인증 방법 amr (예: pwd,otp). 없으면 NULL
ALTER TABLE tb_auth_event DROP CONSTRAINT ck_auth_event_type;
ALTER TABLE tb_auth_event ADD CONSTRAINT ck_auth_event_type CHECK (type IN (
    'LOGIN', 'LOGOUT', 'REFRESH_REUSED', 'FORCED_LOGOUT',
    'LOGIN_FAILED', 'MFA_SUCCESS', 'MFA_FAILED', 'MFA_LOCKED',
    'SSO_LOGIN', 'SSO_DENIED'));
