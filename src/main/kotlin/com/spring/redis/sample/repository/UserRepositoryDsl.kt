package com.spring.redis.sample.repository

import com.spring.redis.sample.entity.User
import java.util.Optional

interface UserRepositoryDsl {
    fun findByUsername(username: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
