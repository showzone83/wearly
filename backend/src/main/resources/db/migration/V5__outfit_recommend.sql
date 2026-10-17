-- 4차: AI 코디 추천 (doc/09_AI_코디_추천.md 3장)
-- AI 호출 기록에 기능 OUTFIT_RECOMMEND를 더한다. 이미지 없는 호출이라 image_sha256·image_bytes는 NULL이다 (원래 NULL 허용)
ALTER TABLE tb_ai_analysis_log
    DROP CONSTRAINT ck_ai_analysis_log_feature;
ALTER TABLE tb_ai_analysis_log
    ADD CONSTRAINT ck_ai_analysis_log_feature
        CHECK (feature IN ('ITEM_ANALYZE', 'ADMIN_TEST', 'OUTFIT_RECOMMEND'));

-- 일일 한도는 기능별로 성공한 호출만 센다 (사진 분석·코디 추천 따로)
DROP INDEX ix_ai_analysis_log_user_success;
CREATE INDEX ix_ai_analysis_log_user_success ON tb_ai_analysis_log (user_id, feature, created_at)
    WHERE status = 'SUCCESS';
