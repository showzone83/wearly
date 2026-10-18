-- 예정 코디 서버 저장 (doc/19 "다음 단계"). V1의 tb_planned_outfit은 쓰는 코드가 없어 비어 있고 장소·날씨 출처·코디 칸이 없다.
-- 이 용도로 다시 만든다 (V12의 tb_item_image와 같은 방식). 이미 적용된 V1~V13은 바꾸지 않는다.
--  - client_id: 앱이 기기에서 만든 예정 ID (같은 회원 안에서 유일, bulk 올리기의 중복 방지)
--  - concepts: STYLE 코드 (코드 테이블로 검증하므로 V1의 고정 CHECK는 두지 않는다)
--  - place_*: 예정 장소 (없으면 내 지역). regionCode는 REGION 코드, 그 밖은 이름·좌표만
--  - weather: 앱 Weather 모양 + 출처·확인 시각 { temp, min, max, rainProb, cond, manual, source, checkedAt }
--  - outfit: 앱 Outfit 모양 그대로 (items: 칸 → 아이템 ID). 아이템이 지워져도 그대로 두고 응답에서 표시만 한다
DROP TABLE IF EXISTS tb_planned_outfit;

CREATE TABLE tb_planned_outfit (
    planned_outfit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT           NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    client_id         VARCHAR(64),
    planned_date      DATE             NOT NULL,
    time_of_day       VARCHAR(10)      NOT NULL,
    title             VARCHAR(50)      NOT NULL,
    occasion          VARCHAR(20)      NOT NULL,
    concepts          TEXT[]           NOT NULL DEFAULT '{}',
    memo              VARCHAR(500),
    place_region_code VARCHAR(30),
    place_label       VARCHAR(50),
    place_lat         DOUBLE PRECISION,
    place_lon         DOUBLE PRECISION,
    weather           JSONB            NOT NULL,
    forecast_ready    BOOLEAN          NOT NULL DEFAULT FALSE,
    outfit            JSONB,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_planned_outfit_time CHECK (time_of_day IN ('AM', 'PM', 'EVENING')),
    CONSTRAINT ck_planned_outfit_occasion CHECK (occasion IN ('COMMUTE', 'MEETING', 'FIELD', 'DINNER', 'DAILY',
        'INTERVIEW', 'WEDDING', 'DATE', 'TRAVEL', 'GATHERING')),
    -- 장소는 네 칸이 함께: 없음(전부 NULL) 또는 이름·좌표가 모두 있음 (regionCode는 선택)
    CONSTRAINT ck_planned_outfit_place CHECK (
        (place_label IS NULL AND place_lat IS NULL AND place_lon IS NULL AND place_region_code IS NULL)
        OR (place_label IS NOT NULL AND place_lat BETWEEN -90 AND 90 AND place_lon BETWEEN -180 AND 180)),
    CONSTRAINT ck_planned_outfit_weather CHECK (jsonb_typeof(weather) = 'object'),
    CONSTRAINT ck_planned_outfit_outfit CHECK (outfit IS NULL OR jsonb_typeof(outfit) = 'object')
);

CREATE INDEX ix_planned_outfit_user_date ON tb_planned_outfit (user_id, planned_date);
CREATE UNIQUE INDEX ux_planned_outfit_client ON tb_planned_outfit (user_id, client_id) WHERE client_id IS NOT NULL;
