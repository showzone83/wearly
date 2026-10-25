-- 탈퇴 유예 기간 (doc/27_탈퇴_유예.md, 이슈 #19). user 컨텍스트.
--  - 탈퇴를 신청하면 회원 행을 바로 지우지 않고 상태를 WITHDRAWN(탈퇴 예정)으로 둔다. 유예 기간(wearly.user.withdrawal.grace,
--    기본 30일) 안에 다시 로그인하면 취소할 수 있고, 지나면 예약 작업이 지금처럼 회원 행을 지운다(CASCADE).
--  - 탈퇴 기록(tb_user_withdrawal)은 회원 행이 지워져도 남도록 tb_user에 FK를 두지 않는다. 이메일·이름은 넣지 않는다.
ALTER TABLE tb_user DROP CONSTRAINT ck_user_status;
ALTER TABLE tb_user
    ADD CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'));
COMMENT ON COLUMN tb_user.status IS '상태: ACTIVE(사용 중) · SUSPENDED(관리자 정지) · WITHDRAWN(탈퇴 예정, 유예 기간 뒤 삭제, doc/27)';

-- 옷장 도우미: 탈퇴를 신청하면 그 회원의 연결을 바로 끝낸다 (ended_by WITHDRAWN)
ALTER TABLE tb_closet_pairing DROP CONSTRAINT ck_closet_pairing_ended_by;
ALTER TABLE tb_closet_pairing
    ADD CONSTRAINT ck_closet_pairing_ended_by CHECK (ended_by IN ('OWNER', 'HELPER', 'EXPIRED', 'ADMIN', 'WITHDRAWN'));
COMMENT ON COLUMN tb_closet_pairing.ended_by IS '끝낸 쪽: OWNER(주인 해제·거절) · HELPER(돕는 사람 해제·취소) · EXPIRED(승인 대기 만료) · ADMIN(관리자 강제 해제) · WITHDRAWN(어느 한쪽의 탈퇴 신청, doc/27)';

CREATE TABLE tb_user_withdrawal (
    withdrawal_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    reason        VARCHAR(20)  NOT NULL,
    reason_text   VARCHAR(200),
    requested_at  TIMESTAMPTZ  NOT NULL,
    purge_after   TIMESTAMPTZ  NOT NULL,
    canceled_at   TIMESTAMPTZ,
    canceled_by   VARCHAR(10),
    purged_at     TIMESTAMPTZ,
    purged_by     VARCHAR(10),
    CONSTRAINT ck_user_withdrawal_reason CHECK (reason IN ('NOT_USEFUL', 'BAD_RECOMMEND', 'HARD_TO_USE', 'PRIVACY', 'OTHER_APP', 'OTHER')),
    CONSTRAINT ck_user_withdrawal_canceled_by CHECK (canceled_by IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_withdrawal_purged_by CHECK (purged_by IN ('SCHEDULER', 'ADMIN')),
    CONSTRAINT ck_user_withdrawal_canceled CHECK ((canceled_at IS NULL) = (canceled_by IS NULL)),
    CONSTRAINT ck_user_withdrawal_purged CHECK ((purged_at IS NULL) = (purged_by IS NULL)),
    CONSTRAINT ck_user_withdrawal_closed_once CHECK (canceled_at IS NULL OR purged_at IS NULL)
);
-- 회원마다 열린(취소·삭제 안 된) 탈퇴는 하나뿐이다
CREATE UNIQUE INDEX uq_user_withdrawal_open ON tb_user_withdrawal (user_id) WHERE canceled_at IS NULL AND purged_at IS NULL;
-- 예약 삭제가 기한이 지난 열린 탈퇴를 찾는다
CREATE INDEX ix_user_withdrawal_due ON tb_user_withdrawal (purge_after) WHERE canceled_at IS NULL AND purged_at IS NULL;
CREATE INDEX ix_user_withdrawal_requested ON tb_user_withdrawal (requested_at DESC);

COMMENT ON TABLE tb_user_withdrawal IS '탈퇴 기록 (doc/27): 신청·취소·삭제 시각과 사유. 회원 행이 지워져도 남도록 FK가 없고 개인정보(이메일·이름)는 넣지 않는다';
COMMENT ON COLUMN tb_user_withdrawal.withdrawal_id IS '탈퇴 기록 ID';
COMMENT ON COLUMN tb_user_withdrawal.user_id IS '탈퇴한 회원 ID (tb_user.user_id, FK 없음 — 삭제 뒤에도 남는다)';
COMMENT ON COLUMN tb_user_withdrawal.reason IS '사유 (필수): NOT_USEFUL(자주 안 씀) · BAD_RECOMMEND(추천이 마음에 안 듦) · HARD_TO_USE(쓰기 어려움) · PRIVACY(개인정보 걱정) · OTHER_APP(다른 앱) · OTHER(기타)';
COMMENT ON COLUMN tb_user_withdrawal.reason_text IS '회원이 적은 하고 싶은 말 (선택, 200자). 관리자 화면에서만 본다';
COMMENT ON COLUMN tb_user_withdrawal.requested_at IS '탈퇴 신청 시각';
COMMENT ON COLUMN tb_user_withdrawal.purge_after IS '이 시각이 지나면 예약 작업이 회원을 지운다 (신청 시각 + wearly.user.withdrawal.grace)';
COMMENT ON COLUMN tb_user_withdrawal.canceled_at IS '탈퇴를 취소한 시각. 취소하지 않았으면 NULL';
COMMENT ON COLUMN tb_user_withdrawal.canceled_by IS '취소한 쪽: USER(다시 로그인해 취소) · ADMIN(관리자). 취소하지 않았으면 NULL';
COMMENT ON COLUMN tb_user_withdrawal.purged_at IS '회원을 지운 시각. 지우지 않았으면 NULL';
COMMENT ON COLUMN tb_user_withdrawal.purged_by IS '지운 쪽: SCHEDULER(예약 작업) · ADMIN(관리자 바로 삭제). 지우지 않았으면 NULL';
