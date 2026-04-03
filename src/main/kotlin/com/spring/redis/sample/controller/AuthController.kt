package com.spring.redis.sample.controller

import com.spring.redis.sample.dto.auth.LoginRequest
import com.spring.redis.sample.dto.auth.RefreshTokenRequest
import com.spring.redis.sample.dto.auth.SignUpRequest
import com.spring.redis.sample.dto.auth.TokenResponse
import com.spring.redis.sample.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    /**
     * 회원가입
     * POST /api/auth/signup
     */
    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<Map<String, String>> {
        authService.signUp(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapOf("message" to "회원가입이 완료되었습니다."))
    }

    /**
     * 로그인 - Access Token + Refresh Token 발급
     * POST /api/auth/login
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(authService.login(request))

    /**
     * Access Token 재발급
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(authService.refresh(request.refreshToken))

    /**
     * 로그아웃 - Redis에서 Refresh Token 삭제
     * POST /api/auth/logout (인증 필요)
     */
    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<Map<String, String>> {
        authService.logout(authentication.name)
        return ResponseEntity.ok(mapOf("message" to "로그아웃되었습니다."))
    }
}
