-- 비밀번호 변경 주기 (doc/28_비밀번호_변경주기.md, 이슈 #21). auth 컨텍스트.
--  - 비밀번호를 바꾼 지 주기(wearly.auth.password.change-interval, 기본 90일)가 지나면 바꾸라고 알린다. 막지는 않는다.
--  - 기한 = max(password_changed_at, password_postponed_at) + 주기. 회원이 "연장"을 고르면 password_postponed_at = 그 시각.
--  - 이미 있는 행은 이 마이그레이션 시각부터 센다 (배포 첫날 알림이 한꺼번에 가지 않게).
ALTER TABLE tb_user_local_credential
    ADD COLUMN password_changed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN password_postponed_at TIMESTAMPTZ,
    ADD COLUMN password_reminded_at  TIMESTAMPTZ;
COMMENT ON COLUMN tb_user_local_credential.password_changed_at IS '비밀번호를 마지막으로 정한 시각 (가입·변경, doc/28)';
COMMENT ON COLUMN tb_user_local_credential.password_postponed_at IS '비밀번호 변경 알림을 마지막으로 연장한 시각. 바꾸면 비운다 (doc/28)';
COMMENT ON COLUMN tb_user_local_credential.password_reminded_at IS '비밀번호 변경 푸시를 마지막으로 보낸 시각 (주기마다 한 번). 바꾸면 비운다 (doc/28)';

-- 로그인 이력: 비밀번호 변경·연장
ALTER TABLE tb_auth_event DROP CONSTRAINT ck_auth_event_type;
ALTER TABLE tb_auth_event ADD CONSTRAINT ck_auth_event_type CHECK (type IN (
    'LOGIN', 'LOGOUT', 'REFRESH_REUSED', 'FORCED_LOGOUT',
    'LOGIN_FAILED', 'MFA_SUCCESS', 'MFA_FAILED', 'MFA_LOCKED',
    'SSO_LOGIN', 'SSO_DENIED',
    'PASSWORD_CHANGED', 'PASSWORD_POSTPONED'));
COMMENT ON COLUMN tb_auth_event.type IS '종류: LOGIN · LOGOUT · REFRESH_REUSED · FORCED_LOGOUT · LOGIN_FAILED(가입한 이메일로 비밀번호 틀림) · MFA_SUCCESS · MFA_FAILED · MFA_LOCKED(5번 틀려 15분 잠김) · SSO_LOGIN(회사 계정 로그인) · SSO_DENIED(IdP 인증은 됐지만 관리자 그룹이 아니라 거부) · PASSWORD_CHANGED(비밀번호 변경) · PASSWORD_POSTPONED(비밀번호 변경 알림 연장)';
