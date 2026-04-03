package com.spring.redis.sample.repository

import com.spring.redis.sample.entity.Store
import org.springframework.data.jpa.repository.JpaRepository

interface StoreRepository : JpaRepository<Store, Long>, StoreRepositoryDsl
