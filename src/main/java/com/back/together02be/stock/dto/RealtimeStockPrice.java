package com.back.together02be.stock.dto;

import com.back.together02be.infra.kis.rest.dto.KisPriceRes;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RealtimeStockPrice { // todo stockSubscriptionInitializer 바꿀 때 변환
	private String stockCode;      // 종목코드
	private String price;          // 현재가
	private String changeSign;     // 전일 대비 부호 (1:상한 2:상승 3:보합 4:하한 5:하락)
	private String change;         // 전일 대비
	private String changeRate;     // 전일 대비율
	private String tradeTime;	   // 체결시간

	// REST 시딩용 변환
	public static RealtimeStockPrice fromRest(String stockCode, KisPriceRes.Output output) {
		return RealtimeStockPrice.builder()
			.stockCode(stockCode)
			.price(output.getCurrentPrice()) // 현재가
			.changeSign(output.getChangeSign()) // 전일 대비 부호
			.change(output.getPriceDifference()) // 전일 대비 금액
			.changeRate(output.getChangeRate()) // 전일 대비율
			.tradeTime(null) // rest-> 체결 시간 없음
			.build();
	}
}
