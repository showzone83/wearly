-- V1~V22에서 만든 테이블·컬럼에 DB 코멘트(COMMENT ON)를 단다 (doc/08_백엔드_구조.md 8장).
-- 지금까지 설명은 마이그레이션 파일의 -- 주석에만 있어 DB 도구(psql \d+, DBeaver 등)에서 보이지 않았다.
-- 적용된 마이그레이션은 체크섬 때문에 고치지 않고 여기에 모은다. 내용은 각 파일의 주석과 계약 문서를 옮겼다.
-- 이후 새 테이블·컬럼은 만드는 마이그레이션에서 바로 COMMENT ON을 쓴다 (V23 날씨 이력부터).

-- ---------------------------------------------------------------------------
-- 회원 (V1·V3·V10)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_user IS '회원. 이름은 여기에만 두고 프로필은 tb_user_profile';
COMMENT ON COLUMN tb_user.user_id IS '회원 ID';
COMMENT ON COLUMN tb_user.email IS '이메일 (참고용). 소셜 계정은 없을 수 있다';
COMMENT ON COLUMN tb_user.name IS '이름 (20자 이하)';
COMMENT ON COLUMN tb_user.status IS '상태: ACTIVE · SUSPENDED';
COMMENT ON COLUMN tb_user.role IS '역할: USER · ADMIN. 관리자는 서버 시작 시 WEARLY_ADMIN_*로만 만든다 (가입 API로는 못 만든다)';
COMMENT ON COLUMN tb_user.created_at IS '가입 시각';
COMMENT ON COLUMN tb_user.updated_at IS '마지막 수정 시각';

COMMENT ON TABLE tb_user_local_credential IS '이메일·비밀번호 자격 증명 (임시 수단, 소셜 로그인 도입 후 제거 가능). 회원당 하나';
COMMENT ON COLUMN tb_user_local_credential.credential_id IS '자격 증명 ID';
COMMENT ON COLUMN tb_user_local_credential.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_local_credential.email IS '로그인 이메일 (소문자, 중복 불가)';
COMMENT ON COLUMN tb_user_local_credential.password_hash IS '비밀번호 해시 (원문은 저장하지 않는다)';
COMMENT ON COLUMN tb_user_local_credential.last_login_at IS '마지막 로그인 시각';
COMMENT ON COLUMN tb_user_local_credential.created_at IS '만든 시각';
COMMENT ON COLUMN tb_user_local_credential.updated_at IS '마지막 수정 시각';

COMMENT ON TABLE tb_user_social IS '소셜 계정 연결 (D5 설계). 1차 API에서는 쓰지 않는다';
COMMENT ON COLUMN tb_user_social.social_id IS '소셜 연결 ID';
COMMENT ON COLUMN tb_user_social.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_social.provider IS '소셜 공급자: KAKAO · GOOGLE · APPLE · NAVER';
COMMENT ON COLUMN tb_user_social.provider_user_id IS '공급자 쪽 사용자 ID (공급자와 함께 중복 불가)';
COMMENT ON COLUMN tb_user_social.email IS '공급자가 준 이메일 (없을 수 있다)';
COMMENT ON COLUMN tb_user_social.connected_at IS '연결한 시각';

COMMENT ON TABLE tb_user_profile IS '취향·근무 패턴 프로필 (앱 Profile + onboarded). 회원당 하나';
COMMENT ON COLUMN tb_user_profile.profile_id IS '프로필 ID';
COMMENT ON COLUMN tb_user_profile.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_profile.dress_codes IS '회사 복장 규정: CASUAL · BIZ_CASUAL · FORMAL';
COMMENT ON COLUMN tb_user_profile.moods IS '좋아하는 스타일 (STYLE 코드)';
COMMENT ON COLUMN tb_user_profile.colors IS '좋아하는 색 (COLOR 코드)';
COMMENT ON COLUMN tb_user_profile.work_type IS '근무 패턴: WEEKDAY · WEEKEND · CUSTOM';
COMMENT ON COLUMN tb_user_profile.work_days IS '근무 요일. 0 = 일 … 6 = 토';
COMMENT ON COLUMN tb_user_profile.work_rule IS 'true면 근무일에 출근 코디로 추천한다';
COMMENT ON COLUMN tb_user_profile.off_rule IS 'true면 휴무일에 데일리(편한) 코디로 추천한다';
COMMENT ON COLUMN tb_user_profile.notify IS '알림 설정 {morning, evening, dMinus1}';
COMMENT ON COLUMN tb_user_profile.onboarded IS '첫 설정(온보딩)을 마쳤는지';
COMMENT ON COLUMN tb_user_profile.age_range IS '나의 정보: 연령대 (AGE_RANGE 코드, 선택)';
COMMENT ON COLUMN tb_user_profile.body_type IS '나의 정보: 체형 (BODY_TYPE 코드, 골격 유형, 선택)';
COMMENT ON COLUMN tb_user_profile.height_cm IS '나의 정보: 키(cm) 130~210 (선택)';
COMMENT ON COLUMN tb_user_profile.fit_preference IS '나의 정보: 핏 선호 (FIT 코드, 선택)';
COMMENT ON COLUMN tb_user_profile.created_at IS '만든 시각';
COMMENT ON COLUMN tb_user_profile.updated_at IS '마지막 수정 시각';

COMMENT ON TABLE tb_user_style IS '취향 학습 점수 (테이블만, 아직 쓰지 않는다)';
COMMENT ON COLUMN tb_user_style.user_style_id IS '취향 점수 ID';
COMMENT ON COLUMN tb_user_style.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_style.style_code IS '스타일 (STYLE 코드)';
COMMENT ON COLUMN tb_user_style.preference_score IS '선호 점수';
COMMENT ON COLUMN tb_user_style.updated_at IS '마지막 수정 시각';

