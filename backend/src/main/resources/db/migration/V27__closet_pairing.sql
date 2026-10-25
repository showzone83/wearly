-- 옷장 도우미 연결 (doc/26_옷장_도우미.md, 이슈 #11). pairing 컨텍스트.
--  - 돕는 사람(helper)이 옷장 주인(owner)의 옷을 대신 등록·관리하려면 먼저 연결한다.
--  - 주인 코드를 돕는 사람이 입력하면 PENDING(주인 승인 대기), 돕는 사람 코드를 주인이 입력하면 바로 ACTIVE.
--    코드 자체는 Valkey에 24시간 둔다 (wearly:pairing:code:*).
--  - 해제·거절·대기 만료는 지우지 않고 ENDED로 남긴다 (대리 작업 기록이 이 행을 가리킨다).
--    같은 두 사람 사이에 끝나지 않은 연결은 하나뿐이다 (부분 고유 인덱스).
--  - 어느 한쪽이 탈퇴하면 함께 지운다 (CASCADE). 옷은 원래 주인 옷장에 있으므로 영향이 없다.
CREATE TABLE tb_closet_pairing (
    pairing_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id     BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    helper_id    BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    permission   VARCHAR(10),
    status       VARCHAR(10) NOT NULL,
    issued_by    VARCHAR(10) NOT NULL,
    notify_owner BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at  TIMESTAMPTZ,
    ended_at     TIMESTAMPTZ,
    ended_by     VARCHAR(10),
    CONSTRAINT ck_closet_pairing_self CHECK (owner_id <> helper_id),
    CONSTRAINT ck_closet_pairing_permission CHECK (permission IN ('VIEW', 'EDIT')),
    CONSTRAINT ck_closet_pairing_status CHECK (status IN ('PENDING', 'ACTIVE', 'ENDED')),
    CONSTRAINT ck_closet_pairing_issued_by CHECK (issued_by IN ('OWNER', 'HELPER')),
    CONSTRAINT ck_closet_pairing_ended_by CHECK (ended_by IN ('OWNER', 'HELPER', 'EXPIRED')),
    CONSTRAINT ck_closet_pairing_active_permission CHECK (status <> 'ACTIVE' OR permission IS NOT NULL),
    CONSTRAINT ck_closet_pairing_ended CHECK ((status = 'ENDED') = (ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_closet_pairing_unended ON tb_closet_pairing (owner_id, helper_id) WHERE status <> 'ENDED';
CREATE INDEX ix_closet_pairing_owner ON tb_closet_pairing (owner_id, created_at DESC) WHERE status <> 'ENDED';
CREATE INDEX ix_closet_pairing_helper ON tb_closet_pairing (helper_id, created_at DESC) WHERE status <> 'ENDED';

COMMENT ON TABLE tb_closet_pairing IS '옷장 도우미 연결: 돕는 사람이 옷장 주인의 옷을 대신 등록·관리 (doc/26). 끝난 연결도 ENDED로 남긴다';
COMMENT ON COLUMN tb_closet_pairing.pairing_id IS '연결 ID';
COMMENT ON COLUMN tb_closet_pairing.owner_id IS '옷장 주인 (tb_user). 옷은 이 회원 옷장에 저장된다';
COMMENT ON COLUMN tb_closet_pairing.helper_id IS '돕는 사람 (tb_user)';
COMMENT ON COLUMN tb_closet_pairing.permission IS '돕는 사람 권한 (누적): VIEW(보기) · EDIT(등록·수정·사용 중지·다시 사용, 완전 삭제는 주인만). 승인 전(PENDING)이면 NULL';
COMMENT ON COLUMN tb_closet_pairing.status IS '상태: PENDING(주인 승인 대기, wearly.pairing.pending-ttl 기본 7일 지나면 만료로 본다) · ACTIVE(연결됨) · ENDED(해제·거절·만료)';
COMMENT ON COLUMN tb_closet_pairing.issued_by IS '연결 코드를 만든 쪽: OWNER(돕는 사람이 입력해 승인 대기) · HELPER(주인이 입력해 바로 연결)';
COMMENT ON COLUMN tb_closet_pairing.notify_owner IS '돕는 사람의 작업을 주인에게 알릴지 (주인이 바꾼다)';
COMMENT ON COLUMN tb_closet_pairing.created_at IS '연결 요청 시각 (코드를 입력한 시각)';
COMMENT ON COLUMN tb_closet_pairing.approved_at IS '연결된 시각. 승인 전이면 NULL';
COMMENT ON COLUMN tb_closet_pairing.ended_at IS '끝난 시각. 끝나지 않았으면 NULL';
COMMENT ON COLUMN tb_closet_pairing.ended_by IS '끝낸 쪽: OWNER(주인 해제·거절) · HELPER(돕는 사람 해제·취소) · EXPIRED(승인 대기 만료)';
