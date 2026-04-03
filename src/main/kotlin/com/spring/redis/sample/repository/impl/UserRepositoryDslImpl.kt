package com.spring.redis.sample.repository.impl

import com.querydsl.jpa.impl.JPAQueryFactory
import com.spring.redis.sample.entity.QUser
import com.spring.redis.sample.entity.User
import com.spring.redis.sample.repository.UserRepositoryDsl
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class UserRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory
) : UserRepositoryDsl {

    override fun findByUsername(username: String): Optional<User> {
        val user = QUser.user

        return Optional.ofNullable(
            queryFactory
                .selectFrom(user)
                .where(user.username.eq(username))
                .fetchOne()
        )
    }

    override fun existsByUsername(username: String): Boolean {
        val user = QUser.user

        return queryFactory
            .selectFrom(user)
            .where(user.username.eq(username))
            .fetchFirst() != null
    }

    override fun existsByEmail(email: String): Boolean {
        val user = QUser.user

        return queryFactory
            .selectFrom(user)
            .where(user.email.eq(email))
            .fetchFirst() != null
    }
}
