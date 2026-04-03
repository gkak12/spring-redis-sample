package com.spring.redis.sample.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        resolveToken(request)
            ?.takeIf { jwtTokenProvider.validateToken(it) }
            ?.takeIf { jwtTokenProvider.getTokenType(it) == JwtTokenProvider.TOKEN_TYPE_ACCESS }
            ?.let { token ->
                val username = jwtTokenProvider.getUsernameFromToken(token)
                val authorities = jwtTokenProvider.getRolesFromToken(token).map { SimpleGrantedAuthority(it) }
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(username, null, authorities)
            }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }
}
