package com.back.together02be.trade.processor

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.ranking.repository.RankingSeasonRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.support.IntegrationTestSupport
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.trade.util.MarketTimeValidator
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Transactional(propagation = Propagation.NOT_SUPPORTED) // 각 스레드가 독립적인 트랜잭션을 가지도록 격리
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

    private var userId: Long = 0L
    private var stockId: Long = 0L

    private val INITIAL_QUANTITY = 100L
    private val INITIAL_DEPOSIT = 1_000_000L
    private val STOCK_PRICE = 10_000L

    @BeforeEach
    fun setUp() {
        // 1. BaseInitData로 이미 저장된 삼성전자 불러오기
        val stock = stockRepository.findByStockCode("005930")
            .orElseThrow { IllegalStateException("삼성전자 종목이 초기 데이터에 없습니다.") }
        stockId = stock.id

        // 2. 테스트용 유저 생성 (매번 새로 만들어 격리)
        val user = Users("testUser_${System.nanoTime()}", "test@test.com", "password")
        userRepository.saveAndFlush(user)
        userId = user.id

        // 3. 테스트용 계좌 생성
        val account = UserAccount(user, INITIAL_DEPOSIT, STOCK_PRICE * INITIAL_QUANTITY)
        userAccountRepository.saveAndFlush(account)

        // 4. 보유 주식 생성
        val userStock = UserStock(user, stock, INITIAL_QUANTITY, STOCK_PRICE)
        userStockRepository.saveAndFlush(userStock)

        // 5. 실시간 주가 세팅 (stale 방지 — 현재 시각 기준)
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        val realtimePrice = RealtimeStockPrice.builder()
            .stockCode("005930")
            .price(STOCK_PRICE.toString())
            .changeSign("3")
            .change("0")
            .changeRate("0.00")
            .tradeTime(currentTime)
            .build()
        stockPriceStore.put("005930", realtimePrice)
    }

    @AfterEach
    fun tearDown() {
        // stock은 BaseInitData 소유이므로 삭제 제외
        tradeRepository.deleteAll()
        userStockRepository.deleteAll()
        userAccountRepository.deleteAll()
        rankingSeasonRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    @DisplayName("동시에 여러 매도 요청 - 총 수량 초과 매도 불가 검증")
    fun concurrentSell_shouldNotExceedTotalQuantity() {
        // given
        val threadCount = 10
        val sellQuantityPerThread = 20L // 각 스레드가 20주씩 매도 시도 (총 200주, 보유는 100주)

        val executorService = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val exceptions = Collections.synchronizedList(ArrayList<Exception>())

        // when
        for (i in 0 until threadCount) {
            val threadId = i
            executorService.submit {
                try {
                    startLatch.await() // 모든 스레드가 준비될 때까지 대기

                    println("[Thread-$threadId] 매도 시도 시작 (수량: $sellQuantityPerThread)")

                    val request = TradeSellReq(0L, stockId, sellQuantityPerThread, STOCK_PRICE)
                    tradeSellProcessor.processSell(userId, request)
                    successCount.incrementAndGet()

                    val current = userStockRepository
                        .findByUsersIdAndStockId(userId, stockId)
                        .orElse(null)

                    val remain = current?.quantity ?: 0L
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

        startLatch.countDown() // 모든 스레드 동시 시작
        doneLatch.await(30, TimeUnit.SECONDS)
        executorService.shutdown()

        // then
        val finalStock = userStockRepository.findByUsersIdAndStockId(userId, stockId).orElse(null)
        val finalQuantity = finalStock?.quantity ?: 0L

        println("=== 동시성 테스트 결과 ===")
        println("성공한 매도 수: ${successCount.get()}")
        println("실패한 매도 수: ${failCount.get()}")
        println("최종 보유 수량: $finalQuantity")
        println("실패 이유들: ")

        // 코틀린 스타일로 예외 카운트 집계 및 출력
        exceptions.groupBy { it.message ?: "Unknown Error" }
            .forEach { (msg, list) -> println("  - $msg : ${list.size}건") }

        // 검증
        assertThat(finalQuantity).isGreaterThanOrEqualTo(0L)

        val totalSoldQuantity = successCount.get() * sellQuantityPerThread
        assertThat(totalSoldQuantity).isLessThanOrEqualTo(INITIAL_QUANTITY)
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount)
    }

    @Test
    @DisplayName("동시에 전량 매도 요청 - 중복 매도 불가 검증")
    fun concurrentFullSell_onlyOneSucceeds() {
        // given
        val threadCount = 5
        val sellQuantity = INITIAL_QUANTITY // 전량 매도 요청

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

        // when
        for (i in 0 until threadCount) {
            val threadId = i
            executorService.submit {
                // 자바의 try-with-resources 대신 코틀린의 .use 블록으로 정적 모킹 자원 해제 처리 보장
                mockStatic(MarketTimeValidator::class.java).use { sw ->
                    sw.`when`<Any> { MarketTimeValidator.validateMarketOpen() }.thenAnswer { null }

                    try {
                        startLatch.await()

                        println("[Thread-$threadId] ▶ 전량 매도 시도 (요청 수량: $sellQuantity)")

                        val request = TradeSellReq(0L, stockId, sellQuantity, STOCK_PRICE)
                        tradeSellProcessor.processSell(userId, request)
                        successCount.incrementAndGet()

                        val current = userStockRepository
                            .findByUsersIdAndStockId(userId, stockId)
                            .orElse(null)

                        val remain = current?.quantity ?: 0L
                        println("[Thread-$threadId] ✅ 성공 → 보유 수량: ${remain}주 (전량 매도 완료)")

                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                        System.err.println("======= 에러 발생 상세 원인 =======")
                        e.printStackTrace()
                        System.err.println("==================================")

                        val current = userStockRepository
                            .findByUsersIdAndStockId(userId, stockId)
                            .orElse(null)

                        val remain = current?.quantity ?: 0L
                        println("[Thread-$threadId] ❌ 실패 → 보유 수량: ${remain}주 (이미 매도됨)")
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
        }

        println("=== 모든 스레드 동시 시작 ===")
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executorService.shutdown()

        // then
        println("\n=== 전량 매도 동시성 테스트 결과 ===")
        println("초기 보유 수량: ${INITIAL_QUANTITY}주")
        println("총 요청 수량: ${threadCount * sellQuantity}주")
        println("성공한 매도 수: ${successCount.get()}")
        println("실패한 매도 수: ${failCount.get()}")

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(threadCount - 1)

        val deletedStock = userStockRepository.findByUsersIdAndStockId(userId, stockId)
        val finalQuantity = deletedStock.map { it.quantity }.orElse(0L)
        println("최종 보유 수량: ${finalQuantity}주")

        if (successCount.get() == 1 && finalQuantity == 0L) {
            println("✔️ 결과: 단 1명만 전량 매도 성공 → 중복 매도 완벽 차단")
        } else {
            println("❌ 결과: 동시성 문제 발생")
        }

        assertThat(deletedStock).isEmpty
    }
}