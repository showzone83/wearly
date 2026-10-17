-- 5차 M7: AI 일일 한도를 동시 요청에도 넘지 않게.
-- 한도 기준은 그대로 "오늘(한국 시간) 성공한 tb_ai_analysis_log 수"이고, AI를 부르기 전에
-- 회원·기능별 잠금(pg_advisory_xact_lock) 안에서 "성공 수 + 진행 중 예약 수 < 한도"일 때만 예약을 넣는다.
-- 호출이 끝나면(성공·실패 모두) 예약을 지운다. 서버가 죽어 남은 예약은 30분이 지나면 세지 않고 지운다.
CREATE TABLE tb_ai_usage_reservation (
    reservation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    feature        VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_usage_reservation_user ON tb_ai_usage_reservation (user_id, feature, created_at);
