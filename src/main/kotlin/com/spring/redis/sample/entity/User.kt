package com.spring.redis.sample.entity

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 50)
    var username: String,

    // OAuth2 로그인 사용자는 비밀번호가 없으므로 nullable
    @Column(nullable = true)
    var password: String? = null,

    @Column(nullable = false, unique = true, length = 100)
    var email: String,

    // 로그인 제공자 (LOCAL: ID/PW, GOOGLE/KAKAO/NAVER: OAuth2)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: AuthProvider = AuthProvider.LOCAL,

    // OAuth2 제공자가 발급한 고유 ID (LOCAL 사용자는 null)
    @Column(nullable = true, length = 255)
    var providerId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.USER
)

enum class UserRole {
    USER, ADMIN
}

enum class AuthProvider {
    LOCAL, GOOGLE, KAKAO, NAVER
}
