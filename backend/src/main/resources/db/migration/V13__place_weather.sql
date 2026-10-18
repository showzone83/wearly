-- 장소별 날씨 (계약: doc/19_장소별_날씨.md). 17 문서의 주기 수집(REGION)은 그대로 두고,
--  1) REGION 밖 장소(좌표)는 요청 시점에 공급자를 한 번 불러 격자별로 캐시하고 (tb_weather_place_snapshot)
--  2) 예보 밖 날짜는 Open-Meteo 과거 기상 자료로 계산한 월별 평년값을 쓴다 (tb_weather_climate)

-- ---------------------------------------------------------------------------
-- 호출 이력: 실행(run) 밖의 호출도 남긴다
--   FORECAST  주기·수동·시작·시험 실행의 예보 호출 (run_id 있음, region_code = REGION 코드)
--   ON_DEMAND 좌표 요청 때 부른 예보 (run_id NULL, region_code = 격자 키 "37.6,127.0")
--   SEARCH    장소 검색 (run_id NULL, region_code NULL)
--   CLIMATE   평년값 계산용 과거 기상 자료 (run_id NULL, region_code = 격자 키, provider_code OPEN_METEO)
-- ---------------------------------------------------------------------------
ALTER TABLE tb_weather_call_log ALTER COLUMN run_id DROP NOT NULL;
ALTER TABLE tb_weather_call_log ALTER COLUMN region_code DROP NOT NULL;
ALTER TABLE tb_weather_call_log
    ADD CONSTRAINT ck_weather_call_kind CHECK (call_kind IN ('FORECAST', 'ON_DEMAND', 'SEARCH', 'CLIMATE')),
    ADD CONSTRAINT ck_weather_call_run CHECK (call_kind <> 'FORECAST' OR run_id IS NOT NULL);
-- 실행 밖 호출은 실행과 함께 지워지지 않으므로 보관 기간이 지나면 따로 지운다
CREATE INDEX ix_weather_call_no_run ON tb_weather_call_log (called_at) WHERE run_id IS NULL;

-- ---------------------------------------------------------------------------
-- REGION 밖 장소의 최신 예보. 격자 키 = 위도·경도를 소수 첫째 자리로 반올림 ("37.6,127.0", 약 11km)
-- 받기에 실패하면 이전 값은 두고 last_error만 바꾼다 (tb_weather_snapshot과 같은 규칙)
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_place_snapshot (
    grid_key      VARCHAR(30)  PRIMARY KEY,
    lat           DOUBLE PRECISION NOT NULL,       -- 격자 위도 (소수 첫째 자리로 반올림한 값)
    lon           DOUBLE PRECISION NOT NULL,       -- 격자 경도
    label         VARCHAR(100),                    -- 공급자가 알려 준 장소 이름
    provider_code VARCHAR(20),
    current       JSONB,                           -- {temp, feelsLike, cond, condText, observedAt}
    days          JSONB,                           -- [{date, temp, min, max, rainProb, cond, condText}]
    fetched_at    TIMESTAMPTZ,
    last_error    VARCHAR(300),
    last_error_at TIMESTAMPTZ,
    CONSTRAINT ck_weather_place_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_weather_place_lon CHECK (lon BETWEEN -180 AND 180)
);

-- ---------------------------------------------------------------------------
-- 격자별 월 평년값. 처음 필요할 때 지난 N개 완결 연도(기본 10년)의 일별 자료로 12개월을 한 번에 계산해 저장하고,
-- computed_at이 400일을 넘으면 다시 계산한다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_climate (
    grid_key       VARCHAR(30)  NOT NULL,
    month          SMALLINT     NOT NULL,
    temp_avg       DOUBLE PRECISION NOT NULL,      -- (최고+최저)/2의 평균 (섭씨, 소수 둘째 자리로 저장)
    temp_min       DOUBLE PRECISION NOT NULL,      -- 일 최저 기온의 평균
    temp_max       DOUBLE PRECISION NOT NULL,      -- 일 최고 기온의 평균
    rain_day_ratio DOUBLE PRECISION NOT NULL,      -- 강수 1mm 이상인 날의 비율 0~1 (소수 셋째 자리로 저장)
    years          VARCHAR(20)  NOT NULL,          -- "2016-2025"
    source         VARCHAR(20)  NOT NULL,          -- OPEN_METEO | MOCK
    computed_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (grid_key, month),
    CONSTRAINT ck_weather_climate_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_weather_climate_ratio CHECK (rain_day_ratio BETWEEN 0 AND 1)
);
