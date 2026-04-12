package com.spring.redis.sample.security.oauth2

import com.spring.redis.sample.entity.AuthProvider
import com.spring.redis.sample.entity.User
import com.spring.redis.sample.entity.UserRole
import com.spring.redis.sample.repository.UserRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) : DefaultOAuth2UserService() {

    companion object {
        private const val PROFILE_TTL_MINUTES = 30L
    }

    /**
     * OAuth2 로그인 처리 흐름:
     * 1. OAuth2 제공자(Google/Kakao)에서 사용자 정보 조회
     * 2. DB에서 기존 사용자 조회 → 없으면 신규 생성, 있으면 정보 업데이트
     * 3. Redis Hash에 사용자 프로필 캐싱 (이후 프로필 조회 시 DB 접근 최소화)
     */
    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)

        val provider = AuthProvider.valueOf(
            userRequest.clientRegistration.registrationId.uppercase()
        )
        val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(provider, oAuth2User.attributes)
        val user = findOrCreateUser(userInfo, provider)

        // Redis Hash에 사용자 프로필 캐싱
        // Hash 활용: 필드 단위로 저장 → 역할 변경 등 일부 필드만 갱신 가능
        cacheUserProfile(user)

        val nameAttributeKey = userRequest.clientRegistration
            .providerDetails.userInfoEndpoint.userNameAttributeName

        return DefaultOAuth2User(
            setOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
            oAuth2User.attributes,
            nameAttributeKey
        )
    }

    /**
     * 기존 사용자 조회 또는 신규 생성
     *
     * 조회 우선순위:
     * 1. provider + providerId로 조회 (같은 제공자로 재로그인)
     * 2. email로 조회 (다른 제공자로 가입한 동일 이메일 → 계정 연동)
     * 3. 신규 생성
     */
    private fun findOrCreateUser(userInfo: OAuth2UserInfo, provider: AuthProvider): User {
        return userRepository.findByProviderAndProviderId(provider, userInfo.getProviderId())
            .orElseGet {
                userRepository.findByEmail(userInfo.getEmail())
                    .map { existingUser ->
                        // 기존 계정에 OAuth2 제공자 정보 연동
                        existingUser.provider = provider
                        existingUser.providerId = userInfo.getProviderId()
                        userRepository.save(existingUser)
                    }
                    .orElseGet {
                        // 신규 사용자 생성 (password null → OAuth2 전용 계정)
                        userRepository.save(
                            User(
                                username = generateUsername(userInfo, provider),
                                email = userInfo.getEmail(),
                                provider = provider,
                                providerId = userInfo.getProviderId(),
                                role = UserRole.USER
                            )
                        )
                    }
            }
    }

    /**
     * Redis Hash에 사용자 프로필 캐싱
     *
     * Key:    user:profile:{username}
     * Fields: email, role, provider, name
     * TTL:    30분
     *
     * Hash를 사용하는 이유:
     * - String(JSON): 역할 하나 바꿀 때 전체 JSON 덮어써야 함
     * - Hash: HSET으로 변경된 필드만 업데이트 가능
     */
    private fun cacheUserProfile(user: User) {
        val profileKey = "user:profile:${user.username}"

        redisTemplate.opsForHash<String, String>().putAll(
            profileKey,
            mapOf(
                "email"    to user.email,
                "role"     to user.role.name,
                "provider" to user.provider.name,
                "name"     to user.username
            )
        )
        redisTemplate.expire(profileKey, PROFILE_TTL_MINUTES, TimeUnit.MINUTES)
    }

    private fun generateUsername(userInfo: OAuth2UserInfo, provider: AuthProvider): String {
        // 제공자_이름 형태로 유저네임 생성, 중복 시 providerId 뒤에 붙임
        val base = "${provider.name.lowercase()}_${userInfo.getName()}"
        return if (userRepository.existsByUsername(base)) "${base}_${userInfo.getProviderId().takeLast(6)}"
        else base
    }
}
