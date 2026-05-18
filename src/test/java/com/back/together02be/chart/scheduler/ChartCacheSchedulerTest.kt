package com.back.together02be.chart.scheduler

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

@ExtendWith(MockitoExtension::class)
@DisplayName("ChartCacheScheduler 단위 테스트")
class ChartCacheSchedulerTest {
    @Mock
    private lateinit var cacheManager: CacheManager

    @Mock
    private lateinit var cache: Cache

    private lateinit var scheduler: ChartCacheScheduler

    @BeforeEach
    fun setUp() {
        scheduler = ChartCacheScheduler(cacheManager)
    }

    @Test
    @DisplayName("clearChartCache 호출 시 chart 캐시가 초기화된다")
    fun `캐시 초기화 호출`() {
        given(cacheManager.getCache("chart")).willReturn(cache)

        scheduler.clearChartCache()

        verify(cacheManager, times(1)).getCache("chart")
        verify(cache, times(1)).clear()
    }

    @Test
    @DisplayName("chart 캐시가 null 이면 NPE 가 발생한다")
    fun `캐시 null이면 예외 발생`() {
        given(cacheManager.getCache("chart")).willReturn(null)

        assertThrows<NullPointerException> {
            scheduler.clearChartCache()
        }
    }
}