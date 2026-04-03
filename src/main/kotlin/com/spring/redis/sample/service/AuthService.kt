package com.spring.redis.sample.service

import com.spring.redis.sample.dto.auth.LoginRequest
import com.spring.redis.sample.dto.auth.SignUpRequest
import com.spring.redis.sample.dto.auth.TokenResponse
import com.spring.redis.sample.entity.User

interface AuthService {
    fun signUp(request: SignUpRequest): User
    fun login(request: LoginRequest): TokenResponse
    fun refresh(refreshToken: String): TokenResponse
    fun logout(username: String)
}