COMMENT ON TABLE tb_auth_event IS '회원별 로그인 이력 (로그인·로그아웃·재사용 감지·강제 로그아웃·비밀번호 실패·2단계 인증·SSO). 관리자 로그인 기록 화면도 이걸 본다. 누가 끊었는지는 tb_admin_audit_log에 따로 남는다 (doc/16·doc/22)';
COMMENT ON COLUMN tb_auth_event.auth_event_id IS '이력 ID';
COMMENT ON COLUMN tb_auth_event.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_auth_event.type IS '종류: LOGIN · LOGOUT · REFRESH_REUSED · FORCED_LOGOUT · LOGIN_FAILED(가입한 이메일로 비밀번호 틀림) · MFA_SUCCESS · MFA_FAILED · MFA_LOCKED(5번 틀려 15분 잠김) · SSO_LOGIN(회사 계정 로그인) · SSO_DENIED(IdP 인증은 됐지만 관리자 그룹이 아니라 거부)';
COMMENT ON COLUMN tb_auth_event.session_id IS '로그인 세션 ID (UUID 문자열, 세션은 Valkey에 있다)';
COMMENT ON COLUMN tb_auth_event.client IS '클라이언트: APP · ADMIN · WEB';
COMMENT ON COLUMN tb_auth_event.ip IS '요청 IP (IPv6까지 45자)';
COMMENT ON COLUMN tb_auth_event.user_agent IS 'User-Agent (300자까지)';
COMMENT ON COLUMN tb_auth_event.sso_provider IS 'SSO 공급자 ID (wearly.sso.providers.<id>). SSO가 아니면 NULL';
COMMENT ON COLUMN tb_auth_event.sso_amr IS 'IdP가 알려 준 인증 방법 amr (예: pwd,otp). 없으면 NULL';
COMMENT ON COLUMN tb_auth_event.created_at IS '일어난 시각';

-- ---------------------------------------------------------------------------
-- 옷장 (V1·V2·V9)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_wardrobe_item IS '내 옷장의 옷. 속성 값은 코드 테이블(tb_code)로 서버가 검증한다';
COMMENT ON COLUMN tb_wardrobe_item.item_id IS '옷 ID';
COMMENT ON COLUMN tb_wardrobe_item.user_id IS '주인 (tb_user)';
COMMENT ON COLUMN tb_wardrobe_item.name IS '이름 (40자 이하)';
COMMENT ON COLUMN tb_wardrobe_item.category IS '종류 (CATEGORY 코드)';
COMMENT ON COLUMN tb_wardrobe_item.sub_type IS '세부 종류 (SUB_TYPE 코드, 부모가 category)';
COMMENT ON COLUMN tb_wardrobe_item.color IS '주 색상 (COLOR 코드). API의 colors = [color] + sub_colors';
COMMENT ON COLUMN tb_wardrobe_item.pattern IS '패턴 (PATTERN 코드)';
COMMENT ON COLUMN tb_wardrobe_item.seasons IS '입는 계절 (SEASON 코드: 초봄·봄·초여름·여름·초가을·가을·겨울)';
COMMENT ON COLUMN tb_wardrobe_item.styles IS '스타일 (STYLE 코드)';
COMMENT ON COLUMN tb_wardrobe_item.formality IS '격식 1(캐주얼)~3(포멀)';
COMMENT ON COLUMN tb_wardrobe_item.status IS '보유 상태 (ITEM_STATUS 코드: 보유·세탁·수선 등). 사용 중지(retired_at)와는 별개';
COMMENT ON COLUMN tb_wardrobe_item.brand IS '브랜드 (선택)';
COMMENT ON COLUMN tb_wardrobe_item.worn_count IS '착용 횟수';
COMMENT ON COLUMN tb_wardrobe_item.last_worn_at IS '마지막 착용 날짜';
COMMENT ON COLUMN tb_wardrobe_item.ai_suggestion IS '등록 당시 AI 제안값 (1차 API에서는 비워 둔다)';
COMMENT ON COLUMN tb_wardrobe_item.created_at IS '등록 시각';
COMMENT ON COLUMN tb_wardrobe_item.updated_at IS '마지막 수정 시각';
COMMENT ON COLUMN tb_wardrobe_item.retired_at IS '사용 중지한 시각. NULL이면 사용 중 (doc/13)';
COMMENT ON COLUMN tb_wardrobe_item.retire_reason IS '사용 중지 이유 (RETIRE_REASON 코드). retired_at과 함께 있다';
COMMENT ON COLUMN tb_wardrobe_item.retire_memo IS '사용 중지 메모 (선택, 200자 이하)';
COMMENT ON COLUMN tb_wardrobe_item.sub_colors IS '보조 색 0~2개 (COLOR 코드, 순서 유지, 주 색상과 겹치지 않음)';
COMMENT ON COLUMN tb_wardrobe_item.purchased_at IS '구입일 (1990-01-01부터). 모르면 등록일(서울 날짜)';
COMMENT ON COLUMN tb_wardrobe_item.purchased_at_estimated IS 'true면 구입일을 몰라 등록일로 넣은 값 ("모름(등록일)")';

COMMENT ON TABLE tb_item_image IS '옷 사진 메타데이터 (V12). 파일은 S3 호환 저장소(로컬 MinIO 버킷 wearly-images, 비공개)에 있다 (doc/18)';
COMMENT ON COLUMN tb_item_image.image_id IS '서버가 만든 무작위 UUID (API의 imageId)';
COMMENT ON COLUMN tb_item_image.user_id IS '올린 회원 (tb_user)';
COMMENT ON COLUMN tb_item_image.item_id IS '붙은 옷 (tb_wardrobe_item, 옷당 하나). 아직 안 붙였거나 DELETED면 NULL';
COMMENT ON COLUMN tb_item_image.purpose IS '용도: ITEM';
COMMENT ON COLUMN tb_item_image.status IS '상태: PENDING(업로드 URL 발급) · READY(저장 완료) · DELETED. 옷에 붙을 수 있는 것은 READY뿐';
COMMENT ON COLUMN tb_item_image.content_type IS 'PENDING: 업로드 URL을 받을 때 선언한 형식, READY: 저장한 original의 형식(image/jpeg)';
COMMENT ON COLUMN tb_item_image.declared_size IS '업로드 URL을 받을 때 선언한 크기(바이트)';
COMMENT ON COLUMN tb_item_image.size_bytes IS '저장한 original.jpg 크기(바이트, READY부터)';
COMMENT ON COLUMN tb_item_image.width IS 'original.jpg 가로(px, READY부터)';
COMMENT ON COLUMN tb_item_image.height IS 'original.jpg 세로(px, READY부터)';
COMMENT ON COLUMN tb_item_image.upload_key IS '업로드 객체 키 uploads/{userId}/{imageId} (처리 후 지운다)';
COMMENT ON COLUMN tb_item_image.object_key IS '원본 객체 키 users/{userId}/items/{imageId}/original.jpg';
COMMENT ON COLUMN tb_item_image.thumb_key IS '썸네일 객체 키 users/{userId}/items/{imageId}/thumb.jpg';
COMMENT ON COLUMN tb_item_image.created_at IS '업로드 URL을 발급한 시각 (하루 업로드 한도 집계)';
COMMENT ON COLUMN tb_item_image.ready_at IS '저장을 마친 시각';
COMMENT ON COLUMN tb_item_image.deleted_at IS '지운 시각';

