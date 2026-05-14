package com.back.together02be.trade.controller;

import com.back.together02be.global.apiRes.ApiRes;
import com.back.together02be.global.security.SecurityUser;
import com.back.together02be.trade.dto.BuyReq;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.dto.request.TradeSellReq;
import com.back.together02be.trade.dto.response.TradeSellRes;
import com.back.together02be.trade.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trades")
@Tag(name = "TradeController", description = "주식 거래 API")
public class TradeController {

    private final TradeService tradeService;

    @Operation(summary = "주식 매수", description = "실시간 현재가 기준으로 매수를 처리합니다. X-Idempotency-Key 헤더 필수.")
    @PostMapping("/buy")
    public ResponseEntity<ApiRes<BuyRes>> buy(
            @AuthenticationPrincipal SecurityUser user,
            @Parameter(description = "중복 요청 방지용 UUID (버튼 클릭마다 새로 생성)", required = true)
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BuyReq request
    ) {
        BuyRes response = tradeService.buy(user.getId(), idempotencyKey, request);
        return ResponseEntity.ok(new ApiRes<>("매수가 완료되었습니다.", response));
    }

    //주식 매도
    @Operation(summary = "주식 매도", description = "실시간 현재가 기준으로 매도를 처리합니다. X-Idempotency-Key 헤더 필수.")
    @PostMapping("/sell")
    public ResponseEntity<ApiRes<TradeSellRes>> sell(
            @AuthenticationPrincipal SecurityUser user,
            @Parameter(description = "중복 요청 방지용 UUID (버튼 클릭마다 새로 생성)", required = true)
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid TradeSellReq req
    ){
        TradeSellRes res = tradeService.sell(user.getId(),idempotencyKey,req);
        return ResponseEntity.ok(new ApiRes<>("매도가 완료되었습니다.", res));
    }
}
