-- 날씨 정보: 서버가 주기적으로 받아 저장하고 앱에는 저장된 값만 준다 (계약: doc/17_날씨.md)
-- 지금은 공급자 WeatherAPI.com 하나만 쓴다. 공급자 표·우선순위·호출 이력의 fallback 칸은 예비 공급자(OpenWeatherMap)를
-- 나중에 붙일 때 그대로 쓴다.

-- ---------------------------------------------------------------------------
-- 지역: 코드 그룹 REGION. attrs = {"lat": 위도, "lon": 경도} (시·도 17개, 도청·시청 소재지 좌표)
-- ---------------------------------------------------------------------------
INSERT INTO tb_code_group (group_code, name, description, sort_order) VALUES
    ('REGION', '날씨 지역', '날씨를 받아 두는 지역. attrs.lat(위도 -90~90), lon(경도 -180~180). 활성 지역만 주기적으로 받는다', 90);

INSERT INTO tb_code (group_code, code, label, parent_code, sort_order, attrs) VALUES
    ('REGION', 'SEOUL', '서울', NULL, 10, '{"lat":37.5665,"lon":126.978}'::jsonb),
    ('REGION', 'BUSAN', '부산', NULL, 20, '{"lat":35.1796,"lon":129.0756}'::jsonb),
    ('REGION', 'DAEGU', '대구', NULL, 30, '{"lat":35.8714,"lon":128.6014}'::jsonb),
    ('REGION', 'INCHEON', '인천', NULL, 40, '{"lat":37.4563,"lon":126.7052}'::jsonb),
    ('REGION', 'GWANGJU', '광주', NULL, 50, '{"lat":35.1595,"lon":126.8526}'::jsonb),
    ('REGION', 'DAEJEON', '대전', NULL, 60, '{"lat":36.3504,"lon":127.3845}'::jsonb),
    ('REGION', 'ULSAN', '울산', NULL, 70, '{"lat":35.5384,"lon":129.3114}'::jsonb),
    ('REGION', 'SEJONG', '세종', NULL, 80, '{"lat":36.48,"lon":127.289}'::jsonb),
    ('REGION', 'GYEONGGI', '경기', NULL, 90, '{"lat":37.2636,"lon":127.0286}'::jsonb),
    ('REGION', 'GANGWON', '강원', NULL, 100, '{"lat":37.8813,"lon":127.7298}'::jsonb),
    ('REGION', 'CHUNGBUK', '충북', NULL, 110, '{"lat":36.6424,"lon":127.489}'::jsonb),
    ('REGION', 'CHUNGNAM', '충남', NULL, 120, '{"lat":36.6588,"lon":126.6728}'::jsonb),
    ('REGION', 'JEONBUK', '전북', NULL, 130, '{"lat":35.8242,"lon":127.148}'::jsonb),
    ('REGION', 'JEONNAM', '전남', NULL, 140, '{"lat":34.8161,"lon":126.4629}'::jsonb),
    ('REGION', 'GYEONGBUK', '경북', NULL, 150, '{"lat":36.576,"lon":128.5056}'::jsonb),
    ('REGION', 'GYEONGNAM', '경남', NULL, 160, '{"lat":35.228,"lon":128.6811}'::jsonb),
    ('REGION', 'JEJU', '제주', NULL, 170, '{"lat":33.4996,"lon":126.5312}'::jsonb);

-- ---------------------------------------------------------------------------
-- 스케줄 설정: 한 행만 둔다 (id = 1). 관리자가 바꾸면 서버를 다시 띄우지 않아도 다음 실행부터 반영된다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_setting (
    id                SMALLINT    PRIMARY KEY DEFAULT 1,
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    interval_minutes  INT         NOT NULL DEFAULT 30,
    forecast_days     INT         NOT NULL DEFAULT 3,
    failure_threshold INT         NOT NULL DEFAULT 3,   -- 연속 실패가 이만큼이면 공급자를 차단한다
    cooldown_minutes  INT         NOT NULL DEFAULT 15,  -- 차단 시간
    updated_by        BIGINT      REFERENCES tb_user (user_id) ON DELETE SET NULL,
    updated_at        TIMESTAMPTZ,
    CONSTRAINT ck_weather_setting_single CHECK (id = 1),
    CONSTRAINT ck_weather_setting_interval CHECK (interval_minutes BETWEEN 10 AND 1440),
    CONSTRAINT ck_weather_setting_days CHECK (forecast_days BETWEEN 1 AND 14),
    CONSTRAINT ck_weather_setting_threshold CHECK (failure_threshold BETWEEN 1 AND 20),
    CONSTRAINT ck_weather_setting_cooldown CHECK (cooldown_minutes BETWEEN 1 AND 1440)
);
INSERT INTO tb_weather_setting (id) VALUES (1);

