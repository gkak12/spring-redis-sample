package com.spring.redis.sample.repository

import com.spring.redis.sample.entity.AuthProvider
import com.spring.redis.sample.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long>, UserRepositoryDsl {

    fun findByEmail(email: String): Optional<User>

    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): Optional<User>
}