-- ---------------------------------------------------------------------------
-- 코디 (V1, 테이블만)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_outfit IS '추천·저장한 코디 (테이블만)';
COMMENT ON COLUMN tb_outfit.outfit_id IS '코디 ID';
COMMENT ON COLUMN tb_outfit.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_outfit.score IS '추천 점수 0~100';
COMMENT ON COLUMN tb_outfit.breakdown IS '점수 항목별 내역 (앱 ScoreBreakdown: 색상·상황·날씨·취향·다양성)';
COMMENT ON COLUMN tb_outfit.context IS '추천 조건 (앱 RecommendContext)';
COMMENT ON COLUMN tb_outfit.reasons IS '추천 이유 문구 목록';
COMMENT ON COLUMN tb_outfit.saved_at IS '사용자가 저장한 시각. NULL이면 저장하지 않음';
COMMENT ON COLUMN tb_outfit.created_at IS '만든 시각';

COMMENT ON TABLE tb_outfit_item IS '코디에 들어간 옷 (칸마다 하나)';
COMMENT ON COLUMN tb_outfit_item.outfit_item_id IS '코디 옷 ID';
COMMENT ON COLUMN tb_outfit_item.outfit_id IS '코디 (tb_outfit)';
COMMENT ON COLUMN tb_outfit_item.item_id IS '옷 (tb_wardrobe_item)';
COMMENT ON COLUMN tb_outfit_item.item_role IS '칸: TOP · BOTTOM · DRESS · OUTER · SHOES · BAG';

COMMENT ON TABLE tb_outfit_feedback IS '코디 좋아요·싫어요 (회원당 코디 하나에 하나)';
COMMENT ON COLUMN tb_outfit_feedback.feedback_id IS '피드백 ID';
COMMENT ON COLUMN tb_outfit_feedback.outfit_id IS '코디 (tb_outfit)';
COMMENT ON COLUMN tb_outfit_feedback.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_outfit_feedback.feedback_type IS 'LIKE · DISLIKE';
COMMENT ON COLUMN tb_outfit_feedback.reasons IS '이유 목록 (싫어요 이유 등)';
COMMENT ON COLUMN tb_outfit_feedback.created_at IS '남긴 시각';

COMMENT ON TABLE tb_planned_outfit IS '예정일 코디 (캘린더 일정, V14에서 다시 만듦). 앱과 동기화한다 (doc/21)';
COMMENT ON COLUMN tb_planned_outfit.planned_outfit_id IS '예정 ID';
COMMENT ON COLUMN tb_planned_outfit.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_planned_outfit.client_id IS '앱이 기기에서 만든 예정 ID (회원 안에서 유일, 일괄 올리기 중복 방지)';
COMMENT ON COLUMN tb_planned_outfit.planned_date IS '예정 날짜';
COMMENT ON COLUMN tb_planned_outfit.time_of_day IS '시간대: AM · PM · EVENING';
COMMENT ON COLUMN tb_planned_outfit.title IS '일정 제목 (50자 이하)';
COMMENT ON COLUMN tb_planned_outfit.occasion IS '상황: COMMUTE · MEETING · FIELD · DINNER · DAILY · INTERVIEW · WEDDING · DATE · TRAVEL · GATHERING';
COMMENT ON COLUMN tb_planned_outfit.concepts IS '원하는 컨셉 (STYLE 코드)';
COMMENT ON COLUMN tb_planned_outfit.memo IS '메모 (500자 이하)';
COMMENT ON COLUMN tb_planned_outfit.place_region_code IS '예정 장소의 지역 (REGION 코드, 선택). 장소가 없으면 내 지역을 쓴다';
COMMENT ON COLUMN tb_planned_outfit.place_label IS '예정 장소 이름. 장소가 있으면 좌표와 함께 있다';
COMMENT ON COLUMN tb_planned_outfit.place_lat IS '예정 장소 위도 -90~90';
COMMENT ON COLUMN tb_planned_outfit.place_lon IS '예정 장소 경도 -180~180';
COMMENT ON COLUMN tb_planned_outfit.weather IS '그날 날씨 (앱 Weather + 출처·확인 시각) {temp, min, max, rainProb, cond, manual, source, checkedAt}';
COMMENT ON COLUMN tb_planned_outfit.forecast_ready IS '"예보가 나왔어요" 표시. 평년값으로 저장한 예정에 예보가 나왔고 기온 3° 이상 또는 비 여부가 달라져 다시 추천해야 하면 true (doc/19)';
COMMENT ON COLUMN tb_planned_outfit.outfit IS '정해 둔 코디 (앱 Outfit 모양, items: 칸 → 옷 ID). 옷이 지워져도 그대로 둔다';
COMMENT ON COLUMN tb_planned_outfit.created_at IS '만든 시각';
COMMENT ON COLUMN tb_planned_outfit.updated_at IS '마지막 수정 시각';

