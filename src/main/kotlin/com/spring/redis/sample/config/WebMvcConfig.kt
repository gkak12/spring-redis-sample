package com.spring.redis.sample.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.spring.redis.sample.ratelimit.RateLimitInterceptor
import com.spring.redis.sample.ratelimit.RateLimiter
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val rateLimiter: RateLimiter,
    private val objectMapper: ObjectMapper
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(RateLimitInterceptor(rateLimiter, objectMapper))
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/refresh"  // 토큰 재발급은 Rate Limit 제외 (만료 직후 정상 재발급 방해 방지)
            )
    }
}
