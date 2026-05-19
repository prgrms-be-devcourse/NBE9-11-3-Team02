package com.back.together02be.global.initData

import com.back.together02be.achievement.entity.Achievement
import com.back.together02be.achievement.repository.AchievementRepository
import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.extend.getOrThrow
import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.users.dto.request.SignupReq
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import com.back.together02be.users.service.UsersService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Configuration
class BaseInitData(
    private val usersRepository: UsersRepository,
    private val stockRepository: StockRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    @param:Autowired
    @param:Lazy
    private val self: BaseInitData,
    private val usersService: UsersService,
    private val userStockRepository: UserStockRepository,
    private val achievementRepository: AchievementRepository,
    private val rankingSeasonService: RankingSeasonService
) {
    private fun createTestUserIfNotExists(username: String, password: String, nickname: String) {
        if (usersRepository.findByUsername(username) != null) {
            return
        }

        usersService.signup(SignupReq(username, password, password, nickname))
    }

    @Bean
    fun initData(): ApplicationRunner {
        return ApplicationRunner { _: ApplicationArguments ->
//            self.work1();
//            self.work2();
            self.work3()
            self.work4()
            self.work6()
            self.work5()
        }
    }

    @Transactional
    fun work1() {
        if (stockRepository.existsByStockCode("005930")) {
            return
        }

        // 테스트용 시드 데이터 (H2 dev 환경 전용)
        // 테스트 계정이 이미 있으면 다시 생성하지 않는다.
        if (usersRepository.existsByUsername("testuser")) {
            return
        }

        val encodedPassword = requireNotNull(passwordEncoder.encode("password1234")) {
            "비밀번호 암호화에 실패했습니다."
        }
        val user = usersRepository.save(Users("testuser", encodedPassword, "테스트유저"))

        userAccountRepository.save(UserAccount(user, 0L, 50000000L))
    }

    @Transactional
    fun work2() {
        if (usersRepository.findByUsername("user1") == null) {
            usersService.signup(SignupReq("user1", "password01", "password01", "유저1"))
        }

        if (usersRepository.findByUsername("user2") == null) {
            usersService.signup(SignupReq("user2", "password02", "password02", "유저2"))
        }

        if (usersRepository.findByUsername("user3") == null) {
            usersService.signup(SignupReq("user3", "password03", "password03", "유저3"))
        }
    }

    @Transactional
    fun work3() {
        if (stockRepository.count() > 0) {
            return
        }

        stockRepository.save(Stock("005930", "삼성전자", StockMarket.KOSPI))
        stockRepository.save(Stock("000660", "SK하이닉스", StockMarket.KOSPI))
        stockRepository.save(Stock("035420", "NAVER", StockMarket.KOSPI))
        stockRepository.save(Stock("035720", "카카오", StockMarket.KOSPI))
        stockRepository.save(Stock("068270", "셀트리온", StockMarket.KOSPI))
        stockRepository.save(Stock("005380", "현대차", StockMarket.KOSPI))
        stockRepository.save(Stock("012330", "현대모비스", StockMarket.KOSPI))
        stockRepository.save(Stock("105560", "KB금융", StockMarket.KOSPI))
        stockRepository.save(Stock("055550", "신한지주", StockMarket.KOSPI))
        stockRepository.save(Stock("034730", "SK", StockMarket.KOSPI))
        stockRepository.save(Stock("066570", "LG전자", StockMarket.KOSPI))
        stockRepository.save(Stock("003670", "포스코퓨처엠", StockMarket.KOSPI))
        stockRepository.save(Stock("096770", "SK이노베이션", StockMarket.KOSPI))
        stockRepository.save(Stock("015760", "한국전력", StockMarket.KOSPI))
        stockRepository.save(Stock("032830", "삼성생명", StockMarket.KOSPI))
        stockRepository.save(Stock("086790", "하나금융지주", StockMarket.KOSPI))
        stockRepository.save(Stock("051910", "LG화학", StockMarket.KOSPI))
        stockRepository.save(Stock("006400", "삼성SDI", StockMarket.KOSPI))
        stockRepository.save(Stock("207940", "삼성바이오로직스", StockMarket.KOSPI))
        stockRepository.save(Stock("373220", "LG에너지솔루션", StockMarket.KOSPI))
    }

    @Transactional
    fun work4() {
        if (achievementRepository.count() > 0) {
            return
        }

        log.info("업적(Achievement) 초기 데이터 세팅을 시작합니다.")

        // 4. 기본 업적 데이터 저장 (이전 단계에서 Achievement 엔티티에 @Builder를 추가했다고 가정)
        achievementRepository.save(
            Achievement(
                code = "FIRST_TRADE",
                name = "첫 주주 등극",
                description = "생애 첫 주식 매수 성공" // 생성자에 정의된 파라미터에 맞게 매핑
            )
        )

        achievementRepository.save(
            Achievement(
                code = "BIG_SPENDER",
                name = "모의투자 큰 손",
                description = "누적 매수 금액 1,000만원 돌파" // 생성자에 정의된 파라미터에 맞게 매핑
            )
        )

        log.info("업적 초기 데이터 세팅 완료.")
    }

    @Transactional
    fun work5() {
        if (userStockRepository.count() > 0) {
            return
        }

        val plus1 = usersRepository.findByUsername("plus1")
            ?: run {
                usersService.signup(SignupReq("plus1", "password01", "password01", "플러스1"))
                usersRepository.findByUsername("plus1")
                    .getOrThrow { IllegalStateException("plus1 생성 실패") }
            }

        val plus2 = usersRepository.findByUsername("plus2")
            ?: run {
                usersService.signup(SignupReq("plus2", "password02", "password02", "플러스2"))
                usersRepository.findByUsername("plus2")
                    .getOrThrow { IllegalStateException("plus2 생성 실패") }
            }

        val minus1 = usersRepository.findByUsername("minus1")
            ?: run {
                usersService.signup(SignupReq("minus1", "password04", "password04", "마이너스1"))
                usersRepository.findByUsername("minus1")
                    .getOrThrow { IllegalStateException("minus1 생성 실패") }
            }

        val minus2 = usersRepository.findByUsername("minus2")
            ?: run {
                usersService.signup(SignupReq("minus2", "password05", "password05", "마이너스2"))
                usersRepository.findByUsername("minus2")
                    .getOrThrow { IllegalStateException("minus2 생성 실패") }
            }

        val plus1Account = userAccountRepository.findByUsersId(plus1.id)
            .orElse(null)
            .getOrThrow { IllegalStateException("plus1 계좌 없음") }
        val plus2Account = userAccountRepository.findByUsersId(plus2.id)
            .orElse(null)
            .getOrThrow { IllegalStateException("plus2 계좌 없음") }
        val minus1Account = userAccountRepository.findByUsersId(minus1.id)
            .orElse(null)
            .getOrThrow { IllegalStateException("minus1 계좌 없음") }
        val minus2Account = userAccountRepository.findByUsersId(minus2.id)
            .orElse(null)
            .getOrThrow { IllegalStateException("minus2 계좌 없음") }


        val samsung = stockRepository.findByStockCode("005930")
            .getOrThrow { IllegalStateException("삼성전자 없음") }
        val skhynix = stockRepository.findByStockCode("000660")
            .getOrThrow { IllegalStateException("SK하이닉스 없음") }
        val naver = stockRepository.findByStockCode("035420")
            .getOrThrow { IllegalStateException("NAVER 없음") }
        val kakao = stockRepository.findByStockCode("035720")
            .getOrThrow { IllegalStateException("카카오 없음") }
        val lgChem = stockRepository.findByStockCode("051910")
            .getOrThrow { IllegalStateException("LG화학 없음") }

        // 수익 유저
        userStockRepository.save<UserStock>(UserStock(plus1, samsung, 50L, 70000L))
        plus1Account.decreaseDeposit(3500000L)
        plus1Account.increaseTotalPurchase(3500000L)

        userStockRepository.save<UserStock>(UserStock(plus2, skhynix, 10L, 1000000L))
        plus2Account.decreaseDeposit(10000000L)
        plus2Account.increaseTotalPurchase(10000000L)


        // 손실 유저
        userStockRepository.save<UserStock>(UserStock(minus1, kakao, 100L, 60000L))
        minus1Account.decreaseDeposit(6000000L)
        minus1Account.increaseTotalPurchase(6000000L)

        userStockRepository.save<UserStock>(UserStock(minus2, lgChem, 10L, 450000L))
        minus2Account.decreaseDeposit(4500000L)
        minus2Account.increaseTotalPurchase(4500000L)
    }

    @Transactional
    fun work6() {
        val today = LocalDate.now()
        rankingSeasonService.startSeason(today)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BaseInitData::class.java)
    }
}