-- ---------------------------------------------------------------------------
-- 착용 기록·진단 (V1, 테이블만)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_daily_look IS '그날 입은 옷 기록 (회원당 하루 하나)';
COMMENT ON COLUMN tb_daily_look.daily_look_id IS '착용 기록 ID';
COMMENT ON COLUMN tb_daily_look.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_daily_look.worn_date IS '입은 날짜';
COMMENT ON COLUMN tb_daily_look.occasion IS '상황 (tb_planned_outfit.occasion과 같은 값)';
COMMENT ON COLUMN tb_daily_look.self_score IS '스스로 매긴 점수 1~5';
COMMENT ON COLUMN tb_daily_look.emotions IS '느낌: CONFIDENT · COMFY · AWKWARD · UNCOMFY';
COMMENT ON COLUMN tb_daily_look.memo IS '메모';
COMMENT ON COLUMN tb_daily_look.others_reaction IS '주변 반응: PRAISE · NORMAL · MEH';
COMMENT ON COLUMN tb_daily_look.praised_item_id IS '칭찬받은 옷 (tb_wardrobe_item, 없으면 NULL)';
COMMENT ON COLUMN tb_daily_look.created_at IS '기록한 시각';

COMMENT ON TABLE tb_daily_look_item IS '착용 기록에 들어간 옷 (칸마다 하나)';
COMMENT ON COLUMN tb_daily_look_item.daily_look_item_id IS '착용 옷 ID';
COMMENT ON COLUMN tb_daily_look_item.daily_look_id IS '착용 기록 (tb_daily_look)';
COMMENT ON COLUMN tb_daily_look_item.item_id IS '옷 (tb_wardrobe_item)';
COMMENT ON COLUMN tb_daily_look_item.item_role IS '칸: TOP · BOTTOM · DRESS · OUTER · SHOES · BAG';

COMMENT ON TABLE tb_look_diagnosis IS '착용 코디 진단 결과 (5개 항목, 강점·개선점, 교체 처방)';
COMMENT ON COLUMN tb_look_diagnosis.diagnosis_id IS '진단 ID';
COMMENT ON COLUMN tb_look_diagnosis.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_look_diagnosis.daily_look_id IS '진단한 착용 기록 (tb_daily_look, 기록당 하나)';
COMMENT ON COLUMN tb_look_diagnosis.ai_score IS '진단 점수 0~100';
COMMENT ON COLUMN tb_look_diagnosis.axes IS '항목별 점수 (앱 AxisScores)';
COMMENT ON COLUMN tb_look_diagnosis.strength IS '강점 항목: color · silhouette · tpo · weather · diversity';
COMMENT ON COLUMN tb_look_diagnosis.weakness IS '개선점 항목 (strength와 같은 값)';
COMMENT ON COLUMN tb_look_diagnosis.swap IS '내 옷장 교체 처방 {slot, fromId, toId}';
COMMENT ON COLUMN tb_look_diagnosis.tip IS '조언 문구';
COMMENT ON COLUMN tb_look_diagnosis.created_at IS '진단한 시각';

COMMENT ON TABLE tb_wardrobe_report IS '기간별 옷장 리포트 (테이블만)';
COMMENT ON COLUMN tb_wardrobe_report.report_id IS '리포트 ID';
COMMENT ON COLUMN tb_wardrobe_report.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_wardrobe_report.period_start IS '기간 시작 날짜';
COMMENT ON COLUMN tb_wardrobe_report.period_end IS '기간 끝 날짜 (시작 이후)';
COMMENT ON COLUMN tb_wardrobe_report.report IS '리포트 내용';
COMMENT ON COLUMN tb_wardrobe_report.created_at IS '만든 시각';

-- ---------------------------------------------------------------------------
-- 코드 (V2, doc/06)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_code_group IS '코드 그룹 (CATEGORY · SUB_TYPE · COLOR · PATTERN · SEASON · STYLE · ITEM_STATUS · RETIRE_REASON · REGION · AGE_RANGE · BODY_TYPE · FIT 등, doc/06)';
COMMENT ON COLUMN tb_code_group.group_code IS '그룹 코드';
COMMENT ON COLUMN tb_code_group.name IS '그룹 이름 (관리자 화면 표시)';
COMMENT ON COLUMN tb_code_group.description IS '설명 (attrs 모양 등)';
COMMENT ON COLUMN tb_code_group.sort_order IS '표시 순서';
COMMENT ON COLUMN tb_code_group.created_at IS '만든 시각';
COMMENT ON COLUMN tb_code_group.updated_at IS '마지막 수정 시각';

COMMENT ON TABLE tb_code IS '코드 값. 옷 속성·지역 등을 enum·CHECK 대신 여기서 관리하고 서버가 검증한다 (doc/06)';
COMMENT ON COLUMN tb_code.code_id IS '코드 ID';
COMMENT ON COLUMN tb_code.group_code IS '그룹 (tb_code_group)';
COMMENT ON COLUMN tb_code.code IS '코드 값 (그룹 안에서 중복 불가, 1~30자)';
COMMENT ON COLUMN tb_code.label IS '한국어 표시 이름';
COMMENT ON COLUMN tb_code.parent_code IS '부모 코드. SUB_TYPE만 CATEGORY 코드를 둔다';
COMMENT ON COLUMN tb_code.sort_order IS '표시 순서';
COMMENT ON COLUMN tb_code.active IS 'false면 새로 고를 수 없지만 저장된 값의 이름은 보여 준다';
COMMENT ON COLUMN tb_code.attrs IS '그룹별 추가 속성 (예: REGION은 {lat, lon}, ITEM_STATUS는 {recommendable})';
COMMENT ON COLUMN tb_code.created_at IS '만든 시각';
COMMENT ON COLUMN tb_code.updated_at IS '마지막 수정 시각';

