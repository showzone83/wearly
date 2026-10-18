-- 나의 정보: 연령대·체형·키·핏 선호 (GitHub 이슈 #5, 기획서 P2 "체형·핏 선호도 반영")
-- 모두 선택 입력이고 언제든 비울 수 있다(NULL). 코디 추천 AI의 사용자 메시지에 참고로 넣고, 고도화 때 새 옷 추천에 쓴다.
-- 회원 탈퇴 때는 tb_user_profile 행과 함께 지워진다 (user_id ON DELETE CASCADE).
ALTER TABLE tb_user_profile
    ADD COLUMN age_range      VARCHAR(30),   -- AGE_RANGE 코드
    ADD COLUMN body_type      VARCHAR(30),   -- BODY_TYPE 코드
    ADD COLUMN height_cm      SMALLINT,      -- 키 (cm)
    ADD COLUMN fit_preference VARCHAR(30);   -- FIT 코드

ALTER TABLE tb_user_profile
    ADD CONSTRAINT ck_user_profile_height CHECK (height_cm IS NULL OR height_cm BETWEEN 130 AND 210);

-- 코드 그룹 (doc/06_코드_관리.md, doc/06_코드_초기값.json)
INSERT INTO tb_code_group (group_code, name, description, sort_order) VALUES
    ('AGE_RANGE', '연령대', '프로필 나의 정보의 연령대(ageRange). attrs 없음', 100),
    ('BODY_TYPE', '체형', '프로필 나의 정보의 체형(bodyType, 골격 유형). attrs.description(짧은 설명), hint(고르기를 돕는 한 줄 특징)', 110),
    ('FIT', '핏 선호', '프로필 나의 정보의 핏 선호(fitPreference). attrs 없음', 120);

-- 연령대: 직장인 대상이라 20~30대는 5년 단위(사회 초년생·주니어·시니어), 40대부터는 10년 단위
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('AGE_RANGE', 'AGE_20_EARLY', '20대 초반', NULL, 10, '{}'::jsonb),
    ('AGE_RANGE', 'AGE_20_LATE', '20대 후반', NULL, 20, '{}'::jsonb),
    ('AGE_RANGE', 'AGE_30_EARLY', '30대 초반', NULL, 30, '{}'::jsonb),
    ('AGE_RANGE', 'AGE_30_LATE', '30대 후반', NULL, 40, '{}'::jsonb),
    ('AGE_RANGE', 'AGE_40', '40대', NULL, 50, '{}'::jsonb),
    ('AGE_RANGE', 'AGE_50_PLUS', '50대 이상', NULL, 60, '{}'::jsonb);

-- 체형: 골격 유형 3가지 + 잘 모르겠어요
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('BODY_TYPE', 'STRAIGHT', '스트레이트', NULL, 10,
     '{"description":"탄탄하고 입체적인 몸. 심플한 정핏이 잘 어울려요","hint":"상체에 볼륨이 있고 허리 위치가 높은 편이에요"}'::jsonb),
    ('BODY_TYPE', 'WAVE', '웨이브', NULL, 20,
     '{"description":"부드럽고 가녀린 몸. 허리를 살린 가벼운 옷이 잘 어울려요","hint":"하체에 볼륨이 있고 어깨·윗몸이 얇은 편이에요"}'::jsonb),
    ('BODY_TYPE', 'NATURAL', '내추럴', NULL, 30,
     '{"description":"뼈대가 또렷한 몸. 여유 있는 핏과 자연스러운 소재가 잘 어울려요","hint":"어깨가 넓고 쇄골·손목 같은 관절이 도드라진 편이에요"}'::jsonb),
    ('BODY_TYPE', 'UNKNOWN', '잘 모르겠어요', NULL, 40,
     '{"description":"체형은 빼고 추천해요","hint":"나중에 언제든 고를 수 있어요"}'::jsonb);

-- 핏 선호
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('FIT', 'SLIM', '슬림핏', NULL, 10, '{}'::jsonb),
    ('FIT', 'REGULAR', '레귤러핏', NULL, 20, '{}'::jsonb),
    ('FIT', 'OVERSIZED', '오버핏', NULL, 30, '{}'::jsonb);
