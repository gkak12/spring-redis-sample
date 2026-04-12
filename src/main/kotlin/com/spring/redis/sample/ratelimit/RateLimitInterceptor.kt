package com.spring.redis.sample.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Rate Limit 인터셉터
 *
 * @RateLimit 애노테이션이 선언된 컨트롤러/메서드에 자동으로 Rate Limiting 적용
 * 메서드 선언 > 클래스 선언 순으로 우선순위 적용
 *
 * 식별자 우선순위:
 *   1. X-Forwarded-For 헤더 (Nginx 등 프록시 뒤에 있을 때 실제 클라이언트 IP)
 *   2. request.remoteAddr (직접 연결 시 IP)
 *
 * 응답 헤더:
 *   X-RateLimit-Limit     : 최대 허용 요청 수
 *   X-RateLimit-Remaining : 남은 요청 수
 *   X-RateLimit-Window    : 시간 윈도우 (초)
 */
class RateLimitInterceptor(
    private val rateLimiter: RateLimiter,
    private val objectMapper: ObjectMapper
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (handler !is HandlerMethod) return true

        // 메서드 선언 우선, 없으면 클래스 선언 확인
        val rateLimit = handler.getMethodAnnotation(RateLimit::class.java)
            ?: handler.beanType.getAnnotation(RateLimit::class.java)
            ?: return true  // @RateLimit 없으면 제한 없이 통과

        val identifier = resolveIdentifier(request)
        val endpoint   = "${request.method}:${request.requestURI}"

        val result = rateLimiter.isAllowed(
            identifier    = identifier,
            endpoint      = endpoint,
            limit         = rateLimit.limit,
            windowSeconds = rateLimit.windowSeconds
        )

        // 표준 Rate Limit 응답 헤더 추가 (클라이언트가 남은 횟수 인지 가능)
        response.setHeader("X-RateLimit-Limit",     result.limit.toString())
        response.setHeader("X-RateLimit-Remaining", result.remaining.toString())
        response.setHeader("X-RateLimit-Window",    "${result.windowSeconds}s")

        if (!result.allowed) {
            log.warn("[RateLimit] 요청 초과: identifier={}, endpoint={}, limit={}/{}s",
                identifier, endpoint, rateLimit.limit, rateLimit.windowSeconds)
            sendTooManyRequestsResponse(response, rateLimit)
            return false
        }

        return true
    }

    /**
     * 클라이언트 식별자 추출
     * 프록시 환경에서는 X-Forwarded-For가 실제 클라이언트 IP를 담고 있음
     */
    private fun resolveIdentifier(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 → 첫 번째가 실제 클라이언트 IP
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }

    private fun sendTooManyRequestsResponse(
        response: HttpServletResponse,
        rateLimit: RateLimit
    ) {
        response.status      = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val body = mapOf(
            "status"  to 429,
            "error"   to "Too Many Requests",
            "message" to "${rateLimit.windowSeconds}초 내 최대 ${rateLimit.limit}회 요청을 초과했습니다. 잠시 후 다시 시도해주세요."
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