-- ---------------------------------------------------------------------------
-- 공급자: 키 값은 저장하지 않고 서버 설정의 키 이름만 둔다 (wearly.weather.keys.<key_name>)
-- 상태 칸(consecutive_failures ~ last_error)은 실행이 바꾸고, 설정 칸(enabled ~ max_forecast_days)은 관리자가 바꾼다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_provider (
    provider_code        VARCHAR(20)  PRIMARY KEY,         -- WEATHERAPI (예비 공급자는 나중에 행으로 더한다)
    display_name         VARCHAR(50)  NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    priority             INT          NOT NULL DEFAULT 100, -- 작을수록 먼저 쓴다
    key_name             VARCHAR(100) NOT NULL,
    monthly_quota        INT,                              -- NULL이면 한도 없음
    max_forecast_days    INT          NOT NULL,
    consecutive_failures INT          NOT NULL DEFAULT 0,
    circuit_reason       VARCHAR(20),                      -- NULL(정상) | FAILURES(연속 실패) | AUTH(키 문제)
    circuit_open_until   TIMESTAMPTZ,                      -- FAILURES일 때 차단이 풀리는 시각
    last_success_at      TIMESTAMPTZ,
    last_failure_at      TIMESTAMPTZ,
    last_error           VARCHAR(300),
    updated_by           BIGINT       REFERENCES tb_user (user_id) ON DELETE SET NULL,
    updated_at           TIMESTAMPTZ,
    CONSTRAINT ck_weather_provider_code CHECK (provider_code IN ('WEATHERAPI')),
    CONSTRAINT ck_weather_provider_priority CHECK (priority BETWEEN 0 AND 10000),
    CONSTRAINT ck_weather_provider_quota CHECK (monthly_quota IS NULL OR monthly_quota >= 0),
    CONSTRAINT ck_weather_provider_days CHECK (max_forecast_days BETWEEN 1 AND 14),
    CONSTRAINT ck_weather_provider_reason CHECK (circuit_reason IS NULL OR circuit_reason IN ('FAILURES', 'AUTH'))
);
-- WeatherAPI.com 무료 플랜은 3일 예보다. 유료 플랜이면 관리자 화면에서 늘린다. 월 한도는 가입한 플랜을 보고 넣는다
INSERT INTO tb_weather_provider (provider_code, display_name, enabled, priority, key_name, monthly_quota, max_forecast_days) VALUES
    ('WEATHERAPI', 'WeatherAPI.com', TRUE, 1, 'WEATHERAPI_KEY', NULL, 3);

-- ---------------------------------------------------------------------------
-- 실행 이력: 스케줄·수동·시작·시험 실행 한 번이 한 행
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_refresh_run (
    run_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trigger_type   VARCHAR(20) NOT NULL,
    requested_by   BIGINT      REFERENCES tb_user (user_id) ON DELETE SET NULL,
    region_code    VARCHAR(30),                 -- 한 지역만 실행했으면 그 지역
    status         VARCHAR(20) NOT NULL,
    skip_reason    VARCHAR(20),
    success_count  INT         NOT NULL DEFAULT 0,
    fail_count     INT         NOT NULL DEFAULT 0,
    fallback_count INT         NOT NULL DEFAULT 0, -- 1순위가 아닌 공급자로 받은 지역 수
    started_at     TIMESTAMPTZ NOT NULL,
    finished_at    TIMESTAMPTZ,
    CONSTRAINT ck_weather_run_trigger CHECK (trigger_type IN ('SCHEDULE', 'MANUAL', 'STARTUP', 'TEST')),
    CONSTRAINT ck_weather_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_weather_run_skip CHECK (skip_reason IS NULL OR skip_reason IN ('DISABLED', 'NO_PROVIDER', 'LOCKED'))
);
CREATE INDEX ix_weather_run_started ON tb_weather_refresh_run (started_at DESC, run_id DESC);

-- ---------------------------------------------------------------------------
-- 호출 이력: 공급자 HTTP 호출 한 번이 한 행. 오류 문구에는 키가 들어가지 않는다 (서버가 지운다)
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_call_log (
    call_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id         BIGINT       NOT NULL REFERENCES tb_weather_refresh_run (run_id) ON DELETE CASCADE,
    region_code    VARCHAR(30)  NOT NULL,
    provider_code  VARCHAR(20)  NOT NULL,
    call_kind      VARCHAR(20)  NOT NULL,       -- FORECAST(현재 + 일별 예보 한 번에)
    fallback       BOOLEAN      NOT NULL DEFAULT FALSE,
    http_status    INT,
    success        BOOLEAN      NOT NULL,
    error_code     VARCHAR(20),                 -- TIMEOUT · HTTP_4XX · HTTP_429 · HTTP_5XX · AUTH · QUOTA · PARSE · CLIENT
    error_message  VARCHAR(300),
    duration_ms    INT          NOT NULL,
    response_bytes INT,
    called_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_weather_call_called ON tb_weather_call_log (called_at DESC, call_id DESC);
CREATE INDEX ix_weather_call_region ON tb_weather_call_log (region_code, called_at DESC);
CREATE INDEX ix_weather_call_provider ON tb_weather_call_log (provider_code, called_at DESC);
CREATE INDEX ix_weather_call_run ON tb_weather_call_log (run_id);

-- ---------------------------------------------------------------------------
-- 지역별 최신 날씨. 받기에 실패하면 이전 값은 두고 last_error만 바꾼다
-- ---------------------------------------------------------------------------
CREATE TABLE tb_weather_snapshot (
    region_code   VARCHAR(30)  PRIMARY KEY,
    provider_code VARCHAR(20),                  -- 이 값을 준 공급자
    current       JSONB,                        -- {temp, feelsLike, cond, condText, observedAt}
    days          JSONB,                        -- [{date, temp, min, max, rainProb, cond, condText}]
    raw           JSONB,                        -- 공급자 원본 응답 (시간별 예보는 빼고 저장)
    location_name VARCHAR(100),
    fetched_at    TIMESTAMPTZ,
    last_error    VARCHAR(300),
    last_error_at TIMESTAMPTZ
);
