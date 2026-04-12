package com.spring.redis.sample.ratelimit

/**
 * API Rate Limiting 애노테이션
 *
 * 클래스 또는 메서드에 선언 가능
 * 메서드 선언이 클래스 선언보다 우선 적용됨
 *
 * 사용 예:
 *   @RateLimit(limit = 5, windowSeconds = 60)  → 1분에 5회
 *   @RateLimit(limit = 100, windowSeconds = 1)  → 1초에 100회
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    val limit: Int = 60,          // 허용 요청 수
    val windowSeconds: Long = 60  // 시간 윈도우 (초)
)
