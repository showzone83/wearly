-- 로그인 이력에 비밀번호 실패·2단계 인증 결과를 더한다 (계약: doc/22_관리자_계정_관리.md 4.1, doc/16 3장)
--   LOGIN_FAILED: 가입한 이메일로 비밀번호가 틀림 (없는 이메일은 남기지 않는다)
--   MFA_SUCCESS · MFA_FAILED: 관리자 TOTP·복구 코드 확인 성공·실패
--   MFA_LOCKED: 5번 틀려 15분 잠김
ALTER TABLE tb_auth_event DROP CONSTRAINT ck_auth_event_type;
ALTER TABLE tb_auth_event ADD CONSTRAINT ck_auth_event_type CHECK (type IN (
    'LOGIN', 'LOGOUT', 'REFRESH_REUSED', 'FORCED_LOGOUT',
    'LOGIN_FAILED', 'MFA_SUCCESS', 'MFA_FAILED', 'MFA_LOCKED'));

-- 관리자 화면 "관리자 로그인 기록" (최신순 전체 목록)
CREATE INDEX ix_auth_event_created ON tb_auth_event (created_at DESC, auth_event_id DESC);
