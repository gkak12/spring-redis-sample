package com.spring.redis.sample.service.impl

import com.spring.redis.sample.dto.auth.LoginRequest
import com.spring.redis.sample.dto.auth.SignUpRequest
import com.spring.redis.sample.dto.auth.TokenResponse
import com.spring.redis.sample.entity.User
import com.spring.redis.sample.repository.UserRepository
import com.spring.redis.sample.security.JwtTokenProvider
import com.spring.redis.sample.service.AuthService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: RedisTemplate<String, String>
) : AuthService {

    @Transactional
    override fun signUp(request: SignUpRequest): User {
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("이미 사용중인 아이디입니다.")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("이미 사용중인 이메일입니다.")
        }
        return userRepository.save(
            User(
                username = request.username,
                password = passwordEncoder.encode(request.password),
                email = request.email
            )
        )
    }

    override fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByUsername(request.username)
            .orElseThrow { IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.") }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.")
        }

        return issueTokens(user.username, listOf("ROLE_${user.role.name}"))
    }

    override fun refresh(refreshToken: String): TokenResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.")
        }
        if (jwtTokenProvider.getTokenType(refreshToken) != JwtTokenProvider.TOKEN_TYPE_REFRESH) {
            throw IllegalArgumentException("리프레시 토큰이 아닙니다.")
        }

        val username = jwtTokenProvider.getUsernameFromToken(refreshToken)
        val storedToken = redisTemplate.opsForValue().get(refreshTokenKey(username))

        if (storedToken == null || storedToken != refreshToken) {
            throw IllegalArgumentException("만료되었거나 이미 사용된 리프레시 토큰입니다.")
        }

        val user = userRepository.findByUsername(username)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        return issueTokens(username, listOf("ROLE_${user.role.name}"))
    }

    override fun logout(username: String) {
        redisTemplate.delete(refreshTokenKey(username))
    }

    private fun issueTokens(username: String, roles: List<String>): TokenResponse {
        val accessToken = jwtTokenProvider.createAccessToken(username, roles)
        val refreshToken = jwtTokenProvider.createRefreshToken(username)

        val ttlSeconds = (jwtTokenProvider.getExpiration(refreshToken).time - System.currentTimeMillis()) / 1000
        redisTemplate.opsForValue().set(refreshTokenKey(username), refreshToken, ttlSeconds, TimeUnit.SECONDS)

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    private fun refreshTokenKey(username: String) = "refresh:token:$username"
}
