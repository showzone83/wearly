-- 옷장 도우미 관리자 강제 해제 (doc/26 6단계): 연결을 끝낸 쪽에 ADMIN을 더한다 (V27 ck_closet_pairing_ended_by).
ALTER TABLE tb_closet_pairing DROP CONSTRAINT ck_closet_pairing_ended_by;
ALTER TABLE tb_closet_pairing
    ADD CONSTRAINT ck_closet_pairing_ended_by CHECK (ended_by IN ('OWNER', 'HELPER', 'EXPIRED', 'ADMIN'));

COMMENT ON COLUMN tb_closet_pairing.ended_by IS '끝낸 쪽: OWNER(주인 해제·거절) · HELPER(돕는 사람 해제·취소) · EXPIRED(승인 대기 만료) · ADMIN(관리자 강제 해제)';