-- ---------------------------------------------------------------------------
-- AI (V1·V3~V7, doc/07·09)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_ai_provider IS 'AI 공급자. 키 값은 저장하지 않고 키가 든 환경변수 이름만 둔다 (웹 입력 키는 tb_ai_provider_secret)';
COMMENT ON COLUMN tb_ai_provider.provider_id IS '관리자가 정하는 이름 (claude, openai, gemini …)';
COMMENT ON COLUMN tb_ai_provider.type IS '구현 종류: CLAUDE · OPENAI · GEMINI';
COMMENT ON COLUMN tb_ai_provider.display_name IS '표시 이름';
COMMENT ON COLUMN tb_ai_provider.model IS '모델 ID. 사용(enabled)하려면 필수';
COMMENT ON COLUMN tb_ai_provider.api_key_env IS '키가 들어 있는 환경변수 이름';
COMMENT ON COLUMN tb_ai_provider.enabled IS '사용 여부';
COMMENT ON COLUMN tb_ai_provider.priority IS '우선순위. 작을수록 먼저 쓴다';
COMMENT ON COLUMN tb_ai_provider.options IS '구현별 옵션 (예: {"effort":"medium"})';
COMMENT ON COLUMN tb_ai_provider.created_at IS '만든 시각';
COMMENT ON COLUMN tb_ai_provider.updated_at IS '마지막 수정 시각';

COMMENT ON TABLE tb_ai_provider_secret IS '관리자가 웹으로 넣은 AI 키. AES-256-GCM 암호문만 두고 마스터 키(WEARLY_SECRET_KEY)는 DB에 두지 않는다. AAD에 provider_id를 넣어 다른 행에 옮기면 복호화되지 않는다 (doc/07 6장)';
COMMENT ON COLUMN tb_ai_provider_secret.provider_id IS '공급자 (tb_ai_provider)';
COMMENT ON COLUMN tb_ai_provider_secret.ciphertext IS '암호문 + GCM 태그(16바이트)';
COMMENT ON COLUMN tb_ai_provider_secret.nonce IS '저장할 때마다 새로 만드는 무작위 12바이트';
COMMENT ON COLUMN tb_ai_provider_secret.key_version IS '마스터 키 버전 (나중에 KMS·키 교체용, 지금 1)';
COMMENT ON COLUMN tb_ai_provider_secret.last4 IS '화면 표시용 키 끝 4자';
COMMENT ON COLUMN tb_ai_provider_secret.updated_by IS '넣은 관리자 (tb_user, 탈퇴 시 NULL)';
COMMENT ON COLUMN tb_ai_provider_secret.updated_at IS '넣은 시각';

COMMENT ON TABLE tb_ai_analysis_log IS 'AI 호출 기록 (일일 한도 집계, 코디당 AI 비용 지표, 관리자 AI 기록)';
COMMENT ON COLUMN tb_ai_analysis_log.ai_analysis_log_id IS '기록 ID';
COMMENT ON COLUMN tb_ai_analysis_log.user_id IS '호출한 회원 (tb_user)';
COMMENT ON COLUMN tb_ai_analysis_log.feature IS '기능 (ITEM_ANALYZE · ADMIN_TEST · OUTFIT_RECOMMEND 등, 대문자 형식만 검사). 호출하는 컨텍스트가 정한다';
COMMENT ON COLUMN tb_ai_analysis_log.provider IS '공급자 (tb_ai_provider.provider_id 또는 mock)';
COMMENT ON COLUMN tb_ai_analysis_log.model IS '모델 ID';
COMMENT ON COLUMN tb_ai_analysis_log.status IS '결과: SUCCESS · FAILED. 일일 한도는 기능별 SUCCESS만 센다';
COMMENT ON COLUMN tb_ai_analysis_log.latency_ms IS '응답 시간(ms)';
COMMENT ON COLUMN tb_ai_analysis_log.input_tokens IS '입력 토큰 수';
COMMENT ON COLUMN tb_ai_analysis_log.output_tokens IS '출력 토큰 수';
COMMENT ON COLUMN tb_ai_analysis_log.image_sha256 IS '보낸 이미지의 SHA-256 hex (이미지 자체는 저장하지 않는다, 이미지 없는 호출은 NULL)';
COMMENT ON COLUMN tb_ai_analysis_log.image_bytes IS '보낸 이미지 크기(바이트)';
COMMENT ON COLUMN tb_ai_analysis_log.result IS '결과 JSON';
COMMENT ON COLUMN tb_ai_analysis_log.error_code IS '실패 오류 코드';
COMMENT ON COLUMN tb_ai_analysis_log.created_at IS '호출 시각';
COMMENT ON COLUMN tb_ai_analysis_log.result_summary IS '결과 요약 문구 (관리자 AI 기록 목록). 기록할 때 호출자가 남긴다';

COMMENT ON TABLE tb_ai_usage_reservation IS 'AI 일일 한도 예약. 호출 전 회원·기능별 잠금 안에서 "성공 수 + 예약 수 < 한도"일 때만 넣고, 호출이 끝나면 지운다. 30분 지난 예약은 세지 않고 지운다';
COMMENT ON COLUMN tb_ai_usage_reservation.reservation_id IS '예약 ID';
COMMENT ON COLUMN tb_ai_usage_reservation.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_ai_usage_reservation.feature IS '기능 (tb_ai_analysis_log.feature와 같은 값)';
COMMENT ON COLUMN tb_ai_usage_reservation.created_at IS '예약한 시각';

-- ---------------------------------------------------------------------------
-- 관리자·앱 설정 (V3·V8)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_admin_audit_log IS '관리자 변경 이력. 관리자가 탈퇴해도 남긴다 (admin_user_id는 NULL이 된다)';
COMMENT ON COLUMN tb_admin_audit_log.audit_id IS '이력 ID';
COMMENT ON COLUMN tb_admin_audit_log.admin_user_id IS '바꾼 관리자 (tb_user)';
COMMENT ON COLUMN tb_admin_audit_log.action IS '동작 (예: CODE_UPDATE, SESSION_REVOKE)';
COMMENT ON COLUMN tb_admin_audit_log.target_type IS '대상 종류: CODE · AI_PROVIDER · USER · APP_SETTING · SESSION · WEATHER_SETTING · WEATHER_PROVIDER · WEATHER_REFRESH 등';
COMMENT ON COLUMN tb_admin_audit_log.target_id IS '대상 ID';
COMMENT ON COLUMN tb_admin_audit_log.before IS '바꾸기 전 값 (키 원문 등 비밀 값은 넣지 않는다)';
COMMENT ON COLUMN tb_admin_audit_log.after IS '바꾼 뒤 값';
COMMENT ON COLUMN tb_admin_audit_log.created_at IS '바꾼 시각';

