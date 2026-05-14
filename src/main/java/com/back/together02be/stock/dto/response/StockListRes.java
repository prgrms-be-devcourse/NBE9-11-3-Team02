package com.back.together02be.stock.dto.response;

//프론트에 보내기 위해 데이터 형태를 따로 정의
//DB (Stock) -> 가공 -> API 응답
public record StockListRes(
        Long id,
        String stockCode,
        String stockName,
        Long currentPrice,
        Double changeRate
) {
}
