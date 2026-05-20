package com.back.together02be.chart.scheduler

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(ChartCacheScheduler::class.java)

@Component
class ChartCacheScheduler (
    private val cacheManager: CacheManager
){
    /*
		15:30 장 마감 -> 당일 캔들 확정
		이때 캐시 비워야 다음 요청에 최신 데이터 반영

		토/일은 장이 없으니 스케줄러 돌릴 필요 없음
		월 ~ 금만 실행
	*/

    // @Scheduled(cron = "0 * * * * *") 테스트
    @Scheduled(cron = "0 30 15 * * MON-FRI") // 평일 15:30
    fun clearChartCache() {
        cacheManager.getCache("chart")!!.clear()
        log.info("차트 캐시 장 마감 캐시 초기화 완료")
    }
}
