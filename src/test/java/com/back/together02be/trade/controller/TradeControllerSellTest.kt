package com.back.together02be.trade.controller

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.util.JwtUtil
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.support.ControllerTestSupport
import com.back.together02be.trade.util.MarketTimeValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@Transactional
class TradeControllerSellTest : ControllerTestSupport() {

    @Autowired
    private lateinit var userStockRepository: UserStockRepository

    @Autowired
    private lateinit var userAccountRepository: UserAccountRepository

    @Autowired
    private lateinit var realtimeStockPriceService: RealTimeStockPriceStore

    @Value("\${jwt.secret}")
    private lateinit var jwtSecret: String

    private lateinit var accessToken: String

    companion object {
        // 자바의 MockedStatic을 코틀린 companion object 내부에 선언
        private var mockedValidator: MockedStatic<MarketTimeValidator>? = null

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            mockedValidator = mockStatic(MarketTimeValidator::class.java)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            mockedValidator?.close()
        }
    }

    @BeforeEach
    fun setUp() {
        // Mockito의 정적 메서드 모킹 설정 (아무것도 하지 않음)
        mockedValidator?.`when`<Any> { MarketTimeValidator.validateMarketOpen() }
            ?.thenAnswer { null }

        accessToken = JwtUtil.generateAccessToken(
            jwtSecret,
            60 * 60, // 1시간
            mapOf(
                "id" to 1L,
                "username" to "testuser",
                "nickname" to "테스터"
            )
        )

        // 데이터 초기화
        val userStock = userStockRepository.findByUsersIdAndStockId(1L, 1L)
            .orElseThrow { RuntimeException("테스트용 UserStock 데이터가 없습니다.") }

        val userAccount = userAccountRepository.findByUsersId(1L)
            .orElseThrow { RuntimeException("테스트용 UserAccount 데이터가 없습니다.") }

        userStock.updateQuantity(10L)
        userAccount.addDeposit(1_000_000L - userAccount.deposit)
        userAccount.subtractTotalPurchase(userAccount.totalPurchase)
        userAccount.increaseTotalPurchase(700_000L)

        userStockRepository.saveAndFlush(userStock)
        userAccountRepository.saveAndFlush(userAccount)

        val samsungPrice = RealtimeStockPrice.builder()
            .stockCode("005930")
            .price("75000")
            .changeSign("1")
            .change("1")
            .changeRate("3")
            .tradeTime(LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
            .build()
        realtimeStockPriceService.put("005930", samsungPrice)
    }

    @Test
    @DisplayName("매도 성공 - 부분 매도")
    @Throws(Exception::class)
    fun t1() {
        val result: ResultActions = mockMvc
            .perform(
                post("/api/trades/sell")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        """
                        {
                            "userId": 1,
                            "stockId": 1,
                            "quantity": 5,
                            "expectedPrice": 75000
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        result
            .andExpect(handler().handlerType(TradeController::class.java))
            .andExpect(handler().methodName("sell"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("매도가 완료되었습니다."))
            .andExpect(jsonPath("$.data").exists())

        val userStock = userStockRepository.findByUsersIdAndStockId(1L, 1L).orElseThrow()
        val userAccount = userAccountRepository.findByUsersId(1L).orElseThrow()

        assertThat(userStock.quantity).isEqualTo(5L)
        assertThat(userAccount.deposit).isEqualTo(1_375_000L)
        assertThat(userAccount.totalPurchase).isEqualTo(350_000L)
    }

    @Test
    @DisplayName("매도 성공 - 전량 매도 시 UserStock 삭제")
    @Throws(Exception::class)
    fun t2() {
        val result: ResultActions = mockMvc
            .perform(
                post("/api/trades/sell")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        """
                        {
                            "userId": 1,
                            "stockId": 1,
                            "quantity": 10,
                            "expectedPrice": 75000
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        result
            .andExpect(handler().handlerType(TradeController::class.java))
            .andExpect(handler().methodName("sell"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("매도가 완료되었습니다."))
            .andExpect(jsonPath("$.data").exists())

        val deleted = userStockRepository.findByUsersIdAndStockId(1L, 1L)
        val userAccount = userAccountRepository.findByUsersId(1L).orElseThrow()

        assertThat(deleted).isEmpty
        assertThat(userAccount.deposit).isEqualTo(1_750_000L)
        assertThat(userAccount.totalPurchase).isEqualTo(0L)
    }

    @Test
    @DisplayName("매도 성공 - 손실 매도 (현재가 < 평단가)")
    @Throws(Exception::class)
    fun t3() {
        val lossPrice = RealtimeStockPrice.builder()
            .stockCode("005930")
            .price("60000")
            .changeSign("1")
            .change("1")
            .changeRate("3")
            .tradeTime(LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
            .build()
        realtimeStockPriceService.put("005930", lossPrice)

        val result: ResultActions = mockMvc
            .perform(
                post("/api/trades/sell")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        """
                        {
                            "userId": 1,
                            "stockId": 1,
                            "quantity": 1,
                            "expectedPrice": 60000
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("매도가 완료되었습니다."))

        val userStock = userStockRepository.findByUsersIdAndStockId(1L, 1L).orElseThrow()

        assertThat(userStock.quantity).isEqualTo(9L)
        assertThat(userAccount().deposit).isEqualTo(1_060_000L)
    }

    @Test
    @DisplayName("매도 실패 - 보유하지 않은 종목")
    @Throws(Exception::class)
    fun t4() {
        val result: ResultActions = mockMvc
            .perform(
                post("/api/trades/sell")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        """
                        {
                            "userId": 1,
                            "stockId": 999,
                            "quantity": 1,
                            "price": 75000
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        result
            .andExpect(handler().handlerType(TradeController::class.java))
            .andExpect(handler().methodName("sell"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("주식 정보가 없습니다."))
    }

    @Test
    @DisplayName("매도 실패 - 보유 수량 초과")
    @Throws(Exception::class)
    fun t5() {
        val result: ResultActions = mockMvc
            .perform(
                post("/api/trades/sell")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        """
                        {
                            "userId": 1,
                            "stockId": 1,
                            "quantity": 11,
                            "expectedPrice": 75000
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        result
            .andExpect(handler().handlerType(TradeController::class.java))
            .andExpect(handler().methodName("sell"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("보유 수량이 부족합니다."))

        val userStock = userStockRepository.findByUsersIdAndStockId(1L, 1L).orElseThrow()
        assertThat(userStock.quantity).isEqualTo(10L)
    }

    @Test
    @DisplayName("매도 실패 - quantity가 0 이하")
    @Throws(Exception::class)
    fun t6() {
        val result: ResultActions = mockMvc
            .perform(
                post("/api/trades/sell")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        """
                        {
                            "userId": 1,
                            "stockId": 1,
                            "quantity": 0,
                            "price": 75000
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        result
            .andExpect(handler().handlerType(TradeController::class.java))
            .andExpect(handler().methodName("sell"))
            .andExpect(status().isBadRequest)
    }

    private fun userAccount(): UserAccount {
        return userAccountRepository.findByUsersId(1L).orElseThrow()
    }
}