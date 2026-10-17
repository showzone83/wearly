-- 앱 전체 설정 (계약: doc/12_디자인_테마.md 3장)
-- 키 하나에 JSON 값 하나. 지금은 'theme'(전체 사용자의 디자인 테마)만 쓴다.
-- updated_at·updated_by는 관리자가 바꾼 시각·관리자이고, 한 번도 바꾸지 않았으면 NULL이다.
CREATE TABLE tb_app_setting (
    setting_key VARCHAR(50) PRIMARY KEY,
    value       JSONB       NOT NULL,
    updated_by  BIGINT      REFERENCES tb_user (user_id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ
);

-- 초기값: 냥깨비 테마
INSERT INTO tb_app_setting (setting_key, value) VALUES ('theme', '{"themeId":"nyang"}'::jsonb);
