-- 5차 L6: 기능 이름은 호출하는 컨텍스트가 정한다 (ai는 형식만 본다). 결과 요약 문구는 기록할 때 호출자가 함께 남긴다
-- 1) 기능 목록 CHECK를 형식 CHECK로 바꾼다 (새 기능을 더할 때 ai 코드·DB를 고치지 않는다)
ALTER TABLE tb_ai_analysis_log
    DROP CONSTRAINT ck_ai_analysis_log_feature;
ALTER TABLE tb_ai_analysis_log
    ADD CONSTRAINT ck_ai_analysis_log_feature CHECK (feature ~ '^[A-Z][A-Z0-9_]{1,29}$');

-- 2) 결과 요약 (관리자 AI 기록 목록의 summary). 관리자 화면이 결과 JSON 모양을 추측하지 않게 한다
ALTER TABLE tb_ai_analysis_log
    ADD COLUMN result_summary TEXT;

-- 기존 기록은 4차까지 관리자 서비스가 만들던 문구와 똑같이 채운다
--   코디 추천: "코디 후보 N개", 사진 분석: "이름 · 종류/세부 종류 · 색" (값이 없으면 이름은 빈 문자열, 나머지는 null)
UPDATE tb_ai_analysis_log
   SET result_summary = CASE
        WHEN jsonb_typeof(result -> 'candidates') = 'array'
            THEN '코디 후보 ' || jsonb_array_length(result -> 'candidates') || '개'
        ELSE coalesce(result ->> 'name', '')
            || ' · ' || coalesce(result ->> 'category', 'null')
            || '/' || coalesce(result ->> 'subType', 'null')
            || ' · ' || coalesce(result ->> 'color', 'null')
        END
 WHERE result IS NOT NULL;
