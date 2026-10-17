-- 사용하지 않는 옷 (계약: doc/13_옷_사용_중지.md 2장)
-- 옷장에서 빼되 데이터는 남긴다. retired_at이 NULL이면 사용 중이다. 보유 상태(status)와는 별개다.
ALTER TABLE tb_wardrobe_item
    ADD COLUMN retired_at    TIMESTAMPTZ,
    ADD COLUMN retire_reason VARCHAR(30),       -- RETIRE_REASON 코드
    ADD COLUMN retire_memo   VARCHAR(200);

-- 사용 중이면 세 값이 모두 비고, 사용 중지면 시각과 이유가 함께 있다 (메모는 선택)
ALTER TABLE tb_wardrobe_item
    ADD CONSTRAINT ck_wardrobe_item_retired CHECK (
        (retired_at IS NULL AND retire_reason IS NULL AND retire_memo IS NULL)
        OR (retired_at IS NOT NULL AND retire_reason IS NOT NULL)
    );

-- 목록·통계·추천은 사용 중인 옷만 보는 경우가 많다
CREATE INDEX ix_wardrobe_item_in_use ON tb_wardrobe_item (user_id) WHERE retired_at IS NULL;

-- 코드 그룹 RETIRE_REASON (doc/06_코드_관리.md, doc/06_코드_초기값.json). attrs 없음
INSERT INTO tb_code_group (group_code, name, description, sort_order) VALUES
    ('RETIRE_REASON', '사용 중지 이유', '옷을 사용하지 않는 옷으로 옮긴 이유. attrs 없음', 80);

INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('RETIRE_REASON', 'NOT_WORN', '안 입어요', NULL, 10, '{}'::jsonb),
    ('RETIRE_REASON', 'DISCARDED', '버렸어요', NULL, 20, '{}'::jsonb),
    ('RETIRE_REASON', 'DONATED', '기부했어요', NULL, 30, '{}'::jsonb),
    ('RETIRE_REASON', 'SOLD', '팔았어요', NULL, 40, '{}'::jsonb),
    ('RETIRE_REASON', 'ETC', '기타', NULL, 50, '{}'::jsonb);
