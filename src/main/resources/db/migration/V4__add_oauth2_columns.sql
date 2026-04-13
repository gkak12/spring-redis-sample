-- OAuth2 로그인 지원을 위한 컬럼 추가 (멱등성 보장 - 재실행 안전)

-- 1. password를 nullable로 변경 (OAuth2 사용자는 비밀번호 없음)
ALTER TABLE users MODIFY password VARCHAR(255) NULL;

-- 2. 로그인 제공자 추가 (기존 사용자는 LOCAL로 기본값 설정)
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider ENUM('LOCAL', 'GOOGLE', 'KAKAO', 'NAVER') NOT NULL DEFAULT 'LOCAL' AFTER email;

-- 3. OAuth2 제공자의 고유 사용자 ID 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255) NULL AFTER provider;

-- 4. provider + provider_id 조합 유니크 제약 (이미 존재하면 삭제 후 재생성)
ALTER TABLE users DROP INDEX IF EXISTS uq_users_provider_provider_id;
ALTER TABLE users ADD CONSTRAINT uq_users_provider_provider_id UNIQUE (provider, provider_id);
