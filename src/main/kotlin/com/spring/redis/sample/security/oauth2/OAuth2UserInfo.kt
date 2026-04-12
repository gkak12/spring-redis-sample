package com.spring.redis.sample.security.oauth2

/**
 * OAuth2 제공자별 사용자 정보 추상화
 * Google / Kakao 각각 응답 구조가 다르므로 인터페이스로 통일
 */
interface OAuth2UserInfo {
    fun getProviderId(): String
    fun getEmail(): String
    fun getName(): String
}
