-- OAuth2 로그인 지원을 위한 컬럼 추가

-- 1. password를 nullable로 변경 (OAuth2 사용자는 비밀번호 없음)
ALTER TABLE users MODIFY password VARCHAR(255) NULL;

-- 2. 로그인 제공자 추가 (기존 사용자는 LOCAL로 기본값 설정)
ALTER TABLE users ADD COLUMN provider ENUM('LOCAL', 'GOOGLE', 'KAKAO', 'NAVER') NOT NULL DEFAULT 'LOCAL' AFTER email;

-- 3. OAuth2 제공자의 고유 사용자 ID 추가
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255) NULL AFTER provider;

-- 4. provider + provider_id 조합 유니크 제약 (동일 제공자에서 중복 가입 방지)
ALTER TABLE users ADD CONSTRAINT uq_users_provider_provider_id UNIQUE (provider, provider_id);
