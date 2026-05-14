package com.back.together02be.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BuyReq(
        @NotNull(message = "종목 ID는 필수입니다.")
        Long stockId,

        @NotNull(message = "매수 수량은 필수입니다.")
        @Min(value = 1, message = "매수 수량은 1 이상이어야 합니다.")
        Long quantity,

        @NotNull(message = "예상 체결가는 필수입니다.")
        @Min(value = 1, message = "예상 체결가는 1 이상이어야 합니다.")
        Long expectedPrice
) {
}
