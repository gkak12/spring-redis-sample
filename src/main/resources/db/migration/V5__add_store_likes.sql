-- 매장 좋아요 영구 저장 테이블
-- Redis Set과 1분 주기로 동기화 (Write-Behind 패턴)
CREATE TABLE store_likes (
    store_id  BIGINT       NOT NULL,
    username  VARCHAR(50)  NOT NULL,
    liked_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (store_id, username),
    CONSTRAINT fk_store_likes_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE
);
