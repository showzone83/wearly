-- Wearly 초기 스키마 (계약: doc/05_백엔드_API.md 3장)
-- 이름은 소문자 snake_case + tb_ 접두사, PK는 identity, 시각은 TIMESTAMPTZ.
-- 열거형 값은 앱 mobile/src/data/types.ts 문자열을 그대로 VARCHAR에 저장하고 CHECK로 막는다.

-- pgvector: 이미지 임베딩(유사 옷 찾기)용. 확장이 설치되지 않은 DB에서도 마이그레이션이 통과하도록 조건부로 만든다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- 회원
-- ---------------------------------------------------------------------------
CREATE TABLE tb_user (
    user_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      VARCHAR(255),                       -- 참고용. 소셜 계정은 이메일이 없을 수 있다
    name       VARCHAR(20)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- 이메일·비밀번호 자격 증명 (임시 수단, 소셜 로그인 도입 후 제거 가능)
CREATE TABLE tb_user_local_credential (
    credential_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_local_credential_user UNIQUE (user_id),
    CONSTRAINT uq_user_local_credential_email UNIQUE (email),
    CONSTRAINT ck_user_local_credential_email_lower CHECK (email = lower(email))
);

-- 소셜 계정 (D5 설계). 1차 API에서는 쓰지 않는다
CREATE TABLE tb_user_social (
    social_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    connected_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_user_social_provider CHECK (provider IN ('KAKAO', 'GOOGLE', 'APPLE', 'NAVER'))
);
CREATE INDEX ix_user_social_user ON tb_user_social (user_id);

-- Refresh 토큰. 원문은 저장하지 않고 SHA-256 hex만 둔다
CREATE TABLE tb_refresh_token (
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    token_hash       CHAR(64)    NOT NULL,
    family_id        UUID        NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by_id   BIGINT      REFERENCES tb_refresh_token (refresh_token_id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_family ON tb_refresh_token (family_id);
CREATE INDEX ix_refresh_token_user ON tb_refresh_token (user_id);

-- 취향·근무 패턴 프로필 (앱 Profile + onboarded). 이름은 tb_user.name을 쓴다
CREATE TABLE tb_user_profile (
    profile_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    dress_codes TEXT[]      NOT NULL DEFAULT '{}',
    moods       TEXT[]      NOT NULL DEFAULT '{}',
    colors      TEXT[]      NOT NULL DEFAULT '{}',
    work_type   VARCHAR(10) NOT NULL,
    work_days   SMALLINT[]  NOT NULL DEFAULT '{}',
    work_rule   BOOLEAN     NOT NULL,
    off_rule    BOOLEAN     NOT NULL,
    notify      JSONB       NOT NULL,
    onboarded   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_profile_user UNIQUE (user_id),
    CONSTRAINT ck_user_profile_dress_codes CHECK (dress_codes <@ ARRAY ['CASUAL', 'BIZ_CASUAL', 'FORMAL']::TEXT[]),
    CONSTRAINT ck_user_profile_moods CHECK (moods <@ ARRAY ['NEAT', 'MINIMAL', 'CASUAL', 'CLASSIC', 'STREET']::TEXT[]),
    CONSTRAINT ck_user_profile_colors CHECK (colors <@ ARRAY ['navy', 'black', 'charcoal', 'gray', 'white', 'ivory', 'beige',
        'camel', 'brown', 'khaki', 'lightblue', 'burgundy']::TEXT[]),
    CONSTRAINT ck_user_profile_work_type CHECK (work_type IN ('WEEKDAY', 'WEEKEND', 'CUSTOM')),
    CONSTRAINT ck_user_profile_work_days CHECK (work_days <@ ARRAY [0, 1, 2, 3, 4, 5, 6]::SMALLINT[])
);

-- 취향 학습 점수 (테이블만)
CREATE TABLE tb_user_style (
    user_style_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    style_code       VARCHAR(20)   NOT NULL,
    preference_score NUMERIC(7, 3) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_style UNIQUE (user_id, style_code),
    CONSTRAINT ck_user_style_code CHECK (style_code IN ('NEAT', 'MINIMAL', 'CASUAL', 'CLASSIC', 'STREET'))
);

-- ---------------------------------------------------------------------------
-- 옷장
-- ---------------------------------------------------------------------------
CREATE TABLE tb_wardrobe_item (
    item_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    name          VARCHAR(40) NOT NULL,
    category      VARCHAR(10) NOT NULL,
    sub_type      VARCHAR(20) NOT NULL,
    color         VARCHAR(20) NOT NULL,
    pattern       VARCHAR(10) NOT NULL,
    seasons       TEXT[]      NOT NULL DEFAULT '{}',
    styles        TEXT[]      NOT NULL DEFAULT '{}',
    formality     SMALLINT    NOT NULL,
    status        VARCHAR(10) NOT NULL DEFAULT 'OWNED',
    brand         VARCHAR(40),
    worn_count    INTEGER     NOT NULL DEFAULT 0,
    last_worn_at  DATE,
    ai_suggestion JSONB,                            -- 등록 당시 AI 제안값 (1차 API에서는 비워 둔다)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_wardrobe_item_category CHECK (category IN ('TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG', 'ACC')),
    -- 카테고리와 세부 종류의 짝 (앱 constants.ts SUBTYPE)
    CONSTRAINT ck_wardrobe_item_sub_type CHECK (
        (category = 'TOP' AND sub_type IN ('TEE', 'KNIT', 'SHIRT'))
        OR (category = 'BOTTOM' AND sub_type IN ('SLACKS', 'WIDE', 'CHINO', 'SKIRT'))
        OR (category = 'OUTER' AND sub_type IN ('CARDIGAN', 'JACKET', 'COAT'))
        OR (category = 'SHOES' AND sub_type IN ('LOAFER', 'SNEAKER'))
        OR (category = 'BAG' AND sub_type IN ('TOTE', 'SHOULDER'))
        OR (category = 'ACC' AND sub_type IN ('WATCH'))
    ),
    CONSTRAINT ck_wardrobe_item_color CHECK (color IN ('navy', 'black', 'charcoal', 'gray', 'white', 'ivory', 'beige',
        'camel', 'brown', 'khaki', 'lightblue', 'burgundy')),
    CONSTRAINT ck_wardrobe_item_pattern CHECK (pattern IN ('SOLID', 'STRIPE', 'CHECK', 'ETC')),
    CONSTRAINT ck_wardrobe_item_seasons CHECK (seasons <@ ARRAY ['SPRING', 'SUMMER', 'FALL', 'WINTER']::TEXT[]),
    CONSTRAINT ck_wardrobe_item_styles CHECK (styles <@ ARRAY ['NEAT', 'MINIMAL', 'CASUAL', 'CLASSIC', 'STREET']::TEXT[]),
    CONSTRAINT ck_wardrobe_item_formality CHECK (formality BETWEEN 1 AND 3),
    CONSTRAINT ck_wardrobe_item_status CHECK (status IN ('OWNED', 'LAUNDRY', 'REPAIR')),
    CONSTRAINT ck_wardrobe_item_worn_count CHECK (worn_count >= 0)
);
CREATE INDEX ix_wardrobe_item_user_created ON tb_wardrobe_item (user_id, created_at DESC, item_id DESC);
CREATE INDEX ix_wardrobe_item_user_category ON tb_wardrobe_item (user_id, category);
-- 추천 후보(보유 중인 옷)만 빠르게 찾는 부분 인덱스
CREATE INDEX ix_wardrobe_item_owned ON tb_wardrobe_item (user_id, category) WHERE status = 'OWNED';

-- 아이템 이미지 URL (테이블만. 1차에서는 사진을 기기에만 둔다)
CREATE TABLE tb_item_image (
    item_image_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id       BIGINT      NOT NULL REFERENCES tb_wardrobe_item (item_id) ON DELETE CASCADE,
    image_url     TEXT        NOT NULL,
    kind          VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL',
    sort_order    SMALLINT    NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_item_image_kind CHECK (kind IN ('ORIGINAL', 'THUMBNAIL', 'CUTOUT'))
);
CREATE INDEX ix_item_image_item ON tb_item_image (item_id);

-- ---------------------------------------------------------------------------
-- 코디 (테이블만)
-- ---------------------------------------------------------------------------
CREATE TABLE tb_outfit (
    outfit_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    score      SMALLINT    NOT NULL,
    breakdown  JSONB       NOT NULL,                -- ScoreBreakdown
    context    JSONB       NOT NULL,                -- RecommendContext
    reasons    JSONB       NOT NULL DEFAULT '[]',
    saved_at   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_outfit_score CHECK (score BETWEEN 0 AND 100)
);
CREATE INDEX ix_outfit_user_created ON tb_outfit (user_id, created_at DESC);

CREATE TABLE tb_outfit_item (
    outfit_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outfit_id      BIGINT      NOT NULL REFERENCES tb_outfit (outfit_id) ON DELETE CASCADE,
    item_id        BIGINT      NOT NULL REFERENCES tb_wardrobe_item (item_id) ON DELETE CASCADE,
    item_role      VARCHAR(10) NOT NULL,            -- 슬롯
    CONSTRAINT uq_outfit_item_role UNIQUE (outfit_id, item_role),
    CONSTRAINT ck_outfit_item_role CHECK (item_role IN ('TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG'))
);
CREATE INDEX ix_outfit_item_item ON tb_outfit_item (item_id);

CREATE TABLE tb_outfit_feedback (
    feedback_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outfit_id     BIGINT      NOT NULL REFERENCES tb_outfit (outfit_id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    feedback_type VARCHAR(10) NOT NULL,
    reasons       JSONB       NOT NULL DEFAULT '[]',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_outfit_feedback UNIQUE (outfit_id, user_id),
    CONSTRAINT ck_outfit_feedback_type CHECK (feedback_type IN ('LIKE', 'DISLIKE'))
);
CREATE INDEX ix_outfit_feedback_user ON tb_outfit_feedback (user_id);

CREATE TABLE tb_planned_outfit (
    planned_outfit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    planned_date      DATE        NOT NULL,
    time_of_day       VARCHAR(10) NOT NULL,
    title             VARCHAR(50) NOT NULL,
    occasion          VARCHAR(20) NOT NULL,
    concepts          TEXT[]      NOT NULL DEFAULT '{}',
    weather           JSONB,
    memo              TEXT,
    outfit_id         BIGINT      REFERENCES tb_outfit (outfit_id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_planned_outfit_time CHECK (time_of_day IN ('AM', 'PM', 'EVENING')),
    CONSTRAINT ck_planned_outfit_occasion CHECK (occasion IN ('COMMUTE', 'MEETING', 'FIELD', 'DINNER', 'DAILY',
        'INTERVIEW', 'WEDDING', 'DATE', 'TRAVEL', 'GATHERING')),
    CONSTRAINT ck_planned_outfit_concepts CHECK (concepts <@ ARRAY ['NEAT', 'MINIMAL', 'CASUAL', 'CLASSIC', 'STREET']::TEXT[])
);
CREATE INDEX ix_planned_outfit_user_date ON tb_planned_outfit (user_id, planned_date);

-- ---------------------------------------------------------------------------
-- 착용 기록·진단 (테이블만)
-- ---------------------------------------------------------------------------
CREATE TABLE tb_daily_look (
    daily_look_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    worn_date       DATE        NOT NULL,
    occasion        VARCHAR(20) NOT NULL,
    self_score      SMALLINT    NOT NULL,
    emotions        TEXT[]      NOT NULL DEFAULT '{}',
    memo            TEXT,
    others_reaction VARCHAR(10) NOT NULL,
    praised_item_id BIGINT      REFERENCES tb_wardrobe_item (item_id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_look_user_date UNIQUE (user_id, worn_date),
    CONSTRAINT ck_daily_look_occasion CHECK (occasion IN ('COMMUTE', 'MEETING', 'FIELD', 'DINNER', 'DAILY',
        'INTERVIEW', 'WEDDING', 'DATE', 'TRAVEL', 'GATHERING')),
    CONSTRAINT ck_daily_look_self_score CHECK (self_score BETWEEN 1 AND 5),
    CONSTRAINT ck_daily_look_emotions CHECK (emotions <@ ARRAY ['CONFIDENT', 'COMFY', 'AWKWARD', 'UNCOMFY']::TEXT[]),
    CONSTRAINT ck_daily_look_others CHECK (others_reaction IN ('PRAISE', 'NORMAL', 'MEH'))
);

CREATE TABLE tb_daily_look_item (
    daily_look_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    daily_look_id      BIGINT      NOT NULL REFERENCES tb_daily_look (daily_look_id) ON DELETE CASCADE,
    item_id            BIGINT      NOT NULL REFERENCES tb_wardrobe_item (item_id) ON DELETE CASCADE,
    item_role          VARCHAR(10) NOT NULL,
    CONSTRAINT uq_daily_look_item_role UNIQUE (daily_look_id, item_role),
    CONSTRAINT ck_daily_look_item_role CHECK (item_role IN ('TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG'))
);
CREATE INDEX ix_daily_look_item_item ON tb_daily_look_item (item_id);

CREATE TABLE tb_look_diagnosis (
    diagnosis_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    daily_look_id BIGINT      REFERENCES tb_daily_look (daily_look_id) ON DELETE CASCADE,
    ai_score      SMALLINT    NOT NULL,
    axes          JSONB       NOT NULL,             -- AxisScores
    strength      VARCHAR(20) NOT NULL,
    weakness      VARCHAR(20) NOT NULL,
    swap          JSONB,                            -- { slot, fromId, toId }
    tip           TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_look_diagnosis_look UNIQUE (daily_look_id),
    CONSTRAINT ck_look_diagnosis_score CHECK (ai_score BETWEEN 0 AND 100),
    CONSTRAINT ck_look_diagnosis_strength CHECK (strength IN ('color', 'silhouette', 'tpo', 'weather', 'diversity')),
    CONSTRAINT ck_look_diagnosis_weakness CHECK (weakness IN ('color', 'silhouette', 'tpo', 'weather', 'diversity'))
);
CREATE INDEX ix_look_diagnosis_user ON tb_look_diagnosis (user_id, created_at DESC);

CREATE TABLE tb_wardrobe_report (
    report_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    period_start DATE        NOT NULL,
    period_end   DATE        NOT NULL,
    report       JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_wardrobe_report_period CHECK (period_start <= period_end)
);
CREATE INDEX ix_wardrobe_report_user ON tb_wardrobe_report (user_id, period_end DESC);

-- ---------------------------------------------------------------------------
-- AI 호출 기록 (일일 한도, '코디당 AI 비용' 지표)
-- ---------------------------------------------------------------------------
CREATE TABLE tb_ai_analysis_log (
    ai_analysis_log_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    feature            VARCHAR(30)  NOT NULL DEFAULT 'ITEM_ANALYZE',
    provider           VARCHAR(20)  NOT NULL,
    model              VARCHAR(100),
    status             VARCHAR(20)  NOT NULL,     -- PENDING(호출 중) → SUCCESS | FAILED
    latency_ms         INTEGER,                   -- 호출이 끝난 뒤에 채운다
    input_tokens       INTEGER,
    output_tokens      INTEGER,
    image_sha256       CHAR(64),                    -- 이미지 자체는 저장하지 않는다
    image_bytes        INTEGER,
    result             JSONB,
    error_code         VARCHAR(50),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    CONSTRAINT ck_ai_analysis_log_feature CHECK (feature IN ('ITEM_ANALYZE')),
    CONSTRAINT ck_ai_analysis_log_provider CHECK (provider IN ('mock', 'claude')),
    CONSTRAINT ck_ai_analysis_log_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
-- 일일 한도 집계용 (성공 + 진행 중인 호출을 센다)
CREATE INDEX ix_ai_analysis_log_user_counted ON tb_ai_analysis_log (user_id, created_at)
    WHERE status IN ('SUCCESS', 'PENDING');
