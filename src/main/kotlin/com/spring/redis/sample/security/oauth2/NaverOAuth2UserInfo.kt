package com.spring.redis.sample.security.oauth2

/**
 * Naver OAuth2 사용자 정보
 *
 * Naver userinfo 응답 (response 래퍼 구조):
 * {
 *   "resultcode": "00",
 *   "message": "success",
 *   "response": {
 *     "id": "abcdefg1234567",   ← 고유 ID
 *     "email": "user@naver.com",
 *     "name": "홍길동"
 *   }
 * }
 */
class NaverOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    // Naver는 실제 사용자 정보가 "response" 키 안에 중첩
    @Suppress("UNCHECKED_CAST")
    private val response: Map<String, Any> =
        attributes["response"] as? Map<String, Any> ?: emptyMap()

    override fun getProviderId(): String = response["id"].toString()

    override fun getEmail(): String = response["email"]?.toString() ?: ""

    override fun getName(): String = response["name"]?.toString() ?: ""
}
