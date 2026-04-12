package com.spring.redis.sample.security.oauth2

import com.spring.redis.sample.entity.AuthProvider

object OAuth2UserInfoFactory {

    fun getOAuth2UserInfo(
        provider: AuthProvider,
        attributes: Map<String, Any>
    ): OAuth2UserInfo = when (provider) {
        AuthProvider.GOOGLE -> GoogleOAuth2UserInfo(attributes)
        AuthProvider.KAKAO  -> KakaoOAuth2UserInfo(attributes)
        AuthProvider.NAVER  -> NaverOAuth2UserInfo(attributes)
        AuthProvider.LOCAL  -> throw IllegalArgumentException("LOCAL 제공자는 OAuth2 로그인을 지원하지 않습니다.")
    }
}
