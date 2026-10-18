-- 지역·날짜별 날씨 이력 (doc/17_날씨.md 2장). AI 코디 검증처럼 지난 날짜의 날씨가 필요할 때 쓴다.
-- 받기에 성공할 때마다 그날(한국 날짜) 행을 고친다: 예보는 마지막 값으로 덮어쓰고, 현재 날씨는 실측 요약에 더한다.
-- 날짜가 지나면 행이 더 바뀌지 않아 그대로 지난 날씨가 된다. 기간이 지나도 지우지 않는다 (log-retention-days 대상 아님).
CREATE TABLE tb_weather_daily_history (
    region_code         VARCHAR(30)      NOT NULL,
    weather_date        DATE             NOT NULL,          -- 한국 날짜
    provider_code       VARCHAR(20),                        -- 마지막 예보를 준 공급자
    -- 예보 (그날 마지막으로 받은 일별 예보)
    forecast_temp       DOUBLE PRECISION,
    forecast_min        DOUBLE PRECISION,
    forecast_max        DOUBLE PRECISION,
    rain_prob           INT,                                -- 0~100
    cond                VARCHAR(10),                        -- SUNNY · CLOUDY · RAIN
    cond_text           VARCHAR(100),
    forecast_fetched_at TIMESTAMPTZ,
    -- 실측 요약 (주기마다 받은 현재 날씨를 모은 값. 평균 = observed_temp_sum / observed_count)
    observed_min        DOUBLE PRECISION,
    observed_max        DOUBLE PRECISION,
    observed_temp_sum   DOUBLE PRECISION NOT NULL DEFAULT 0,
    observed_count      INT              NOT NULL DEFAULT 0,
    observed_rain_count INT              NOT NULL DEFAULT 0, -- 현재 상태가 RAIN이었던 관측 수
    first_observed_at   TIMESTAMPTZ,
    last_observed_at    TIMESTAMPTZ,                        -- 이보다 늦은 관측만 더한다
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (region_code, weather_date),
    CONSTRAINT ck_weather_history_cond CHECK (cond IS NULL OR cond IN ('SUNNY', 'CLOUDY', 'RAIN'))
);
CREATE INDEX ix_weather_history_date ON tb_weather_daily_history (weather_date DESC, region_code);

COMMENT ON TABLE tb_weather_daily_history IS '지역·날짜별 날씨 이력. 받기에 성공할 때마다 그날(한국 날짜) 행을 고치고, 날짜가 지나면 그대로 지난 날씨가 된다. 이력 정리 대상 아님 (doc/17 2장)';
COMMENT ON COLUMN tb_weather_daily_history.region_code IS '지역 (코드 그룹 REGION)';
COMMENT ON COLUMN tb_weather_daily_history.weather_date IS '날짜 (한국 날짜)';
COMMENT ON COLUMN tb_weather_daily_history.provider_code IS '마지막 예보를 준 공급자 (tb_weather_provider.provider_code)';
COMMENT ON COLUMN tb_weather_daily_history.forecast_temp IS '예보 평균 기온(섭씨). 그날 마지막으로 받은 일별 예보';
COMMENT ON COLUMN tb_weather_daily_history.forecast_min IS '예보 최저 기온(섭씨)';
COMMENT ON COLUMN tb_weather_daily_history.forecast_max IS '예보 최고 기온(섭씨)';
COMMENT ON COLUMN tb_weather_daily_history.rain_prob IS '예보 강수(비·눈) 확률 0~100';
COMMENT ON COLUMN tb_weather_daily_history.cond IS '예보 날씨 상태: SUNNY · CLOUDY · RAIN';
COMMENT ON COLUMN tb_weather_daily_history.cond_text IS '공급자의 날씨 설명 문구 (한국어)';
COMMENT ON COLUMN tb_weather_daily_history.forecast_fetched_at IS '예보를 받은 시각';
COMMENT ON COLUMN tb_weather_daily_history.observed_min IS '그날 관측한 현재 기온 중 최저(섭씨)';
COMMENT ON COLUMN tb_weather_daily_history.observed_max IS '그날 관측한 현재 기온 중 최고(섭씨)';
COMMENT ON COLUMN tb_weather_daily_history.observed_temp_sum IS '관측 기온 합. 평균 = observed_temp_sum / observed_count';
COMMENT ON COLUMN tb_weather_daily_history.observed_count IS '더한 관측 수';
COMMENT ON COLUMN tb_weather_daily_history.observed_rain_count IS '현재 상태가 RAIN이었던 관측 수';
COMMENT ON COLUMN tb_weather_daily_history.first_observed_at IS '그날 첫 관측 시각 (공급자 갱신 시각)';
COMMENT ON COLUMN tb_weather_daily_history.last_observed_at IS '그날 마지막 관측 시각. 이보다 늦은 관측만 더한다 (같은 관측을 두 번 세지 않게)';
COMMENT ON COLUMN tb_weather_daily_history.updated_at IS '행을 마지막으로 고친 시각';
