package com.back.together02be.asset.controller;

import com.back.together02be.asset.dto.response.StockInfoRes;
import com.back.together02be.asset.dto.response.TotalPurchaseRes;
import com.back.together02be.asset.dto.response.UserStockRes;
import com.back.together02be.asset.service.AssetService;
import com.back.together02be.global.apiRes.ApiRes;
import com.back.together02be.global.security.SecurityUser;
import com.back.together02be.users.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/asset")
@Tag(name = "AssetController", description = "자산 API")
public class AssetController {

    private final AssetService assetService;
    private final UsersService usersService;

    // 초기 데이터 조회 (REST API)
    @GetMapping("/stocks")
    @Operation(summary = "보유 종목 초기 데이터 조회")
    public ResponseEntity<ApiRes<List<UserStockRes>>> getUserStocks(@AuthenticationPrincipal SecurityUser user) {
        List<UserStockRes> userStocks = assetService.getUserStocks(user.getId());
        return ResponseEntity.ok(new ApiRes<>("보유 종목 조회 성공", userStocks));
    }

    // 실시간 시세 구독 (SSE)
    @GetMapping(value = "/stocks/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "보유 종목 실시간 시세 구독")
    public SseEmitter subscribeUserStocks(@AuthenticationPrincipal SecurityUser user) {
        return assetService.subscribeToUserStocks(user.getId());
    }

    @GetMapping("/accounts")
    @Operation(summary = "총 매수금, 예수금, 닉네임 조회")
    public ApiRes<TotalPurchaseRes> totalPrice(@AuthenticationPrincipal SecurityUser user){
        long userId = user.getId();
        String nickname = user.getNickname();
        long totalPurchase = assetService.getTotalAmountByUserId(userId);
        List<StockInfoRes> stockInfos = assetService.getStockInfo(userId);
        long deposit = assetService.getDeposit(userId);

        return new ApiRes<>(
                "조회가 완료되었습니다.",
                new TotalPurchaseRes(nickname,deposit,totalPurchase,stockInfos)
        );
    }
}
