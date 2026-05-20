package com.back.together02be.common.loadtest

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * loadtest 프로파일 전용 캐시 통계 로거.
 * 30초마다 hit/miss/hitRatio/evictionCount를 로그로 출력.
 *
 * 테스트 종료 후 로그에서 시계열로 추적 가능.
 * /actuator/caches 엔드포인트로도 동일 수치 확인 가능.
 */
@Component
@Profile("loadtest")
class CacheStatsLogger(
    private val cacheManager: CacheManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 30_000)
    fun logStats() {
        cacheManager.cacheNames.forEach { cacheName ->
            val cache = cacheManager.getCache(cacheName)
            if (cache is CaffeineCache) {
                val stats = cache.nativeCache.stats()
                val hitRatioFormatted = "%.2f".format(stats.hitRate())
                log.info(
                    "[CACHE-STATS] name={} | hit={} | miss={} | hitRatio={} | eviction={} | loadCount={}",
                    cacheName,
                    stats.hitCount(),
                    stats.missCount(),
                    hitRatioFormatted,
                    stats.evictionCount(),
                    stats.loadCount(),
                )
            }
        }
    }
}