-- 예정 코디 D-1 알림 (서버 푸시). notify 컨텍스트.
--  - tb_push_token: 앱이 등록한 Expo 푸시 토큰. 회원·기기(device_id)당 하나. 같은 토큰은 한 행만 둔다
--    (기기에서 다른 계정으로 로그인하면 새 계정 쪽으로 옮긴다). 로그아웃할 때 앱이 지우고, 탈퇴하면 CASCADE로 지워진다.
--    Expo가 DeviceNotRegistered로 답하면 서버가 지운다.
--  - tb_push_send_log: D-1 알림 발송 기록. 같은 예정은 하루(한국 날짜 send_date)에 한 번만 보낸다.
--    보내기 전에 행을 먼저 넣어(ON CONFLICT DO NOTHING) 서버가 여러 대여도 한 번만 보낸다. 30일이 지나면 D-1 작업이 지운다.
CREATE TABLE tb_push_token (
    push_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    device_id     VARCHAR(64)  NOT NULL,
    platform      VARCHAR(10)  NOT NULL,
    token         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_token_user_device UNIQUE (user_id, device_id),
    CONSTRAINT uq_push_token_token UNIQUE (token),
    CONSTRAINT ck_push_token_platform CHECK (platform IN ('IOS', 'ANDROID'))
);

CREATE TABLE tb_push_send_log (
    push_send_log_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    planned_outfit_id BIGINT      NOT NULL REFERENCES tb_planned_outfit (planned_outfit_id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    notify_kind       VARCHAR(20) NOT NULL,
    planned_date      DATE        NOT NULL,
    send_date         DATE        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_send_log UNIQUE (planned_outfit_id, notify_kind, send_date),
    CONSTRAINT ck_push_send_log_kind CHECK (notify_kind IN ('DAY_BEFORE'))
);

CREATE INDEX ix_push_send_log_created ON tb_push_send_log (created_at);
