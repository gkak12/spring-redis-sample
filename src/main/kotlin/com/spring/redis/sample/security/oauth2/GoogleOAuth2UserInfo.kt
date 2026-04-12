package com.spring.redis.sample.security.oauth2

/**
 * Google OAuth2 사용자 정보
 *
 * Google userinfo 응답:
 * {
 *   "sub": "1234567890",   ← 고유 ID
 *   "email": "user@gmail.com",
 *   "name": "홍길동"
 * }
 */
class GoogleOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    override fun getProviderId(): String = attributes["sub"].toString()

    override fun getEmail(): String = attributes["email"].toString()

    override fun getName(): String = attributes["name"].toString()
}
