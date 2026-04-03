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

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false, unique = true, length = 100)
    var email: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.USER
)

enum class UserRole {
    USER, ADMIN
}
