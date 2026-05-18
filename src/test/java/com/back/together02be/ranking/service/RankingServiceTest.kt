package com.back.together02be.ranking.service

import com.back.together02be.ranking.entity.Ranking
import com.back.together02be.ranking.entity.RankingSnapshotType
import com.back.together02be.ranking.repository.RankingRepository
import com.back.together02be.users.entity.Users
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
@DisplayName("RankingService - 랭킹 조회 로직 테스트")
internal class RankingServiceTest {

    @Mock
    private lateinit var rankingRepository: RankingRepository

    @InjectMocks
    private lateinit var rankingService: RankingService

    @Test
    @DisplayName("일간 랭킹 조회 - 오늘 DAILY 랭킹을 순위순으로 반환한다")
    fun 일간_랭킹_조회_성공() {
        // given
        val today = LocalDate.now()

        val user1 = Users("user1", "password", "투자왕").apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }
        val user2 = Users("user2", "password", "수익왕").apply {
            ReflectionTestUtils.setField(this, "id", 2L)
        }

        val ranking1 = Ranking(user1, 1, BigDecimal("12.34"), 56170000L, RankingSnapshotType.DAILY, today)
        val ranking2 = Ranking(user2, 2, BigDecimal("8.50"), 54250000L, RankingSnapshotType.DAILY, today)

        // 💡 Kotlin x Mockito 해결책: 엘비스 연산자(?:)를 활용해 NPE 방어
        given(
            rankingRepository.findRankings(
                eq(RankingSnapshotType.DAILY) ?: RankingSnapshotType.DAILY,
                eq(today) ?: today,
                any(Sort::class.java) ?: Sort.unsorted()
            )
        ).willReturn(listOf(ranking1, ranking2))

        // when
        val result = rankingService.getDailyRankings()

        // then
        assertThat(result).hasSize(2)

        assertThat(result[0].userId).isEqualTo(1L)
        assertThat(result[0].nickname).isEqualTo("투자왕")
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[0].profitRate).isEqualByComparingTo("12.34")
        assertThat(result[0].totalAsset).isEqualTo(56170000L)

        assertThat(result[1].userId).isEqualTo(2L)
        assertThat(result[1].nickname).isEqualTo("수익왕")
        assertThat(result[1].rank).isEqualTo(2)
    }

    @Test
    @DisplayName("월간 랭킹 조회 - 특정 날짜 MONTHLY 랭킹을 순위순으로 반환한다")
    fun 월간_랭킹_조회_성공() {
        // given
        val snapshotDate = LocalDate.of(2026, 5, 31)

        val user = Users("user1", "password", "월간왕").apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        val ranking = Ranking(user, 1, BigDecimal("15.50"), 57750000L, RankingSnapshotType.MONTHLY, snapshotDate)

        // 💡 Kotlin x Mockito NPE 우회
        given(
            rankingRepository.findRankings(
                eq(RankingSnapshotType.MONTHLY) ?: RankingSnapshotType.MONTHLY,
                eq(snapshotDate) ?: snapshotDate,
                any(Sort::class.java) ?: Sort.unsorted()
            )
        ).willReturn(listOf(ranking))

        // when
        val result = rankingService.getMonthlyRankings(snapshotDate)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].userId).isEqualTo(1L)
        assertThat(result[0].nickname).isEqualTo("월간왕")
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[0].profitRate).isEqualByComparingTo("15.50")
        assertThat(result[0].totalAsset).isEqualTo(57750000L)
    }

    @Test
    @DisplayName("일간 랭킹 조회 시 Repository에 rankingPosition 오름차순 정렬 조건을 전달한다")
    fun 일간_랭킹_조회_정렬조건_검증() {
        // given
        given(
            rankingRepository.findRankings(
                eq(RankingSnapshotType.DAILY) ?: RankingSnapshotType.DAILY,
                any(LocalDate::class.java) ?: LocalDate.now(),
                any(Sort::class.java) ?: Sort.unsorted()
            )
        ).willReturn(emptyList())

        // when
        rankingService.getDailyRankings()

        // then
        val sortCaptor = ArgumentCaptor.forClass(Sort::class.java)

        // 💡 verify와 capture() 시에도 NPE가 터지므로 엘비스 연산자 방어
        verify(rankingRepository).findRankings(
            eq(RankingSnapshotType.DAILY) ?: RankingSnapshotType.DAILY,
            any(LocalDate::class.java) ?: LocalDate.now(),
            sortCaptor.capture() ?: Sort.unsorted()
        )

        val sort = sortCaptor.value

        assertThat(sort.getOrderFor("rankingPosition")).isNotNull
        assertThat(sort.getOrderFor("rankingPosition")!!.direction).isEqualTo(Sort.Direction.ASC)
    }
}