COMMENT ON TABLE tb_app_setting IS '앱 전체 설정. 키 하나에 JSON 값 하나 (지금은 theme만, doc/12)';
COMMENT ON COLUMN tb_app_setting.setting_key IS '설정 키 (예: theme)';
COMMENT ON COLUMN tb_app_setting.value IS '설정 값 (예: {"themeId":"nyang"})';
COMMENT ON COLUMN tb_app_setting.updated_by IS '바꾼 관리자 (tb_user). 한 번도 바꾸지 않았으면 NULL';
COMMENT ON COLUMN tb_app_setting.updated_at IS '바꾼 시각. 한 번도 바꾸지 않았으면 NULL';

-- ---------------------------------------------------------------------------
-- 날씨 (V11, doc/17)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_weather_setting IS '날씨 스케줄 설정. 한 행만 둔다 (id = 1). 바꾸면 서버를 다시 띄우지 않아도 다음 실행부터 반영된다';
COMMENT ON COLUMN tb_weather_setting.id IS '항상 1';
COMMENT ON COLUMN tb_weather_setting.enabled IS '주기 실행 사용 여부';
COMMENT ON COLUMN tb_weather_setting.interval_minutes IS '실행 주기(분) 10~1440. 다음 실행 = 직전 실행이 끝난 시각 + 주기';
COMMENT ON COLUMN tb_weather_setting.forecast_days IS '받을 예보 일수 1~14 (공급자 최대와 작은 쪽을 쓴다)';
COMMENT ON COLUMN tb_weather_setting.failure_threshold IS '공급자를 차단하는 연속 실패 수';
COMMENT ON COLUMN tb_weather_setting.cooldown_minutes IS '차단 시간(분). 끝나면 한 번 시험해 본다';
COMMENT ON COLUMN tb_weather_setting.updated_by IS '바꾼 관리자 (tb_user)';
COMMENT ON COLUMN tb_weather_setting.updated_at IS '바꾼 시각';

COMMENT ON TABLE tb_weather_provider IS '날씨 공급자. 키 값은 저장하지 않고 서버 설정의 키 이름만 둔다. 설정 칸은 관리자가, 상태 칸은 실행이 바꾼다';
COMMENT ON COLUMN tb_weather_provider.provider_code IS '공급자 코드 (지금 WEATHERAPI)';
COMMENT ON COLUMN tb_weather_provider.display_name IS '표시 이름';
COMMENT ON COLUMN tb_weather_provider.enabled IS '사용 여부 (관리자)';
COMMENT ON COLUMN tb_weather_provider.priority IS '우선순위 0~10000. 작을수록 먼저 쓴다 (관리자)';
COMMENT ON COLUMN tb_weather_provider.key_name IS '서버 설정 키 이름 (wearly.weather.keys.<key_name>, 없으면 같은 이름의 환경변수)';
COMMENT ON COLUMN tb_weather_provider.monthly_quota IS '이번 달(한국 시간) 호출 한도. NULL이면 없음 (관리자)';
COMMENT ON COLUMN tb_weather_provider.max_forecast_days IS '공급자가 줄 수 있는 최대 예보 일수 (관리자)';
COMMENT ON COLUMN tb_weather_provider.consecutive_failures IS '연속 실패 수 (실행)';
COMMENT ON COLUMN tb_weather_provider.circuit_reason IS '차단 이유: NULL(정상) · FAILURES(연속 실패) · AUTH(키 문제, 관리자가 풀 때까지)';
COMMENT ON COLUMN tb_weather_provider.circuit_open_until IS 'FAILURES 차단이 풀리는 시각';
COMMENT ON COLUMN tb_weather_provider.last_success_at IS '마지막 성공 시각';
COMMENT ON COLUMN tb_weather_provider.last_failure_at IS '마지막 실패 시각';
COMMENT ON COLUMN tb_weather_provider.last_error IS '마지막 오류 문구 (키는 지운다)';
COMMENT ON COLUMN tb_weather_provider.updated_by IS '설정을 바꾼 관리자 (tb_user)';
COMMENT ON COLUMN tb_weather_provider.updated_at IS '설정을 바꾼 시각';

COMMENT ON TABLE tb_weather_refresh_run IS '날씨 받기 실행 이력. 스케줄·수동·시작·시험 실행 한 번이 한 행 (log-retention-days가 지나면 지운다)';
COMMENT ON COLUMN tb_weather_refresh_run.run_id IS '실행 ID';
COMMENT ON COLUMN tb_weather_refresh_run.trigger_type IS '시작 계기: SCHEDULE · MANUAL · STARTUP · TEST';
COMMENT ON COLUMN tb_weather_refresh_run.requested_by IS '실행한 관리자 (tb_user, 수동·시험만)';
COMMENT ON COLUMN tb_weather_refresh_run.region_code IS '한 지역만 실행했으면 그 지역, 전체면 NULL';
COMMENT ON COLUMN tb_weather_refresh_run.status IS '상태: RUNNING · SUCCESS · PARTIAL · FAILED · SKIPPED';
COMMENT ON COLUMN tb_weather_refresh_run.skip_reason IS '건너뛴 이유: DISABLED · NO_PROVIDER · LOCKED';
COMMENT ON COLUMN tb_weather_refresh_run.success_count IS '받기에 성공한 지역 수';
COMMENT ON COLUMN tb_weather_refresh_run.fail_count IS '실패한 지역 수';
COMMENT ON COLUMN tb_weather_refresh_run.fallback_count IS '1순위가 아닌 공급자로 받은 지역 수';
COMMENT ON COLUMN tb_weather_refresh_run.started_at IS '시작 시각';
COMMENT ON COLUMN tb_weather_refresh_run.finished_at IS '끝난 시각. NULL이면 진행 중';

