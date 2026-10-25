-- 옷장 도우미 대리 작업 기록 (doc/26_옷장_도우미.md 4.7, 이슈 #11). pairing 컨텍스트.
--  - 돕는 사람이 주인 옷장에서 옷을 등록·수정·사용 중지·다시 사용할 때마다 한 행. 옷 저장과 같은 트랜잭션에서 남긴다.
--  - 조회·AI 분석은 남기지 않는다 (데이터가 바뀌지 않음).
--  - item_id에는 외래 키를 걸지 않는다: 주인이 옷을 완전 삭제해도 기록은 남고, 그때 이름(item_name)으로 보인다.
--  - notified_at: 주인에게 묶음 알림을 보낸 시각 (3단계). 연결이 지워지면(탈퇴) 함께 지운다.
CREATE TABLE tb_closet_proxy_action (
    action_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pairing_id  BIGINT      NOT NULL REFERENCES tb_closet_pairing (pairing_id) ON DELETE CASCADE,
    owner_id    BIGINT      NOT NULL,
    helper_id   BIGINT      NOT NULL,
    action      VARCHAR(20) NOT NULL,
    item_id     BIGINT      NOT NULL,
    item_name   VARCHAR(40),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    notified_at TIMESTAMPTZ,
    CONSTRAINT ck_closet_proxy_action_action
        CHECK (action IN ('ITEM_CREATE', 'ITEM_UPDATE', 'ITEM_RETIRE', 'ITEM_RESTORE'))
);
CREATE INDEX ix_closet_proxy_action_pairing ON tb_closet_proxy_action (pairing_id, created_at DESC);
CREATE INDEX ix_closet_proxy_action_unnotified ON tb_closet_proxy_action (pairing_id) WHERE notified_at IS NULL;

COMMENT ON TABLE tb_closet_proxy_action IS '옷장 도우미 대리 작업 기록: 돕는 사람이 주인 옷장에서 한 일 (doc/26 4.7). 옷 저장과 같은 트랜잭션';
COMMENT ON COLUMN tb_closet_proxy_action.action_id IS '기록 ID';
COMMENT ON COLUMN tb_closet_proxy_action.pairing_id IS '연결 (tb_closet_pairing). 연결이 지워지면 함께 지운다';
COMMENT ON COLUMN tb_closet_proxy_action.owner_id IS '옷장 주인 (tb_user, 기록 당시 연결 값)';
COMMENT ON COLUMN tb_closet_proxy_action.helper_id IS '작업한 돕는 사람 (tb_user, 기록 당시 연결 값)';
COMMENT ON COLUMN tb_closet_proxy_action.action IS '한 일: ITEM_CREATE(등록) · ITEM_UPDATE(수정, 사진 바꾸기 포함) · ITEM_RETIRE(사용 중지) · ITEM_RESTORE(다시 사용)';
COMMENT ON COLUMN tb_closet_proxy_action.item_id IS '옷 (tb_wardrobe_item). 외래 키 없음: 주인이 완전 삭제해도 기록은 남는다';
COMMENT ON COLUMN tb_closet_proxy_action.item_name IS '기록 당시 옷 이름 (40자까지)';
COMMENT ON COLUMN tb_closet_proxy_action.created_at IS '작업 시각';
COMMENT ON COLUMN tb_closet_proxy_action.notified_at IS '주인에게 묶음 알림을 보낸 시각. 아직 안 보냈으면 NULL (3단계)';
