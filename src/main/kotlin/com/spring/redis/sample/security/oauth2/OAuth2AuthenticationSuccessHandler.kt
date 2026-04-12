package com.spring.redis.sample.security.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import com.spring.redis.sample.dto.auth.TokenResponse
import com.spring.redis.sample.repository.UserRepository
import com.spring.redis.sample.security.JwtTokenProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * OAuth2 로그인 성공 시 JWT 발급
 *
 * ID/PW 로그인(AuthService.login)과 동일하게
 * Access Token + Refresh Token을 발급하고 Refresh Token을 Redis에 저장
 */
@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as OAuth2User

        // 제공자별 email 추출 (Google: "email", Kakao: kakao_account.email)
        val email = oAuth2User.getAttribute<String>("email")
            ?: extractKakaoEmail(oAuth2User)
            ?: throw IllegalStateException("OAuth2 사용자 이메일을 확인할 수 없습니다.")

        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalStateException("사용자를 찾을 수 없습니다: $email") }

        val tokenResponse = issueTokens(user.username, "ROLE_${user.role.name}")

        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(objectMapper.writeValueAsString(tokenResponse))
    }

    private fun issueTokens(username: String, role: String): TokenResponse {
        val roles = listOf(role)
        val accessToken = jwtTokenProvider.createAccessToken(username, roles)
        val refreshToken = jwtTokenProvider.createRefreshToken(username)

        val ttlSeconds = (jwtTokenProvider.getExpiration(refreshToken).time - System.currentTimeMillis()) / 1000
        redisTemplate.opsForValue().set("refresh:token:$username", refreshToken, ttlSeconds, TimeUnit.SECONDS)

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractKakaoEmail(oAuth2User: OAuth2User): String? {
        val kakaoAccount = oAuth2User.getAttribute<Map<String, Any>>("kakao_account") ?: return null
        return kakaoAccount["email"] as? String
    }
}
