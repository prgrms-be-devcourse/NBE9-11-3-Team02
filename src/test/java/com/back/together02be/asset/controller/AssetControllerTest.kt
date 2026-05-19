package com.back.together02be.asset.controller

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.extend.getOrThrow
import com.back.together02be.global.security.SecurityUser
import com.back.together02be.infra.kis.rest.KisPriceClient
import com.back.together02be.infra.kis.rest.dto.KisPriceRes
import com.back.together02be.infra.kis.rest.service.KisTokenService
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.support.ControllerTestSupport
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user

class AssetControllerTest : ControllerTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var kisTokenService: KisTokenService

    @MockitoBean
    private lateinit var kisPriceClient: KisPriceClient

    @Autowired
    private lateinit var userAccountRepository: UserAccountRepository

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var userStockRepository: UserStockRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    private lateinit var testUser: Users

    @BeforeEach
    fun setUp() {
        val mockOutput = KisPriceRes.Output("70000", "1000", "2", "1.45")
        val mockPriceRes = KisPriceRes("0", "MCA00000", "정상처리", mockOutput)

        // 실제 메서드명: getCurrentPrice(String token, String stockCode)
        given(kisPriceClient.getCurrentPrice(anyString(), anyString())).willReturn(mockPriceRes)

        // kisTokenService Mock
        given(kisTokenService.getAccessToken()).willReturn("mock-token")

        // 1. 기존 데이터 정리
        userStockRepository.deleteAll()
        userAccountRepository.deleteAll()

        // 2. 이미 존재하는 Stock을 조회
        val samsung = stockRepository.findByStockCode("005930")
            .getOrThrow { RuntimeException("삼성전자 데이터가 존재하지 않습니다.") }

        val hynix = stockRepository.findByStockCode("000660")
            .getOrThrow { RuntimeException("SK하이닉스 데이터가 존재하지 않습니다.") }

        // 3. 테스트에 필요한 유저 데이터만 생성
        testUser = usersRepository.save(Users("user1", "1234", "my_nick"))

        val testUserAccount = UserAccount(testUser, 10000L, 1000000L)
        userAccountRepository.save(testUserAccount)

        // 4. 연관관계 설정 후 저장
        val us1 = UserStock(testUser, samsung, 10L, 5000L)
        val us2 = UserStock(testUser, hynix, 2L, 10000L)
        userStockRepository.saveAll(listOf(us1, us2))
    }

    @Test
    @DisplayName("총매수금 및 보유 주식 목록 조회 테스트")
    fun test1() {
        val securityUser = SecurityUser(
            testUser.id,
            testUser.username,
            testUser.password,
            testUser.nickname,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )

        // 1. URL 수정: "/api/asset/accounts/1" -> "/api/asset/accounts"
        mockMvc.perform(get("/api/asset/accounts").with(user(securityUser)))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalAmount").value(10000L))
            .andExpect(jsonPath("$.data.stocks.length()").value(2))
    }
}