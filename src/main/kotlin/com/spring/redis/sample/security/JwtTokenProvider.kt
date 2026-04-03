package com.spring.redis.sample.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secretKey: String,
    @Value("\${jwt.access-token-expiration}") private val accessTokenExpiration: Long,
    @Value("\${jwt.refresh-token-expiration}") private val refreshTokenExpiration: Long
) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }

    fun createAccessToken(username: String, roles: List<String>): String =
        createToken(username, roles, accessTokenExpiration, TOKEN_TYPE_ACCESS)

    fun createRefreshToken(username: String): String =
        createToken(username, emptyList(), refreshTokenExpiration, TOKEN_TYPE_REFRESH)

    private fun createToken(username: String, roles: List<String>, expiration: Long, tokenType: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .claim("type", tokenType)
            .issuedAt(now)
            .expiration(Date(now.time + expiration))
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean =
        runCatching { getClaims(token) }.isSuccess

    fun getUsernameFromToken(token: String): String =
        getClaims(token).subject

    fun getTokenType(token: String): String =
        getClaims(token)["type"] as String

    fun getExpiration(token: String): Date =
        getClaims(token).expiration

    @Suppress("UNCHECKED_CAST")
    fun getRolesFromToken(token: String): List<String> =
        getClaims(token)["roles"] as? List<String> ?: emptyList()

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

    companion object {
        const val TOKEN_TYPE_ACCESS = "access"
        const val TOKEN_TYPE_REFRESH = "refresh"
    }
}
