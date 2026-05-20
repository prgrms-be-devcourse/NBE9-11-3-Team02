package com.back.together02be.trade.processor

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.extend.getOrThrow
import com.back.together02be.ranking.repository.RankingSeasonRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.support.IntegrationTestSupport
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradeSellProcessorConcurrencyTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var tradeSellProcessor: TradeSellProcessor

    @Autowired
    private lateinit var userAccountRepository: UserAccountRepository

    @Autowired
    private lateinit var userStockRepository: UserStockRepository

    @Autowired
    private lateinit var tradeRepository: TradeRepository

    @Autowired
    private lateinit var userRepository: UsersRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    @Autowired
    private lateinit var stockPriceStore: RealTimeStockPriceStore

    @Autowired
    private lateinit var rankingSeasonRepository: RankingSeasonRepository

    @MockitoBean
    private lateinit var clock: Clock

    private var userId: Long = 0L
    private var stockId: Long = 0L

    private val INITIAL_QUANTITY = 100L
    private val INITIAL_DEPOSIT = 1_000_000L
    private val STOCK_PRICE = 10_000L

    private val fixedClock = Clock.fixed(
        LocalDateTime.of(2024, 1, 15, 10, 0)
            .atZone(ZoneId.of("Asia/Seoul")).toInstant(),
        ZoneId.of("Asia/Seoul")
    )

    @BeforeEach
    fun setUp() {
        given(clock.instant()).willReturn(fixedClock.instant())
        given(clock.zone).willReturn(fixedClock.zone)

        val stock = stockRepository.findByStockCode("005930")
            .getOrThrow { IllegalStateException("삼성전자 종목이 초기 데이터에 없습니다.") }
        stockId = stock.id

        val user = Users("testUser_${System.nanoTime()}", "test@test.com", "password")
        userRepository.saveAndFlush(user)
        userId = user.id

        val account = UserAccount(user, INITIAL_DEPOSIT, STOCK_PRICE * INITIAL_QUANTITY)
        userAccountRepository.saveAndFlush(account)

        val userStock = UserStock(user, stock, INITIAL_QUANTITY, STOCK_PRICE)
        userStockRepository.saveAndFlush(userStock)

        val currentTime = "100000"
        val realtimePrice = RealtimeStockPrice(
            stockCode = "005930",
            price = STOCK_PRICE.toString(),
            changeSign = "3",
            change = "0",
            changeRate = "0.00",
            tradeTime = currentTime
        )
        stockPriceStore.put("005930", realtimePrice)
    }

    @AfterEach
    fun tearDown() {
        tradeRepository.deleteAll()
        userStockRepository.deleteAll()
        userAccountRepository.deleteAll()
        rankingSeasonRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    @DisplayName("동시에 여러 매도 요청 - 총 수량 초과 매도 불가 검증")
    fun concurrentSell_shouldNotExceedTotalQuantity() {
        val threadCount = 10
        val sellQuantityPerThread = 20L

        val executorService = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val exceptions = Collections.synchronizedList(ArrayList<Exception>())

        for (i in 0 until threadCount) {
            val threadId = i
            executorService.submit {
                try {
                    startLatch.await()
                    println("[Thread-$threadId] 매도 시도 시작 (수량: $sellQuantityPerThread)")

                    val request = TradeSellReq(0L, stockId, sellQuantityPerThread, STOCK_PRICE)
                    tradeSellProcessor.processSell(userId, request)
                    successCount.incrementAndGet()

                    val remain = userStockRepository
                        .findByUsersIdAndStockId(userId, stockId)
                        ?.quantity ?: 0L
                    println("[Thread-$threadId] ✅ 매도 성공 → 남은 수량: $remain")

                } catch (e: IllegalStateException) {
                    failCount.incrementAndGet()
                    exceptions.add(e)
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    println("[Thread-$threadId] ❌ 매도 실패 → 이유: ${e.message}")
                    exceptions.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executorService.shutdown()

        val finalQuantity = userStockRepository
            .findByUsersIdAndStockId(userId, stockId)
            ?.quantity ?: 0L

        println("=== 동시성 테스트 결과 ===")
        println("성공한 매도 수: ${successCount.get()}")
        println("실패한 매도 수: ${failCount.get()}")
        println("최종 보유 수량: $finalQuantity")
        exceptions.groupBy { it.message ?: "Unknown Error" }
            .forEach { (msg, list) -> println("  - $msg : ${list.size}건") }

        assertThat(finalQuantity).isGreaterThanOrEqualTo(0L)
        assertThat(successCount.get() * sellQuantityPerThread).isLessThanOrEqualTo(INITIAL_QUANTITY)
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount)
    }

    @Test
    @DisplayName("동시에 전량 매도 요청 - 중복 매도 불가 검증")
    fun concurrentFullSell_onlyOneSucceeds() {
        val threadCount = 5
        val sellQuantity = INITIAL_QUANTITY

        println("\n================= TEST START =================")
        println("초기 보유 수량: ${INITIAL_QUANTITY}주")
        println("각 스레드 요청 수량: ${sellQuantity}주")
        println("총 요청 수량: ${threadCount * sellQuantity}주")
        println("=============================================\n")

        val executorService = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            val threadId = i
            executorService.submit {
                try {
                    startLatch.await()
                    println("[Thread-$threadId] ▶ 전량 매도 시도 (요청 수량: $sellQuantity)")

                    val request = TradeSellReq(0L, stockId, sellQuantity, STOCK_PRICE)
                    tradeSellProcessor.processSell(userId, request)
                    successCount.incrementAndGet()

                    val remain = userStockRepository
                        .findByUsersIdAndStockId(userId, stockId)
                        ?.quantity ?: 0L
                    println("[Thread-$threadId] ✅ 성공 → 보유 수량: ${remain}주")

                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    val remain = userStockRepository
                        .findByUsersIdAndStockId(userId, stockId)
                        ?.quantity ?: 0L
                    println("[Thread-$threadId] ❌ 실패 → 보유 수량: ${remain}주")
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        println("=== 모든 스레드 동시 시작 ===")
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executorService.shutdown()

        println("\n=== 전량 매도 동시성 테스트 결과 ===")
        println("성공한 매도 수: ${successCount.get()}")
        println("실패한 매도 수: ${failCount.get()}")

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(threadCount - 1)

        val deletedStock = userStockRepository.findByUsersIdAndStockId(userId, stockId)
        val finalQuantity = deletedStock?.quantity ?: 0L
        println("최종 보유 수량: ${finalQuantity}주")

        assertThat(deletedStock).isNull()
    }
}