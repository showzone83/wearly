-- 친구 초대·추천인 (doc/25_친구_초대.md, 이슈 #16). invite 컨텍스트.
--  - tb_user_invite_code: 회원별 초대 코드. 회원이 처음 초대 화면을 열 때 만든다 (기존 회원 백필 없음).
--  - tb_invite_share: 초대한 이력. 앱에서 공유 시트·문자·코드 복사를 할 때마다 한 행.
--  - tb_invite_signup: 초대 코드로 가입한 이력. 가입할 때 코드를 넣은 경우만 한 행 (가입자당 하나).
--    없는 코드여도 가입은 막지 않고 result = UNKNOWN_CODE로 남긴다. 추천인 관계의 기준은 result = LINKED인 행이다.
--    추천인 계정이 지워져도 이력은 남는다 (referrer_id SET NULL).
CREATE TABLE tb_user_invite_code (
    user_id    BIGINT      PRIMARY KEY REFERENCES tb_user (user_id) ON DELETE CASCADE,
    code       VARCHAR(8)  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_invite_code UNIQUE (code)
);

CREATE TABLE tb_invite_share (
    invite_share_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    channel         VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_invite_share_channel CHECK (channel IN ('SHARE', 'SMS', 'COPY'))
);
CREATE INDEX ix_invite_share_user ON tb_invite_share (user_id, created_at DESC);
CREATE INDEX ix_invite_share_created ON tb_invite_share (created_at DESC);

CREATE TABLE tb_invite_signup (
    invite_signup_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    referrer_id      BIGINT      REFERENCES tb_user (user_id) ON DELETE SET NULL,
    entered_code     VARCHAR(20) NOT NULL,
    result           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invite_signup_user UNIQUE (user_id),
    CONSTRAINT ck_invite_signup_result CHECK (result IN ('LINKED', 'UNKNOWN_CODE'))
);
CREATE INDEX ix_invite_signup_referrer ON tb_invite_signup (referrer_id, created_at DESC);
CREATE INDEX ix_invite_signup_created ON tb_invite_signup (created_at DESC);

COMMENT ON TABLE tb_user_invite_code IS '회원별 초대 코드. 처음 초대 화면을 열 때 만든다 (doc/25)';
COMMENT ON COLUMN tb_user_invite_code.user_id IS '회원 (tb_user)';
COMMENT ON COLUMN tb_user_invite_code.code IS '초대 코드 8자: 헷갈리는 0·1·I·L·O를 뺀 대문자·숫자. 화면에는 XXXX-XXXX로 보인다';
COMMENT ON COLUMN tb_user_invite_code.created_at IS '코드를 만든 시각';

COMMENT ON TABLE tb_invite_share IS '초대한 이력: 앱에서 공유·문자·코드 복사를 한 기록 (doc/25). Android 공유 시트는 실제로 보냈는지 알 수 없어 연 기록이다';
COMMENT ON COLUMN tb_invite_share.invite_share_id IS '초대 기록 ID';
COMMENT ON COLUMN tb_invite_share.user_id IS '초대한 회원 (tb_user)';
COMMENT ON COLUMN tb_invite_share.channel IS '방법: SHARE(공유 시트, 카카오톡 등) · SMS(문자) · COPY(코드 복사)';
COMMENT ON COLUMN tb_invite_share.created_at IS '기록 시각';

COMMENT ON TABLE tb_invite_signup IS '초대 코드로 가입한 이력. 가입할 때 코드를 넣은 경우만, 가입자당 한 행 (doc/25). 추천인 관계는 result = LINKED인 행';
COMMENT ON COLUMN tb_invite_signup.invite_signup_id IS '가입 이력 ID';
COMMENT ON COLUMN tb_invite_signup.user_id IS '가입한 회원 (tb_user)';
COMMENT ON COLUMN tb_invite_signup.referrer_id IS '추천인 (tb_user). 코드가 없거나 추천인이 탈퇴하면 NULL';
COMMENT ON COLUMN tb_invite_signup.entered_code IS '가입자가 입력한 코드 (공백·하이픈을 빼고 대문자로 정리, 20자까지)';
COMMENT ON COLUMN tb_invite_signup.result IS '결과: LINKED(추천인 연결) · UNKNOWN_CODE(없는 코드, 추천인 없이 가입)';
COMMENT ON COLUMN tb_invite_signup.created_at IS '가입 시각';
