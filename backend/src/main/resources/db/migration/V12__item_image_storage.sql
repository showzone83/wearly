-- 옷 사진 서버 저장 (계약: doc/18, 기획서 p.28 TB_ITEM_IMAGE · p.30 · p.31)
-- 사진 파일은 S3 호환 오브젝트 스토리지(로컬 MinIO 버킷 wearly-images, 비공개)에 두고 DB에는 메타데이터만 둔다.
-- V1의 tb_item_image(이미지 URL, 테이블만 있고 쓰지 않음)를 지우고 이 용도에 맞게 다시 만든다.
DROP TABLE IF EXISTS tb_item_image;

CREATE TABLE tb_item_image (
    image_id      UUID        PRIMARY KEY,                -- 서버가 만든 무작위 UUID (API의 imageId)
    user_id       BIGINT      NOT NULL REFERENCES tb_user (user_id) ON DELETE CASCADE,
    -- 붙은 아이템. 올리기만 하고 아직 안 붙였거나 지운(DELETED) 이미지는 NULL.
    -- 아이템 완전 삭제는 서비스가 먼저 이미지를 DELETED로 바꾸므로 SET NULL은 안전장치다
    item_id       BIGINT      REFERENCES tb_wardrobe_item (item_id) ON DELETE SET NULL,
    purpose       VARCHAR(10) NOT NULL DEFAULT 'ITEM',
    status        VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    -- PENDING: 업로드 URL을 받을 때 선언한 형식, READY: 저장한 original의 형식(image/jpeg)
    content_type  VARCHAR(30) NOT NULL,
    declared_size BIGINT      NOT NULL,                   -- 업로드 URL을 받을 때 선언한 크기 (바이트)
    size_bytes    BIGINT,                                 -- 저장한 original.jpg 크기 (READY부터)
    width         INTEGER,                                -- original.jpg 가로·세로 (READY부터)
    height        INTEGER,
    upload_key    TEXT        NOT NULL,                   -- uploads/{userId}/{imageId} (처리 후 지운다)
    object_key    TEXT,                                   -- users/{userId}/items/{imageId}/original.jpg
    thumb_key     TEXT,                                   -- users/{userId}/items/{imageId}/thumb.jpg
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at      TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT ck_item_image_purpose CHECK (purpose IN ('ITEM')),
    CONSTRAINT ck_item_image_status CHECK (status IN ('PENDING', 'READY', 'DELETED')),
    -- READY면 저장 정보가 모두 있다
    CONSTRAINT ck_item_image_ready CHECK (
        status <> 'READY'
        OR (object_key IS NOT NULL AND thumb_key IS NOT NULL AND size_bytes IS NOT NULL
            AND width IS NOT NULL AND height IS NOT NULL AND ready_at IS NOT NULL)
    ),
    -- 아이템에 붙을 수 있는 것은 READY뿐
    CONSTRAINT ck_item_image_item CHECK (item_id IS NULL OR status = 'READY')
);

-- 한 아이템에 대표 사진 하나 (지금은 하나만)
CREATE UNIQUE INDEX ux_item_image_item ON tb_item_image (item_id) WHERE item_id IS NOT NULL;
-- 하루 업로드 한도 집계 (회원별 created_at 구간)
CREATE INDEX ix_item_image_user_created ON tb_item_image (user_id, created_at);
-- 정리 작업: 오래된 PENDING·DELETED, 아이템에 안 붙은 READY
CREATE INDEX ix_item_image_cleanup ON tb_item_image (status, created_at) WHERE item_id IS NULL;
