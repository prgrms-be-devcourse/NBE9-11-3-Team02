package com.back.together02be.trade.controller

import com.back.together02be.global.apiRes.ApiRes
import com.back.together02be.global.security.SecurityUser
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.dto.BuyRes
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.dto.response.TradeSellRes
import com.back.together02be.trade.service.TradeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/trades")
@Tag(name = "TradeController", description = "주식 거래 API")
class TradeController(private val tradeService: TradeService) {

    @Operation(summary = "주식 매수", description = "실시간 현재가 기준으로 매수를 처리합니다. X-Idempotency-Key 헤더 필수.")
    @PostMapping("/buy")
    fun buy(
        @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "중복 요청 방지용 UUID (버튼 클릭마다 새로 생성)", required = true)
        @RequestHeader("X-Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: BuyReq,
    ): ResponseEntity<ApiRes<BuyRes>> {
        val response = tradeService.buy(user.id, idempotencyKey, request)
        return ResponseEntity.ok(ApiRes("매수가 완료되었습니다.", response))
    }

    @Operation(summary = "주식 매도", description = "실시간 현재가 기준으로 매도를 처리합니다. X-Idempotency-Key 헤더 필수.")
    @PostMapping("/sell")
    fun sell(
        @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "중복 요청 방지용 UUID (버튼 클릭마다 새로 생성)", required = true)
        @RequestHeader("X-Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody req: TradeSellReq,
    ): ResponseEntity<ApiRes<TradeSellRes>> {
        val res = tradeService.sell(user.id, idempotencyKey, req)
        return ResponseEntity.ok(ApiRes("매도가 완료되었습니다.", res))
    }
}
