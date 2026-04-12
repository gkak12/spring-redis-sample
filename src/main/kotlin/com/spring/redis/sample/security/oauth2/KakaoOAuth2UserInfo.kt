package com.spring.redis.sample.security.oauth2

/**
 * Kakao OAuth2 사용자 정보
 *
 * Kakao userinfo 응답 (중첩 구조):
 * {
 *   "id": 1234567890,
 *   "kakao_account": {
 *     "email": "user@kakao.com",
 *     "profile": {
 *       "nickname": "홍길동"
 *     }
 *   }
 * }
 */
class KakaoOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    override fun getProviderId(): String = attributes["id"].toString()

    override fun getEmail(): String {
        @Suppress("UNCHECKED_CAST")
        val kakaoAccount = attributes["kakao_account"] as? Map<String, Any> ?: emptyMap()
        return kakaoAccount["email"]?.toString() ?: ""
    }

    override fun getName(): String {
        @Suppress("UNCHECKED_CAST")
        val kakaoAccount = attributes["kakao_account"] as? Map<String, Any> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val profile = kakaoAccount["profile"] as? Map<String, Any> ?: emptyMap()
        return profile["nickname"]?.toString() ?: ""
    }
}