COMMENT ON TABLE tb_weather_call_log IS '날씨 공급자 HTTP 호출 이력. 호출 한 번이 한 행이고 월 한도도 이걸로 센다. 실행 밖 호출(ON_DEMAND·SEARCH·CLIMATE)도 남긴다. 오류 문구에 키는 들어가지 않는다';
COMMENT ON COLUMN tb_weather_call_log.call_id IS '호출 ID';
COMMENT ON COLUMN tb_weather_call_log.run_id IS '실행 (tb_weather_refresh_run, 실행을 지우면 함께 지워진다). FORECAST만 있고 실행 밖 호출은 NULL';
COMMENT ON COLUMN tb_weather_call_log.region_code IS 'FORECAST: REGION 코드, ON_DEMAND·CLIMATE: 격자 키("37.6,127.0"), SEARCH: NULL';
COMMENT ON COLUMN tb_weather_call_log.provider_code IS '공급자 (tb_weather_provider, 평년값은 OPEN_METEO)';
COMMENT ON COLUMN tb_weather_call_log.call_kind IS '호출 종류: FORECAST(실행의 예보) · ON_DEMAND(좌표 요청 예보) · SEARCH(장소 검색) · CLIMATE(평년값용 과거 자료) (doc/19)';
COMMENT ON COLUMN tb_weather_call_log.fallback IS '1순위가 아닌 공급자로 부른 호출인지';
COMMENT ON COLUMN tb_weather_call_log.http_status IS 'HTTP 상태 코드 (연결 실패·시간 초과면 NULL)';
COMMENT ON COLUMN tb_weather_call_log.success IS '성공 여부';
COMMENT ON COLUMN tb_weather_call_log.error_code IS '오류 코드: TIMEOUT · HTTP_4XX · HTTP_429 · HTTP_5XX · AUTH · QUOTA · PARSE · CLIENT';
COMMENT ON COLUMN tb_weather_call_log.error_message IS '오류 문구 (키를 지우고 300자까지)';
COMMENT ON COLUMN tb_weather_call_log.duration_ms IS '걸린 시간(ms)';
COMMENT ON COLUMN tb_weather_call_log.response_bytes IS '응답 크기(바이트)';
COMMENT ON COLUMN tb_weather_call_log.called_at IS '호출 시각';

COMMENT ON TABLE tb_weather_snapshot IS '지역별 최신 날씨. 앱 GET /weather는 이 값만 준다. 받기에 실패하면 이전 값은 두고 last_error만 바꾼다';
COMMENT ON COLUMN tb_weather_snapshot.region_code IS '지역 (코드 그룹 REGION)';
COMMENT ON COLUMN tb_weather_snapshot.provider_code IS '이 값을 준 공급자 (tb_weather_provider)';
COMMENT ON COLUMN tb_weather_snapshot.current IS '현재 날씨 {temp, feelsLike, cond, condText, observedAt}';
COMMENT ON COLUMN tb_weather_snapshot.days IS '일별 예보 [{date, temp, min, max, rainProb, cond, condText}]';
COMMENT ON COLUMN tb_weather_snapshot.raw IS '공급자 원본 응답 (시간별 예보는 빼고 저장)';
COMMENT ON COLUMN tb_weather_snapshot.location_name IS '공급자가 알려 준 위치 이름';
COMMENT ON COLUMN tb_weather_snapshot.fetched_at IS '마지막으로 받기에 성공한 시각. 주기의 3배보다 오래되면 앱에 업데이트 지연을 알린다';
COMMENT ON COLUMN tb_weather_snapshot.last_error IS '마지막 실패 문구 (성공해도 지우지 않는다)';
COMMENT ON COLUMN tb_weather_snapshot.last_error_at IS '마지막 실패 시각';

-- ---------------------------------------------------------------------------
-- 장소별 날씨 (V13, doc/19)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_weather_place_snapshot IS 'REGION 밖 장소의 최신 예보. 요청 시점에 공급자를 불러 격자별로 캐시한다. 실패하면 이전 값은 두고 last_error만 바꾼다';
COMMENT ON COLUMN tb_weather_place_snapshot.grid_key IS '격자 키: 위도·경도를 소수 첫째 자리로 반올림 ("37.6,127.0", 약 11km)';
COMMENT ON COLUMN tb_weather_place_snapshot.lat IS '격자 위도 (반올림한 값)';
COMMENT ON COLUMN tb_weather_place_snapshot.lon IS '격자 경도 (반올림한 값)';
COMMENT ON COLUMN tb_weather_place_snapshot.label IS '공급자가 알려 준 장소 이름';
COMMENT ON COLUMN tb_weather_place_snapshot.provider_code IS '이 값을 준 공급자 (tb_weather_provider)';
COMMENT ON COLUMN tb_weather_place_snapshot.current IS '현재 날씨 {temp, feelsLike, cond, condText, observedAt}';
COMMENT ON COLUMN tb_weather_place_snapshot.days IS '일별 예보 [{date, temp, min, max, rainProb, cond, condText}]';
COMMENT ON COLUMN tb_weather_place_snapshot.fetched_at IS '마지막으로 받기에 성공한 시각';
COMMENT ON COLUMN tb_weather_place_snapshot.last_error IS '마지막 실패 문구';
COMMENT ON COLUMN tb_weather_place_snapshot.last_error_at IS '마지막 실패 시각';

