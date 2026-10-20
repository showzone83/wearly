-- 쇼핑몰 캡처로 옷 사진 등록 (계약: doc/24_쇼핑몰_캡처_등록.md)
-- 앱이 고른 쇼핑몰을 앱 안 웹 화면으로 열고, 사용자가 상품 사진을 정사각 틀에 맞춰 캡처해 옷 사진으로 쓴다.
-- 쇼핑몰 목록은 코드 그룹 MALL이다. attrs.url = 처음 열 주소(https만). 관리자 코드 관리에서 더하고 끈다.
-- 새 테이블·컬럼이 없어 COMMENT ON은 없다 (tb_code_group·tb_code 코멘트는 V24).

INSERT INTO tb_code_group (group_code, name, description, sort_order) VALUES
    ('MALL', '쇼핑몰', '옷 사진을 캡처해 올 쇼핑몰. attrs.url(처음 열 주소, https만, 500자 이하). 활성 코드만 앱 목록에 나온다', 130);

INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('MALL', 'MUSINSA', '무신사', NULL, 10, '{"url":"https://www.musinsa.com"}'::jsonb),
    ('MALL', '29CM', '29CM', NULL, 20, '{"url":"https://www.29cm.co.kr"}'::jsonb),
    ('MALL', 'WCONCEPT', 'W컨셉', NULL, 30, '{"url":"https://www.wconcept.co.kr"}'::jsonb),
    ('MALL', 'ZIGZAG', '지그재그', NULL, 40, '{"url":"https://zigzag.kr"}'::jsonb),
    ('MALL', 'ABLY', '에이블리', NULL, 50, '{"url":"https://m.a-bly.com"}'::jsonb);
