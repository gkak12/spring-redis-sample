package com.spring.redis.sample.config

import com.spring.redis.sample.security.JwtAuthenticationFilter
import com.spring.redis.sample.security.oauth2.CustomOAuth2UserService
import com.spring.redis.sample.security.oauth2.OAuth2AuthenticationFailureHandler
import com.spring.redis.sample.security.oauth2.OAuth2AuthenticationSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2AuthenticationSuccessHandler: OAuth2AuthenticationSuccessHandler,
    private val oAuth2AuthenticationFailureHandler: OAuth2AuthenticationFailureHandler
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/search/trending",
                        "/api/stores/nearby",
                        "/api/stores/nearby/geo",
                        "/login/oauth2/**",       // OAuth2 콜백 URL
                        "/oauth2/authorization/**" // OAuth2 인가 요청 URL
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            // ID/PW 로그인 - JWT 필터로 처리
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // OAuth2 로그인
            .oauth2Login { oauth2 ->
                oauth2
                    // 사용자 정보 로드 + DB 저장 + Redis Hash 캐싱
                    .userInfoEndpoint { it.userService(customOAuth2UserService) }
                    // 로그인 성공 시 JWT 발급
                    .successHandler(oAuth2AuthenticationSuccessHandler)
                    // 로그인 실패 시 에러 응답
                    .failureHandler(oAuth2AuthenticationFailureHandler)
            }

        return http.build()
    }
}
