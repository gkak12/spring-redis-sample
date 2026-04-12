package com.spring.redis.sample.security.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuth2AuthenticationFailureHandler(
    private val objectMapper: ObjectMapper
) : SimpleUrlAuthenticationFailureHandler() {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(
            objectMapper.writeValueAsString(
                mapOf(
                    "error" to "OAuth2 로그인에 실패했습니다.",
                    "message" to (exception.message ?: "알 수 없는 오류")
                )
            )
        )
    }
}
