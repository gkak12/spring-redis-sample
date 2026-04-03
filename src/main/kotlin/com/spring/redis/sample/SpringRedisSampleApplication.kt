package com.spring.redis.sample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SpringRedisSampleApplication

fun main(args: Array<String>) {
    runApplication<SpringRedisSampleApplication>(*args)
}