COMMENT ON TABLE tb_weather_climate IS '격자별 월 평년값 (예보 밖 날짜용). 처음 필요할 때 지난 완결 연도(기본 10년)의 일별 자료로 12개월을 한 번에 계산하고, 400일이 지나면 다시 계산한다';
COMMENT ON COLUMN tb_weather_climate.grid_key IS '격자 키 (tb_weather_place_snapshot.grid_key와 같은 규칙)';
COMMENT ON COLUMN tb_weather_climate.month IS '월 1~12';
COMMENT ON COLUMN tb_weather_climate.temp_avg IS '(최고+최저)/2의 평균(섭씨, 소수 둘째 자리)';
COMMENT ON COLUMN tb_weather_climate.temp_min IS '일 최저 기온의 평균(섭씨)';
COMMENT ON COLUMN tb_weather_climate.temp_max IS '일 최고 기온의 평균(섭씨)';
COMMENT ON COLUMN tb_weather_climate.rain_day_ratio IS '강수 1mm 이상인 날의 비율 0~1 (소수 셋째 자리)';
COMMENT ON COLUMN tb_weather_climate.years IS '계산에 쓴 연도 범위 (예: "2016-2025")';
COMMENT ON COLUMN tb_weather_climate.source IS '자료 출처: OPEN_METEO · MOCK';
COMMENT ON COLUMN tb_weather_climate.computed_at IS '계산한 시각';

-- ---------------------------------------------------------------------------
-- 관리자 2단계 인증 (V15, doc/22 4.1)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_user_mfa IS '관리자 2단계 인증 TOTP(RFC 6238) 비밀. 마스터 키(wearly.secret-key)로 AES-256-GCM 암호화한 값만 두고, AAD = "mfa:{user_id}"라 다른 회원 행에 옮기면 복호화되지 않는다';
COMMENT ON COLUMN tb_user_mfa.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_mfa.secret_cipher IS 'TOTP 비밀 암호문 + GCM 태그(16바이트)';
COMMENT ON COLUMN tb_user_mfa.secret_nonce IS '저장할 때마다 새로 만드는 무작위 12바이트';
COMMENT ON COLUMN tb_user_mfa.key_version IS '마스터 키 버전 (지금 1)';
COMMENT ON COLUMN tb_user_mfa.last_used_step IS '마지막으로 받은 30초 단계 (같은 코드 재사용 막기)';
COMMENT ON COLUMN tb_user_mfa.enrolled_at IS '등록한 시각';

COMMENT ON TABLE tb_user_mfa_recovery_code IS '2단계 인증 복구 코드 (등록할 때 10개를 한 번만 보여 준다). 원문은 두지 않는다';
COMMENT ON COLUMN tb_user_mfa_recovery_code.recovery_code_id IS '복구 코드 ID';
COMMENT ON COLUMN tb_user_mfa_recovery_code.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_mfa_recovery_code.code_hash IS 'SHA-256 hex("{user_id}:{정규화한 코드}")';
COMMENT ON COLUMN tb_user_mfa_recovery_code.used_at IS '쓴 시각. 쓴 코드는 다시 쓸 수 없다';
COMMENT ON COLUMN tb_user_mfa_recovery_code.created_at IS '만든 시각';

-- ---------------------------------------------------------------------------
-- 푸시 알림 (V17, doc/23)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_push_token IS '앱이 등록한 Expo 푸시 토큰. 회원·기기당 하나, 같은 토큰은 한 행만 (다른 계정으로 로그인하면 옮긴다). 로그아웃하면 앱이, DeviceNotRegistered면 서버가 지운다';
COMMENT ON COLUMN tb_push_token.push_token_id IS '푸시 토큰 ID';
COMMENT ON COLUMN tb_push_token.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_push_token.device_id IS '기기 ID (회원 안에서 유일)';
COMMENT ON COLUMN tb_push_token.platform IS '플랫폼: IOS · ANDROID';
COMMENT ON COLUMN tb_push_token.token IS 'Expo 푸시 토큰 (중복 불가)';
COMMENT ON COLUMN tb_push_token.created_at IS '등록한 시각';
COMMENT ON COLUMN tb_push_token.updated_at IS '마지막으로 고친 시각';

COMMENT ON TABLE tb_push_send_log IS 'D-1 알림 발송 기록. 같은 예정은 하루(한국 날짜)에 한 번만 보낸다. 보내기 전에 행을 먼저 넣어 서버가 여러 대여도 한 번만 보낸다. 30일이 지나면 지운다';
COMMENT ON COLUMN tb_push_send_log.push_send_log_id IS '발송 기록 ID';
COMMENT ON COLUMN tb_push_send_log.planned_outfit_id IS '알린 예정 (tb_planned_outfit)';
COMMENT ON COLUMN tb_push_send_log.user_id IS '받은 회원 (tb_user)';
COMMENT ON COLUMN tb_push_send_log.notify_kind IS '알림 종류: DAY_BEFORE';
COMMENT ON COLUMN tb_push_send_log.planned_date IS '예정 날짜';
COMMENT ON COLUMN tb_push_send_log.send_date IS '보낸 날짜 (한국 날짜, 하루 한 번 기준)';
COMMENT ON COLUMN tb_push_send_log.created_at IS '보낸 시각';

-- ---------------------------------------------------------------------------
-- 관리자 SSO (V22, doc/22 4.2)
-- ---------------------------------------------------------------------------
COMMENT ON TABLE tb_user_sso IS '관리자 사이트 SSO(범용 OIDC) 계정 연결. IdP 계정은 발급자(iss) + subject(sub)로 정한다. 고객 소셜 로그인(tb_user_social)과 섞지 않는다 (doc/22)';
COMMENT ON COLUMN tb_user_sso.user_sso_id IS 'SSO 연결 ID';
COMMENT ON COLUMN tb_user_sso.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_sso.provider IS '연결할 때의 설정 공급자 ID (wearly.sso.providers.<id>). 표시·기록용';
COMMENT ON COLUMN tb_user_sso.issuer IS 'ID 토큰 iss (설정의 issuer-uri와 같은 값)';
COMMENT ON COLUMN tb_user_sso.subject IS 'ID 토큰 sub (그 발급자 안에서 바뀌지 않는 계정 ID, issuer와 함께 중복 불가)';
COMMENT ON COLUMN tb_user_sso.email IS '마지막 로그인 때의 이메일 (참고용, 소문자)';
COMMENT ON COLUMN tb_user_sso.connected_at IS '연결한 시각';
COMMENT ON COLUMN tb_user_sso.last_login_at IS '마지막 SSO 로그인 시각';
