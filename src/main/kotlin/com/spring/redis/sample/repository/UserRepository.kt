package com.spring.redis.sample.repository

import com.spring.redis.sample.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>, UserRepositoryDsl
