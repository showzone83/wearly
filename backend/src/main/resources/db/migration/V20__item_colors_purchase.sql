-- 이슈 #4: 아이템 색상 복수(1~3개)·계절 코드 추가·구입일
--  - color: 주 색상 (그대로). sub_colors: 보조 색 0~2개 (COLOR 코드, 순서 유지). API의 colors = [color] + sub_colors
--  - purchased_at: 구입일. 모르면 등록일(서울 날짜)을 넣고 purchased_at_estimated = TRUE ("모름(등록일)")
--  - 이미 있는 아이템: 보조 색 없음, 구입일은 등록일(서울 날짜)이고 추정 표시
--  - SEASON 그룹에 초봄·초여름·초가을 (정렬: 초봄 5, 봄 10, 초여름 15, 여름 20, 초가을 25, 가을 30, 겨울 40 — 기존 순서는 바꾸지 않는다)
ALTER TABLE tb_wardrobe_item
    ADD COLUMN sub_colors             TEXT[]  NOT NULL DEFAULT '{}',
    ADD COLUMN purchased_at           DATE,
    ADD COLUMN purchased_at_estimated BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tb_wardrobe_item
   SET purchased_at = (created_at AT TIME ZONE 'Asia/Seoul')::date,
       purchased_at_estimated = TRUE;

ALTER TABLE tb_wardrobe_item
    ALTER COLUMN purchased_at SET NOT NULL;

-- 보조 색은 2개까지, 주 색상과 겹치지 않는다 (값 검증은 코드 테이블로 서버가 한다). 구입일은 1990-01-01부터
ALTER TABLE tb_wardrobe_item
    ADD CONSTRAINT ck_wardrobe_item_sub_colors CHECK (
        cardinality(sub_colors) <= 2 AND NOT (color = ANY (sub_colors))
    ),
    ADD CONSTRAINT ck_wardrobe_item_purchased_at CHECK (purchased_at >= DATE '1990-01-01');

-- 코드 그룹 SEASON (doc/06_코드_관리.md, doc/06_코드_초기값.json). attrs 없음
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('SEASON', 'EARLY_SPRING', '초봄', NULL, 5, '{}'::jsonb),
    ('SEASON', 'EARLY_SUMMER', '초여름', NULL, 15, '{}'::jsonb),
    ('SEASON', 'EARLY_FALL', '초가을', NULL, 25, '{}'::jsonb);
