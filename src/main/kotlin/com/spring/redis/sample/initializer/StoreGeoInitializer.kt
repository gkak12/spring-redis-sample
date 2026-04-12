package com.spring.redis.sample.initializer

import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.StoreGeoService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 앱 시작 시 DB의 전체 매장 좌표를 Redis Geo에 일괄 적재
 *
 * ApplicationRunner: Spring Context 완전 초기화 후 실행 (DB/Redis 빈 사용 가능)
 *
 * 재시작 안전성:
 * Redis GEOADD는 동일 member가 이미 존재하면 좌표를 덮어씀 (멱등성 보장)
 * → 서버 재시작 시 중복 등록 걱정 없음
 *
 * 운영 고려사항:
 * 매장 수가 수십만 건 이상이면 페이지 단위 배치 처리 또는
 * Redis Pipeline을 사용해 메모리/네트워크 부하를 줄여야 함
 */
@Component
class StoreGeoInitializer(
    private val storeRepository: StoreRepository,
    private val storeGeoService: StoreGeoService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val stores = storeRepository.findAll()

        if (stores.isEmpty()) {
            log.info("Redis Geo 초기화 건너뜀: 등록된 매장 없음")
            return
        }

        stores.forEach { storeGeoService.addStore(it) }
        log.info("Redis Geo 초기화 완료: {}개 매장 등록", stores.size)
    }
}
