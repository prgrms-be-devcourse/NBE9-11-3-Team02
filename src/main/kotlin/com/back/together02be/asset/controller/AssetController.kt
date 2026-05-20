package com.back.together02be.asset.controller

import com.back.together02be.asset.dto.response.TotalPurchaseRes
import com.back.together02be.asset.dto.response.UserStockRes
import com.back.together02be.asset.service.AssetService
import com.back.together02be.global.apiRes.ApiRes
import com.back.together02be.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/asset")
@Tag(name = "AssetController", description = "자산 API")
class AssetController(
    private val assetService: AssetService
) {

    // 초기 데이터 조회 (REST API)
    @GetMapping("/stocks")
    @Operation(summary = "보유 종목 초기 데이터 조회")
    fun getUserStocks(
        @AuthenticationPrincipal user: SecurityUser
    ): ResponseEntity<ApiRes<List<UserStockRes>>> {
        val userStocks = assetService.getUserStocks(user.id) // Getter 대신 프로퍼티(id) 직접 접근
        return ResponseEntity.ok(ApiRes("보유 종목 조회 성공", userStocks)) // new 키워드 생략
    }

    // 실시간 시세 구독 (SSE)
    @GetMapping("/stocks/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "보유 종목 실시간 시세 구독")
    fun subscribeUserStocks(
        @AuthenticationPrincipal user: SecurityUser
    ): SseEmitter = assetService.subscribeToUserStocks(user.id)

    @GetMapping("/accounts")
    @Operation(summary = "총 매수금, 예수금, 닉네임 조회")
    fun totalPrice(
        @AuthenticationPrincipal user: SecurityUser
    ): ApiRes<TotalPurchaseRes> {
        val userId = user.id
        val nickname = user.nickname

        val totalPurchase = assetService.getTotalAmountByUserId(userId)
        val stockInfos = assetService.getStockInfo(userId)
        val deposit = assetService.getDeposit(userId)

        return ApiRes(
            "조회가 완료되었습니다.",
            TotalPurchaseRes(nickname, deposit, totalPurchase, stockInfos)
        )
    }
}