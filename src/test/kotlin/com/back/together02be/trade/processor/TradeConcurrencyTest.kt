package com.back.together02be.trade.processor

import com.back.together02be.achievement.listener.AchievementEventListener
import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.idempotency.IdempotencyKey
import com.back.together02be.global.idempotency.IdempotencyKeyRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.support.IntegrationTestSupport
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 동시성 통합 테스트.
 *
 * @Transactional 미사용 — 동시 스레드가 커밋된 데이터를 읽어야 하므로
 * 각 테스트 후 @AfterEach에서 데이터를 직접 정리한다.
 */
class TradeConcurrencyTest : IntegrationTestSupport() {

    @Autowired lateinit var tradeBuyProcessor: TradeBuyProcessor
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
        user = usersRepository.save(Users("concurrency_user", "pw", "동시성테스트유저"))
        stock = stockRepository.save(Stock("999999", "테스트종목", StockMarket.KOSPI))

        whenever(stockPriceStore.get("999999")).thenReturn(
            RealtimeStockPrice(
                stockCode = "999999",
                price = "70000",
                changeSign = "",
                change = "",
                changeRate = "",
                tradeTime = null
            )
        )
    }

    @AfterEach
    fun tearDown() {
        // 외래키 참조 순서대로 삭제: Trade → UserStock → UserAccount → IdempotencyKey → Stock → Users
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
    @DisplayName("잔고 부족 — 동시 요청 중 하나만 성공")
    fun 잔고_부족_동시_요청_하나만_성공() {
        // given: 잔고 100만원, 각 요청 70만원 → 한 건만 통과 가능
        val deposit = 1_000_000L
        val price = 70_000L
        val quantity = 10L // 70만원
        userAccountRepository.save(UserAccount(user, 0L, deposit))

        val threadCount = 2
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        println("=".repeat(60))
        println("[비관적 락 + 원자적 잔고차감] 테스트 시작")
        println("초기 잔고: %,d원 | 요청 스레드: %d개 | 건당 매수 금액: %,d원".format(deposit, threadCount, price * quantity))
        println("이론적 허용 건수: %d건 (잔고 초과 시 원자적으로 차단)".format(deposit / (price * quantity)))
        println("=".repeat(60))

        // when
        for (i in 0 until threadCount) {
            val threadId = i + 1
            executor.submit {
                try {
                    val key = UUID.randomUUID().toString()
                    idempotencyKeyRepository.save(IdempotencyKey(key, user.id))
                    startLatch.await()
                    val start = System.currentTimeMillis()
                    tradeBuyProcessor.processBuy(user.id, key, BuyReq(stock.id, quantity, 70_000L))
                    println("[스레드-$threadId] 매수 성공 (${System.currentTimeMillis() - start}ms)")
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("[스레드-$threadId] 차단됨 — ${e.message}")
                    failCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // then
        val result = userAccountRepository.findByUsersId(user.id).orElseThrow()
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(1)
        assertThat(result.deposit).isEqualTo(deposit - price * quantity) // 30만원
        assertThat(result.deposit).isGreaterThanOrEqualTo(0) // 음수 잔고 없음

        println("=".repeat(60))
        println("[결과] 성공: ${successCount.get()}건 | 차단: ${failCount.get()}건")
        println("[결과] 최종 잔고: %,d원 (음수 잔고 없음 — 원자적 차감 정상)".format(result.deposit))
        println("=".repeat(60))
    }

    @Test
    @DisplayName("잔고 충분 — 동시 첫 매수 둘 다 성공, UserStock 수량 정확")
    fun 잔고_충분_동시_첫_매수_둘_다_성공() {
        // given: 잔고 200만원, 각 요청 70만원 → 둘 다 통과
        val deposit = 2_000_000L
        val quantity = 10L // 각 70만원
        userAccountRepository.save(UserAccount(user, 0L, deposit))

        val threadCount = 2
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        println("=".repeat(60))
        println("[비관적 락] 잔고 충분 시 동시 매수 직렬화 테스트")
        println("초기 잔고: %,d원 | 스레드: %d개 | 건당: %,d원".format(deposit, threadCount, 70_000L * quantity))
        println("비관적 락(SELECT FOR UPDATE)으로 UserStock 업데이트 직렬화")
        println("=".repeat(60))

        // when
        for (i in 0 until threadCount) {
            val threadId = i + 1
            executor.submit {
                try {
                    val key = UUID.randomUUID().toString()
                    idempotencyKeyRepository.save(IdempotencyKey(key, user.id))
                    startLatch.await()
                    val start = System.currentTimeMillis()
                    tradeBuyProcessor.processBuy(user.id, key, BuyReq(stock.id, quantity, 70_000L))
                    println("[스레드-$threadId] 매수 성공 (${System.currentTimeMillis() - start}ms)")
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    System.err.println("[스레드-$threadId] 실패: ${e.message}")
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // then
        val userStock = userStockRepository.findByUsersIdAndStockId(user.id, stock.id).orElseThrow()
        val result = userAccountRepository.findByUsersId(user.id).orElseThrow()

        assertThat(successCount.get()).isEqualTo(2)
        assertThat(failCount.get()).isEqualTo(0)
        assertThat(userStock.quantity).isEqualTo(quantity * 2) // 20주
        assertThat(result.deposit).isEqualTo(deposit - 70_000L * quantity * 2) // 60만원

        println("=".repeat(60))
        println("[결과] 성공: ${successCount.get()}건 | 실패: ${failCount.get()}건")
        println("[결과] 보유 주식: ${userStock.quantity}주 (비관적 락으로 수량 유실 없음)")
        println("[결과] 최종 잔고: %,d원 (정확히 차감됨)".format(result.deposit))
        println("=".repeat(60))
    }
}
