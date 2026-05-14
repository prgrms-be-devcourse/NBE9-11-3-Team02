package com.back.together02be.global.initData;

import com.back.together02be.achievement.entity.Achievement;
import com.back.together02be.achievement.repository.AchievementRepository;
import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.ranking.service.RankingSeasonService;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.users.dto.request.SignupReq;
import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import com.back.together02be.users.service.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BaseInitData {

    private final UsersRepository usersRepository;
    private final StockRepository stockRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    @Lazy
    private BaseInitData self;

    private final UsersService usersService;
    private final UserStockRepository userStockRepository;
    private final AchievementRepository achievementRepository;

    private final RankingSeasonService rankingSeasonService;

    private void createTestUserIfNotExists(String username, String password, String nickname) {
        if (usersRepository.findByUsername(username).isPresent()) {
            return;
        }

        usersService.signup(new SignupReq(username, password, password, nickname));
    }

    @Bean
    public ApplicationRunner initData() {
        return args -> {
//            self.work1();
//            self.work2();
            self.work3();
            self.work4();
            self.work6();
            self.work5();
        };
    }

    @Transactional
    public void work1() {

        if (stockRepository.existsByStockCode("005930")) {
            return;
        }

        // 테스트용 시드 데이터 (H2 dev 환경 전용)
        // 테스트 계정이 이미 있으면 다시 생성하지 않는다.
        if (usersRepository.existsByUsername("testuser")) {
            return;
        }

        Users user = usersRepository.save(new Users("testuser", passwordEncoder.encode("password1234"), "테스트유저"));

        userAccountRepository.save(new UserAccount(user, 0L, 50_000_000L));
    }

    @Transactional
    public void work2() {
        if (usersRepository.findByUsername("user1").isEmpty()) {
            usersService.signup(new SignupReq("user1", "password01", "password01", "유저1"));
        }

        if (usersRepository.findByUsername("user2").isEmpty()) {
            usersService.signup(new SignupReq("user2", "password02", "password02", "유저2"));
        }

        if (usersRepository.findByUsername("user3").isEmpty()) {
            usersService.signup(new SignupReq("user3", "password03", "password03", "유저3"));
        }
    }

    @Transactional
    public void work3() {
        if (stockRepository.count() > 0) {
            return;
        }

        stockRepository.save(new Stock("005930", "삼성전자", StockMarket.KOSPI));
        stockRepository.save(new Stock("000660", "SK하이닉스", StockMarket.KOSPI));
        stockRepository.save(new Stock("035420", "NAVER", StockMarket.KOSPI));
        stockRepository.save(new Stock("035720", "카카오", StockMarket.KOSPI));
        stockRepository.save(new Stock("068270", "셀트리온", StockMarket.KOSPI));
        stockRepository.save(new Stock("005380", "현대차", StockMarket.KOSPI));
        stockRepository.save(new Stock("012330", "현대모비스", StockMarket.KOSPI));
        stockRepository.save(new Stock("105560", "KB금융", StockMarket.KOSPI));
        stockRepository.save(new Stock("055550", "신한지주", StockMarket.KOSPI));
        stockRepository.save(new Stock("034730", "SK", StockMarket.KOSPI));
        stockRepository.save(new Stock("066570", "LG전자", StockMarket.KOSPI));
        stockRepository.save(new Stock("003670", "포스코퓨처엠", StockMarket.KOSPI));
        stockRepository.save(new Stock("096770", "SK이노베이션", StockMarket.KOSPI));
        stockRepository.save(new Stock("015760", "한국전력", StockMarket.KOSPI));
        stockRepository.save(new Stock("032830", "삼성생명", StockMarket.KOSPI));
        stockRepository.save(new Stock("086790", "하나금융지주", StockMarket.KOSPI));
        stockRepository.save(new Stock("051910", "LG화학", StockMarket.KOSPI));
        stockRepository.save(new Stock("006400", "삼성SDI", StockMarket.KOSPI));
        stockRepository.save(new Stock("207940", "삼성바이오로직스", StockMarket.KOSPI));
        stockRepository.save(new Stock("373220", "LG에너지솔루션", StockMarket.KOSPI));
    }

    @Transactional
    public void work4() {
        if (achievementRepository.count() > 0) {
            return;
        }

        log.info("업적(Achievement) 초기 데이터 세팅을 시작합니다.");

        // 4. 기본 업적 데이터 저장 (이전 단계에서 Achievement 엔티티에 @Builder를 추가했다고 가정)
        achievementRepository.save(Achievement.builder()
                .code("FIRST_TRADE")
                .name("첫 주주 등극")
                .description("생애 첫 주식 매수 성공")
                .reward("1,000 포인트")
                .build());

        achievementRepository.save(Achievement.builder()
                .code("BIG_SPENDER")
                .name("모의투자 큰 손")
                .description("누적 매수 금액 1,000만원 돌파")
                .reward("10,000 포인트")
                .build());

        log.info("업적 초기 데이터 세팅 완료.");
    }

    @Transactional
    public void work5() {
        if (userStockRepository.count() > 0) {
            return;
        }

        Users plus1 = usersRepository.findByUsername("plus1")
                .orElseGet(() -> {
                    usersService.signup(new SignupReq("plus1", "password01", "password01", "플러스1"));
                    return usersRepository.findByUsername("plus1")
                            .orElseThrow(() -> new IllegalStateException("plus1 생성 실패"));
                });

        Users plus2 = usersRepository.findByUsername("plus2")
                .orElseGet(() -> {
                    usersService.signup(new SignupReq("plus2", "password02", "password02", "플러스2"));
                    return usersRepository.findByUsername("plus2")
                            .orElseThrow(() -> new IllegalStateException("plus2 생성 실패"));
                });

        Users minus1 = usersRepository.findByUsername("minus1")
                .orElseGet(() -> {
                    usersService.signup(new SignupReq("minus1", "password04", "password04", "마이너스1"));
                    return usersRepository.findByUsername("minus1")
                            .orElseThrow(() -> new IllegalStateException("minus1 생성 실패"));
                });

        Users minus2 = usersRepository.findByUsername("minus2")
                .orElseGet(() -> {
                    usersService.signup(new SignupReq("minus2", "password05", "password05", "마이너스2"));
                    return usersRepository.findByUsername("minus2")
                            .orElseThrow(() -> new IllegalStateException("minus2 생성 실패"));
                });

        UserAccount plus1Account = userAccountRepository.findByUsersId(plus1.getId())
                .orElseThrow(() -> new IllegalStateException("plus1 계좌 없음"));
        UserAccount plus2Account = userAccountRepository.findByUsersId(plus2.getId())
                .orElseThrow(() -> new IllegalStateException("plus2 계좌 없음"));
        UserAccount minus1Account = userAccountRepository.findByUsersId(minus1.getId())
                .orElseThrow(() -> new IllegalStateException("minus1 계좌 없음"));
        UserAccount minus2Account = userAccountRepository.findByUsersId(minus2.getId())
                .orElseThrow(() -> new IllegalStateException("minus2 계좌 없음"));


        Stock samsung = stockRepository.findByStockCode("005930")
                .orElseThrow(() -> new IllegalStateException("삼성전자 없음"));
        Stock skhynix = stockRepository.findByStockCode("000660")
                .orElseThrow(() -> new IllegalStateException("SK하이닉스 없음"));
        Stock naver = stockRepository.findByStockCode("035420")
                .orElseThrow(() -> new IllegalStateException("NAVER 없음"));
        Stock kakao = stockRepository.findByStockCode("035720")
                .orElseThrow(() -> new IllegalStateException("카카오 없음"));
        Stock lgChem = stockRepository.findByStockCode("051910")
                .orElseThrow(() -> new IllegalStateException("LG화학 없음"));

        // 수익 유저
        userStockRepository.save(new UserStock(plus1, samsung, 50L, 70000L));
        plus1Account.decreaseDeposit(3_500_000L);
        plus1Account.increaseTotalPurchase(3_500_000L);

        userStockRepository.save(new UserStock(plus2, skhynix, 10L, 1_000_000L));
        plus2Account.decreaseDeposit(10_000_000L);
        plus2Account.increaseTotalPurchase(10_000_000L);


        // 손실 유저
        userStockRepository.save(new UserStock(minus1, kakao, 100L, 60000L));
        minus1Account.decreaseDeposit(6_000_000L);
        minus1Account.increaseTotalPurchase(6_000_000L);

        userStockRepository.save(new UserStock(minus2, lgChem, 10L, 450000L));
        minus2Account.decreaseDeposit(4_500_000L);
        minus2Account.increaseTotalPurchase(4_500_000L);
    }

    @Transactional
    public void work6() {
        LocalDate today = LocalDate.now();
        rankingSeasonService.startSeason(today);
    }

}
