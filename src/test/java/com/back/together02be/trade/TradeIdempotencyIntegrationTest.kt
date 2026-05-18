package com.back.together02be.trade

import com.back.together02be.achievement.listener.AchievementEventListener
import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.idempotency.IdempotencyKeyRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.support.IntegrationTestSupport
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.trade.service.TradeService
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 멱등성 키 통합 테스트 — 실제 DB를 사용해 네트워크 재전송 방어를 검증한다.
 * @Transactional 미사용 — 멱등성 키 UNIQUE 제약은 커밋 후에만 다른 스레드가 감지할 수 있다.
 */
class TradeIdempotencyIntegrationTest : IntegrationTestSupport() {

    @Autowired lateinit var tradeService: TradeService
    @Autowired lateinit var userAccountRepository: UserAccountRepository
    @Autowired lateinit var userStockRepository: UserStockRepository
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var stockRepository: StockRepository
    @Autowired lateinit var tradeRepository: TradeRepository
    @Autowired lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    @MockitoBean lateinit var stockPriceStore: RealTimeStockPriceStore
    @MockitoBean lateinit var achievementEventListener: AchievementEventListener

    private lateinit var user: Users
    private lateinit var stock: Stock

    @BeforeEach
    fun setUp() {
        user = usersRepository.save(Users("idempotency_user", "pw", "멱등성테스트유저"))
        stock = stockRepository.save(Stock("888888", "멱등성테스트종목", StockMarket.KOSPI))
        userAccountRepository.save(UserAccount(user, 0L, 10_000_000L))

        Mockito.`when`(stockPriceStore.get("888888")).thenReturn(
            RealtimeStockPrice.builder()
                .stockCode("888888")
                .price("70000")
                .build()
        )
    }

    @AfterEach
    fun tearDown() {
        tradeRepository.deleteAll(
            tradeRepository.findAll().filter { it.stock.id == stock.id }
        )
        userStockRepository.findByUsersIdAndStockId(user.id, stock.id)
            .ifPresent { userStockRepository.delete(it) }
        userAccountRepository.findByUsersId(user.id)
            .ifPresent { userAccountRepository.delete(it) }
        idempotencyKeyRepository.deleteAll(
            idempotencyKeyRepository.findAll().filter { it.userId == user.id }
        )
        stockRepository.delete(stock)
        usersRepository.delete(user)
    }

    @Test
    @DisplayName("동일 멱등성 키 동시 5회 재전송 — DB Trade 체결은 1건만")
    fun 동일_키_동시_재전송_체결_1건() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = BuyReq(stock.id, 10L, 70_000L)

        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val blockedCount = AtomicInteger(0)
        val tradeIds = CopyOnWriteArrayList<Long>()

        println("=".repeat(60))
        println("[멱등성 테스트 1] 동일 키로 ${threadCount}개 스레드 동시 전송")
        println("키: $idempotencyKey")
        println("=".repeat(60))

        for (i in 0 until threadCount) {
            val threadId = i + 1
            executor.submit {
                try {
                    startLatch.await()
                    val res = tradeService.buy(user.id, idempotencyKey, request)
                    successCount.incrementAndGet()
                    tradeIds.add(res.tradeId)
                    println("[스레드-$threadId] 응답 수신 — tradeId=${res.tradeId}")
                } catch (e: Exception) {
                    blockedCount.incrementAndGet()
                    println("[스레드-$threadId] 처리 중 차단 — ${e.message}")
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val tradeCount = tradeRepository.findAll().count { it.stock.id == stock.id }
        val distinctTradeIds = tradeIds.distinct().count()

        println("=".repeat(60))
        println("[결과] 응답 수신: ${successCount.get()}건 | 처리 중 차단: ${blockedCount.get()}건")
        println("[결과] 수신된 tradeId 목록: $tradeIds (모두 동일해야 함)")
        println("[결과] DB Trade 저장: ${tradeCount}건 (기대값 1건 — 중복 체결 없음)")
        println("=".repeat(60))

        // 핵심: 몇 건이 성공하든 DB에는 Trade 1건, 모든 응답이 같은 tradeId
        assertThat(tradeCount.toLong()).isEqualTo(1)
        assertThat(distinctTradeIds.toLong()).isEqualTo(1)
    }

    @Test
    @DisplayName("완료된 요청 재전송 — 캐시된 응답 반환, DB 체결 추가 없음")
    fun 완료된_요청_재전송_캐시_응답() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = BuyReq(stock.id, 10L, 70_000L)

        println("=".repeat(60))
        println("[멱등성 테스트 2] 완료 후 동일 키 순차 재전송")
        println("=".repeat(60))

        // 1차 요청 — 정상 체결
        val first = tradeService.buy(user.id, idempotencyKey, request)
        println("[1차] 체결 완료 — tradeId=${first.tradeId}, 금액=%,d원".format(first.amount))

        val countAfterFirst = tradeRepository.findAll().count { it.stock.id == stock.id }
        println("[1차 후] DB Trade: ${countAfterFirst}건")

        // 2차 요청 — 네트워크 재전송 시뮬레이션
        val second = tradeService.buy(user.id, idempotencyKey, request)
        println("[2차] 캐시 응답 반환 — tradeId=${second.tradeId} (1차와 동일)")

        val countAfterSecond = tradeRepository.findAll().count { it.stock.id == stock.id }

        println("=".repeat(60))
        println("[결과] 1차 tradeId=${first.tradeId} | 2차 tradeId=${second.tradeId} → ${if (first.tradeId == second.tradeId) "동일 (캐시 응답)" else "다름 (버그!)"}")
        println("[결과] DB Trade: ${countAfterSecond}건 (기대값 1건 — 재전송으로 중복 체결 없음)")
        println("=".repeat(60))

        assertThat(first.tradeId).isEqualTo(second.tradeId)
        assertThat(countAfterSecond.toLong()).isEqualTo(1)
    }
}
