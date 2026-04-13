package com.spring.redis.sample.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod

@ExtendWith(MockitoExtension::class)
@DisplayName("RateLimitInterceptor")
class RateLimitInterceptorTest {

    @Mock private lateinit var rateLimiter: RateLimiter
    @Mock private lateinit var handlerMethod: HandlerMethod

    private val objectMapper = ObjectMapper()
    private lateinit var interceptor: RateLimitInterceptor
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        interceptor = RateLimitInterceptor(rateLimiter, objectMapper)
        request  = MockHttpServletRequest()
        response = MockHttpServletResponse()
        request.method = "POST"
        request.requestURI = "/api/auth/login"
        request.remoteAddr = "192.168.0.1"
    }

    @Test
    @DisplayName("handler가 HandlerMethod가 아니면 Rate Limit 없이 통과한다")
    fun `should pass through when handler is not HandlerMethod`() {
        val result = interceptor.preHandle(request, response, "not-a-handler-method")

        assertThat(result).isTrue()
        verify(rateLimiter, never()).isAllowed(any(), any(), any(), any())
    }

    @Test
    @DisplayName("@RateLimit 애노테이션이 없으면 Rate Limit 없이 통과한다")
    fun `should pass through when no RateLimit annotation`() {
        whenever(handlerMethod.getMethodAnnotation(RateLimit::class.java)).thenReturn(null)
        whenever(handlerMethod.beanType).thenReturn(Any::class.java)

        val result = interceptor.preHandle(request, response, handlerMethod)

        assertThat(result).isTrue()
        verify(rateLimiter, never()).isAllowed(any(), any(), any(), any())
    }

    @Nested
    @DisplayName("@RateLimit 애노테이션 존재 시")
    inner class WithRateLimitAnnotation {

        private val rateLimit = createRateLimit(limit = 5, windowSeconds = 60L)

        @BeforeEach
        fun setUpAnnotation() {
            whenever(handlerMethod.getMethodAnnotation(RateLimit::class.java)).thenReturn(rateLimit)
        }

        @Test
        @DisplayName("허용 시 true를 반환하고 Rate Limit 헤더를 설정한다")
        fun `should return true and set headers when allowed`() {
            whenever(rateLimiter.isAllowed(any(), any(), eq(5), eq(60L)))
                .thenReturn(RateLimitResult(allowed = true, current = 3, limit = 5, windowSeconds = 60L))

            val result = interceptor.preHandle(request, response, handlerMethod)

            assertThat(result).isTrue()
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5")
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("2")
            assertThat(response.getHeader("X-RateLimit-Window")).isEqualTo("60s")
        }

        @Test
        @DisplayName("차단 시 false를 반환하고 429 응답을 반환한다")
        fun `should return false and respond 429 when rejected`() {
            whenever(rateLimiter.isAllowed(any(), any(), eq(5), eq(60L)))
                .thenReturn(RateLimitResult(allowed = false, current = 5, limit = 5, windowSeconds = 60L))

            val result = interceptor.preHandle(request, response, handlerMethod)

            assertThat(result).isFalse()
            assertThat(response.status).isEqualTo(429)
            assertThat(response.contentType).contains("application/json")

            val body = objectMapper.readValue(response.contentAsString, Map::class.java)
            assertThat(body["status"]).isEqualTo(429)
            assertThat(body["error"]).isEqualTo("Too Many Requests")
        }

        @Test
        @DisplayName("IP를 식별자로 사용해 Rate Limit을 적용한다")
        fun `should use IP address as identifier`() {
            request.remoteAddr = "10.0.0.1"
            whenever(rateLimiter.isAllowed(eq("10.0.0.1"), any(), any(), any()))
                .thenReturn(RateLimitResult(allowed = true, current = 1, limit = 5, windowSeconds = 60L))

            interceptor.preHandle(request, response, handlerMethod)

            verify(rateLimiter).isAllowed(eq("10.0.0.1"), any(), any(), any())
        }

        @Test
        @DisplayName("X-Forwarded-For 헤더가 있으면 프록시 뒤 실제 IP를 식별자로 사용한다")
        fun `should use X-Forwarded-For header as identifier when present`() {
            request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
            whenever(rateLimiter.isAllowed(eq("203.0.113.5"), any(), any(), any()))
                .thenReturn(RateLimitResult(allowed = true, current = 1, limit = 5, windowSeconds = 60L))

            interceptor.preHandle(request, response, handlerMethod)

            // X-Forwarded-For의 첫 번째 IP(실제 클라이언트)를 식별자로 사용
            verify(rateLimiter).isAllowed(eq("203.0.113.5"), any(), any(), any())
        }

        @Test
        @DisplayName("엔드포인트 식별자는 'METHOD:URI' 형태로 구성된다")
        fun `should compose endpoint identifier as METHOD colon URI`() {
            request.method = "POST"
            request.requestURI = "/api/auth/login"
            whenever(rateLimiter.isAllowed(any(), eq("POST:/api/auth/login"), any(), any()))
                .thenReturn(RateLimitResult(allowed = true, current = 1, limit = 5, windowSeconds = 60L))

            interceptor.preHandle(request, response, handlerMethod)

            verify(rateLimiter).isAllowed(any(), eq("POST:/api/auth/login"), any(), any())
        }
    }

    /**
     * 테스트용 @RateLimit 인스턴스 생성
     * 코틀린에서 애노테이션 인스턴스를 직접 생성하기 위해 익명 구현체 사용
     */
    private fun createRateLimit(limit: Int, windowSeconds: Long): RateLimit {
        return object : RateLimit {
            override val limit: Int get() = limit
            override val windowSeconds: Long get() = windowSeconds
            override fun annotationType() = RateLimit::class.java
        }
    }
}
