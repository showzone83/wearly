-- 옷 속성 값을 코드 테이블로 옮긴다 (계약: doc/06_코드_관리.md)
-- 초기값은 doc/06_코드_초기값.json 내용 그대로다. 기존 저장값은 모두 새 코드에 있으므로 데이터 변환은 없다.

CREATE TABLE tb_code_group (
    group_code  VARCHAR(30)  PRIMARY KEY,      -- CATEGORY, SUB_TYPE, COLOR, PATTERN, SEASON, STYLE, ITEM_STATUS
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(300),
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tb_code (
    code_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_code  VARCHAR(30)  NOT NULL REFERENCES tb_code_group (group_code),
    code        VARCHAR(30)  NOT NULL,
    label       VARCHAR(50)  NOT NULL,          -- 한국어 표시 이름
    parent_code VARCHAR(30),                    -- SUB_TYPE만: CATEGORY 코드
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE, -- false면 새로 고를 수 없지만 이미 저장된 값의 이름은 보여 준다
    attrs       JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_code_group_code UNIQUE (group_code, code)
);
CREATE INDEX ix_code_group_sort ON tb_code (group_code, sort_order);

INSERT INTO tb_code_group (group_code, name, description, sort_order) VALUES
    ('CATEGORY', '종류', '옷장 아이템의 큰 분류. attrs.slot은 코디 칸(TOP·BOTTOM·DRESS·OUTER·SHOES·BAG, 없으면 null=코디에 안 들어감)', 10),
    ('SUB_TYPE', '세부 종류', 'parent=CATEGORY 코드. attrs.volume(-1 슬림·0 레귤러·1 볼륨), warmth(0~3 보온), shape(일러스트 모양 키)', 20),
    ('COLOR', '색상', 'attrs.hex(표시색), neutral(무채색·뉴트럴), light(밝기 0~1), pairs(잘 어울리는 색 코드, 양방향으로 적는다)', 30),
    ('PATTERN', '패턴', 'attrs 없음', 40),
    ('SEASON', '계절', 'attrs 없음', 50),
    ('STYLE', '스타일', '아이템 스타일 태그이자 프로필 취향(moods) 값', 60),
    ('ITEM_STATUS', '보유 상태', 'attrs.recommendable: 코디 추천에 넣는지', 70);

-- CATEGORY (종류) 7개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('CATEGORY', 'TOP', '상의', NULL, 10, '{"slot": "TOP"}'),
    ('CATEGORY', 'BOTTOM', '하의', NULL, 20, '{"slot": "BOTTOM"}'),
    ('CATEGORY', 'DRESS', '원피스', NULL, 30, '{"slot": "DRESS"}'),
    ('CATEGORY', 'OUTER', '아우터', NULL, 40, '{"slot": "OUTER"}'),
    ('CATEGORY', 'SHOES', '신발', NULL, 50, '{"slot": "SHOES"}'),
    ('CATEGORY', 'BAG', '가방', NULL, 60, '{"slot": "BAG"}'),
    ('CATEGORY', 'ACC', '액세서리', NULL, 70, '{"slot": null}');

-- SUB_TYPE (세부 종류) 52개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('SUB_TYPE', 'TEE', '티셔츠', 'TOP', 10, '{"volume": 0, "warmth": 0, "shape": "TEE"}'),
    ('SUB_TYPE', 'LONG_TEE', '긴소매 티셔츠', 'TOP', 20, '{"volume": 0, "warmth": 1, "shape": "LONG_TEE"}'),
    ('SUB_TYPE', 'POLO', '폴로 셔츠', 'TOP', 30, '{"volume": 0, "warmth": 0, "shape": "POLO"}'),
    ('SUB_TYPE', 'SHIRT', '셔츠', 'TOP', 40, '{"volume": -1, "warmth": 1, "shape": "SHIRT"}'),
    ('SUB_TYPE', 'BLOUSE', '블라우스', 'TOP', 50, '{"volume": 0, "warmth": 0, "shape": "BLOUSE"}'),
    ('SUB_TYPE', 'KNIT', '니트', 'TOP', 60, '{"volume": 0, "warmth": 2, "shape": "KNIT"}'),
    ('SUB_TYPE', 'SWEATSHIRT', '맨투맨', 'TOP', 70, '{"volume": 0, "warmth": 2, "shape": "SWEATSHIRT"}'),
    ('SUB_TYPE', 'HOODIE', '후드티', 'TOP', 80, '{"volume": 1, "warmth": 2, "shape": "HOODIE"}'),
    ('SUB_TYPE', 'SLEEVELESS', '민소매', 'TOP', 90, '{"volume": -1, "warmth": 0, "shape": "SLEEVELESS"}'),
    ('SUB_TYPE', 'VEST', '베스트', 'TOP', 100, '{"volume": -1, "warmth": 1, "shape": "VEST"}'),
    ('SUB_TYPE', 'SLACKS', '슬랙스', 'BOTTOM', 110, '{"volume": -1, "warmth": 1, "shape": "SLACKS"}'),
    ('SUB_TYPE', 'WIDE', '와이드 팬츠', 'BOTTOM', 120, '{"volume": 1, "warmth": 1, "shape": "WIDE"}'),
    ('SUB_TYPE', 'CHINO', '치노 팬츠', 'BOTTOM', 130, '{"volume": 0, "warmth": 1, "shape": "CHINO"}'),
    ('SUB_TYPE', 'JEANS', '청바지', 'BOTTOM', 140, '{"volume": 0, "warmth": 1, "shape": "JEANS"}'),
    ('SUB_TYPE', 'JOGGER', '조거 팬츠', 'BOTTOM', 150, '{"volume": 0, "warmth": 1, "shape": "JOGGER"}'),
    ('SUB_TYPE', 'SHORTS', '반바지', 'BOTTOM', 160, '{"volume": 0, "warmth": 0, "shape": "SHORTS"}'),
    ('SUB_TYPE', 'SKIRT', '스커트', 'BOTTOM', 170, '{"volume": 1, "warmth": 0, "shape": "SKIRT"}'),
    ('SUB_TYPE', 'PENCIL_SKIRT', '펜슬 스커트', 'BOTTOM', 180, '{"volume": -1, "warmth": 0, "shape": "PENCIL_SKIRT"}'),
    ('SUB_TYPE', 'LONG_SKIRT', '롱스커트', 'BOTTOM', 190, '{"volume": 1, "warmth": 1, "shape": "LONG_SKIRT"}'),
    ('SUB_TYPE', 'DRESS', '원피스', 'DRESS', 200, '{"volume": 0, "warmth": 1, "shape": "DRESS"}'),
    ('SUB_TYPE', 'SHIRT_DRESS', '셔츠 원피스', 'DRESS', 210, '{"volume": 0, "warmth": 1, "shape": "SHIRT_DRESS"}'),
    ('SUB_TYPE', 'KNIT_DRESS', '니트 원피스', 'DRESS', 220, '{"volume": 0, "warmth": 2, "shape": "KNIT_DRESS"}'),
    ('SUB_TYPE', 'JUMPSUIT', '점프수트', 'DRESS', 230, '{"volume": 0, "warmth": 1, "shape": "JUMPSUIT"}'),
    ('SUB_TYPE', 'CARDIGAN', '가디건', 'OUTER', 240, '{"volume": 0, "warmth": 1, "shape": "CARDIGAN"}'),
    ('SUB_TYPE', 'JACKET', '재킷', 'OUTER', 250, '{"volume": 0, "warmth": 2, "shape": "JACKET"}'),
    ('SUB_TYPE', 'BLAZER', '블레이저', 'OUTER', 260, '{"volume": 0, "warmth": 2, "shape": "BLAZER"}'),
    ('SUB_TYPE', 'DENIM_JACKET', '데님 재킷', 'OUTER', 270, '{"volume": 0, "warmth": 2, "shape": "DENIM_JACKET"}'),
    ('SUB_TYPE', 'JUMPER', '점퍼', 'OUTER', 280, '{"volume": 1, "warmth": 2, "shape": "JUMPER"}'),
    ('SUB_TYPE', 'TRENCH', '트렌치코트', 'OUTER', 290, '{"volume": 1, "warmth": 2, "shape": "TRENCH"}'),
    ('SUB_TYPE', 'COAT', '코트', 'OUTER', 300, '{"volume": 1, "warmth": 3, "shape": "COAT"}'),
    ('SUB_TYPE', 'PADDING', '패딩', 'OUTER', 310, '{"volume": 1, "warmth": 3, "shape": "PADDING"}'),
    ('SUB_TYPE', 'FLEECE', '플리스', 'OUTER', 320, '{"volume": 1, "warmth": 2, "shape": "FLEECE"}'),
    ('SUB_TYPE', 'LOAFER', '로퍼', 'SHOES', 330, '{"volume": 0, "warmth": 0, "shape": "LOAFER"}'),
    ('SUB_TYPE', 'DRESS_SHOES', '구두', 'SHOES', 340, '{"volume": 0, "warmth": 0, "shape": "DRESS_SHOES"}'),
    ('SUB_TYPE', 'SNEAKER', '스니커즈', 'SHOES', 350, '{"volume": 0, "warmth": 0, "shape": "SNEAKER"}'),
    ('SUB_TYPE', 'FLATS', '플랫 슈즈', 'SHOES', 360, '{"volume": 0, "warmth": 0, "shape": "FLATS"}'),
    ('SUB_TYPE', 'HEELS', '힐', 'SHOES', 370, '{"volume": 0, "warmth": 0, "shape": "HEELS"}'),
    ('SUB_TYPE', 'BOOTS', '부츠', 'SHOES', 380, '{"volume": 0, "warmth": 2, "shape": "BOOTS"}'),
    ('SUB_TYPE', 'SANDAL', '샌들', 'SHOES', 390, '{"volume": 0, "warmth": 0, "shape": "SANDAL"}'),
    ('SUB_TYPE', 'TOTE', '토트백', 'BAG', 400, '{"volume": 0, "warmth": 0, "shape": "TOTE"}'),
    ('SUB_TYPE', 'SHOULDER', '숄더백', 'BAG', 410, '{"volume": 0, "warmth": 0, "shape": "SHOULDER"}'),
    ('SUB_TYPE', 'CROSSBODY', '크로스백', 'BAG', 420, '{"volume": 0, "warmth": 0, "shape": "CROSSBODY"}'),
    ('SUB_TYPE', 'BACKPACK', '백팩', 'BAG', 430, '{"volume": 0, "warmth": 0, "shape": "BACKPACK"}'),
    ('SUB_TYPE', 'BRIEFCASE', '서류가방', 'BAG', 440, '{"volume": 0, "warmth": 0, "shape": "BRIEFCASE"}'),
    ('SUB_TYPE', 'CLUTCH', '클러치', 'BAG', 450, '{"volume": 0, "warmth": 0, "shape": "CLUTCH"}'),
    ('SUB_TYPE', 'WATCH', '시계', 'ACC', 460, '{"volume": 0, "warmth": 0, "shape": "WATCH"}'),
    ('SUB_TYPE', 'BELT', '벨트', 'ACC', 470, '{"volume": 0, "warmth": 0, "shape": "BELT"}'),
    ('SUB_TYPE', 'TIE', '넥타이', 'ACC', 480, '{"volume": 0, "warmth": 0, "shape": "TIE"}'),
    ('SUB_TYPE', 'SCARF', '머플러', 'ACC', 490, '{"volume": 0, "warmth": 2, "shape": "SCARF"}'),
    ('SUB_TYPE', 'CAP', '모자', 'ACC', 500, '{"volume": 0, "warmth": 0, "shape": "CAP"}'),
    ('SUB_TYPE', 'GLASSES', '안경', 'ACC', 510, '{"volume": 0, "warmth": 0, "shape": "GLASSES"}'),
    ('SUB_TYPE', 'NECKLACE', '목걸이', 'ACC', 520, '{"volume": 0, "warmth": 0, "shape": "NECKLACE"}');

-- COLOR (색상) 22개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('COLOR', 'navy', '네이비', NULL, 10, '{"hex": "#22305A", "neutral": true, "light": 0.15, "pairs": ["beige", "burgundy", "camel", "gray", "green", "lavender", "lightblue", "mint", "orange", "pink", "red", "white", "yellow"]}'),
    ('COLOR', 'black', '블랙', NULL, 20, '{"hex": "#1C1C1E", "neutral": true, "light": 0.05, "pairs": ["camel", "denim", "gray", "purple", "red", "white"]}'),
    ('COLOR', 'charcoal', '차콜', NULL, 30, '{"hex": "#3A3B3F", "neutral": true, "light": 0.2, "pairs": ["lightblue", "white"]}'),
    ('COLOR', 'gray', '그레이', NULL, 40, '{"hex": "#9A9CA1", "neutral": true, "light": 0.55, "pairs": ["black", "blue", "burgundy", "denim", "lavender", "mint", "navy", "pink", "purple", "white", "yellow"]}'),
    ('COLOR', 'white', '화이트', NULL, 50, '{"hex": "#F7F7F5", "neutral": true, "light": 1, "pairs": ["beige", "black", "blue", "charcoal", "denim", "gray", "green", "khaki", "lavender", "mint", "navy", "orange", "pink", "purple", "red", "yellow"]}'),
    ('COLOR', 'ivory', '아이보리', NULL, 60, '{"hex": "#EFE8D8", "neutral": true, "light": 0.92, "pairs": ["beige", "brown", "khaki"]}'),
    ('COLOR', 'beige', '베이지', NULL, 70, '{"hex": "#CDB591", "neutral": true, "light": 0.72, "pairs": ["blue", "brown", "denim", "green", "ivory", "lightblue", "mint", "navy", "white"]}'),
    ('COLOR', 'denim', '데님', NULL, 80, '{"hex": "#4A6A8C", "neutral": true, "light": 0.35, "pairs": ["beige", "black", "gray", "lavender", "orange", "pink", "red", "white", "yellow"]}'),
    ('COLOR', 'camel', '카멜', NULL, 90, '{"hex": "#B08453", "neutral": false, "light": 0.5, "pairs": ["black", "blue", "green", "navy"]}'),
    ('COLOR', 'brown', '브라운', NULL, 100, '{"hex": "#6E4A33", "neutral": false, "light": 0.3, "pairs": ["beige", "ivory"]}'),
    ('COLOR', 'khaki', '카키', NULL, 110, '{"hex": "#6E6B4E", "neutral": false, "light": 0.38, "pairs": ["ivory", "white"]}'),
    ('COLOR', 'lightblue', '라이트블루', NULL, 120, '{"hex": "#A9C3E3", "neutral": false, "light": 0.8, "pairs": ["beige", "charcoal", "navy"]}'),
    ('COLOR', 'blue', '블루', NULL, 130, '{"hex": "#2F5DA8", "neutral": false, "light": 0.35, "pairs": ["beige", "camel", "gray", "white"]}'),
    ('COLOR', 'burgundy', '버건디', NULL, 140, '{"hex": "#6B2333", "neutral": false, "light": 0.2, "pairs": ["gray", "navy"]}'),
    ('COLOR', 'red', '레드', NULL, 150, '{"hex": "#B8322F", "neutral": false, "light": 0.3, "pairs": ["black", "denim", "navy", "white"]}'),
    ('COLOR', 'pink', '핑크', NULL, 160, '{"hex": "#E9A6B8", "neutral": false, "light": 0.78, "pairs": ["denim", "gray", "navy", "white"]}'),
    ('COLOR', 'orange', '오렌지', NULL, 170, '{"hex": "#E07B39", "neutral": false, "light": 0.55, "pairs": ["denim", "navy", "white"]}'),
    ('COLOR', 'yellow', '옐로', NULL, 180, '{"hex": "#E8C547", "neutral": false, "light": 0.8, "pairs": ["denim", "gray", "navy", "white"]}'),
    ('COLOR', 'green', '그린', NULL, 190, '{"hex": "#2E6B47", "neutral": false, "light": 0.3, "pairs": ["beige", "camel", "navy", "white"]}'),
    ('COLOR', 'mint', '민트', NULL, 200, '{"hex": "#A7D7C5", "neutral": false, "light": 0.8, "pairs": ["beige", "gray", "navy", "white"]}'),
    ('COLOR', 'purple', '퍼플', NULL, 210, '{"hex": "#5E3F86", "neutral": false, "light": 0.25, "pairs": ["black", "gray", "white"]}'),
    ('COLOR', 'lavender', '라벤더', NULL, 220, '{"hex": "#C4B3E0", "neutral": false, "light": 0.8, "pairs": ["denim", "gray", "navy", "white"]}');

-- PATTERN (패턴) 7개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('PATTERN', 'SOLID', '무지', NULL, 10, '{}'),
    ('PATTERN', 'STRIPE', '스트라이프', NULL, 20, '{}'),
    ('PATTERN', 'CHECK', '체크', NULL, 30, '{}'),
    ('PATTERN', 'DOT', '도트', NULL, 40, '{}'),
    ('PATTERN', 'FLORAL', '플로럴', NULL, 50, '{}'),
    ('PATTERN', 'PRINT', '프린트·그래픽', NULL, 60, '{}'),
    ('PATTERN', 'ETC', '기타', NULL, 70, '{}');

-- SEASON (계절) 4개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('SEASON', 'SPRING', '봄', NULL, 10, '{}'),
    ('SEASON', 'SUMMER', '여름', NULL, 20, '{}'),
    ('SEASON', 'FALL', '가을', NULL, 30, '{}'),
    ('SEASON', 'WINTER', '겨울', NULL, 40, '{}');

-- STYLE (스타일) 7개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('STYLE', 'NEAT', '단정', NULL, 10, '{}'),
    ('STYLE', 'MINIMAL', '미니멀', NULL, 20, '{}'),
    ('STYLE', 'CASUAL', '캐주얼', NULL, 30, '{}'),
    ('STYLE', 'CLASSIC', '클래식', NULL, 40, '{}'),
    ('STYLE', 'STREET', '스트릿', NULL, 50, '{}'),
    ('STYLE', 'FEMININE', '페미닌', NULL, 60, '{}'),
    ('STYLE', 'SPORTY', '스포티', NULL, 70, '{}');

-- ITEM_STATUS (보유 상태) 3개
INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('ITEM_STATUS', 'OWNED', '보유 중', NULL, 10, '{"recommendable": true}'),
    ('ITEM_STATUS', 'LAUNDRY', '세탁 중', NULL, 20, '{"recommendable": false}'),
    ('ITEM_STATUS', 'REPAIR', '수선 중', NULL, 30, '{"recommendable": false}');

-- 검증은 서버가 코드 테이블로 한다. 값 목록을 박은 CHECK 제약을 지운다
ALTER TABLE tb_wardrobe_item
    DROP CONSTRAINT ck_wardrobe_item_category,
    DROP CONSTRAINT ck_wardrobe_item_sub_type,
    DROP CONSTRAINT ck_wardrobe_item_color,
    DROP CONSTRAINT ck_wardrobe_item_pattern,
    DROP CONSTRAINT ck_wardrobe_item_seasons,
    DROP CONSTRAINT ck_wardrobe_item_styles,
    DROP CONSTRAINT ck_wardrobe_item_status;
ALTER TABLE tb_user_profile
    DROP CONSTRAINT ck_user_profile_colors,
    DROP CONSTRAINT ck_user_profile_moods;

-- 관리자가 추가하는 코드는 30자까지라서 코드 컬럼을 넓힌다
ALTER TABLE tb_wardrobe_item
    ALTER COLUMN category TYPE VARCHAR(30),
    ALTER COLUMN sub_type TYPE VARCHAR(30),
    ALTER COLUMN color TYPE VARCHAR(30),
    ALTER COLUMN pattern TYPE VARCHAR(30),
    ALTER COLUMN status TYPE VARCHAR(30);

-- 코디 칸에 원피스(DRESS)를 추가한다
ALTER TABLE tb_outfit_item DROP CONSTRAINT ck_outfit_item_role;
ALTER TABLE tb_outfit_item ADD CONSTRAINT ck_outfit_item_role
    CHECK (item_role IN ('TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES', 'BAG'));
ALTER TABLE tb_daily_look_item DROP CONSTRAINT ck_daily_look_item_role;
ALTER TABLE tb_daily_look_item ADD CONSTRAINT ck_daily_look_item_role
    CHECK (item_role IN ('TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES', 'BAG'));
