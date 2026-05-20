/**
 * data/stocks.js
 *
 * 종목 풀 = BaseInitData.work3()에서 DB에 주입된 20개와 동일하게 맞춤
 * Pareto 분포: 상위 10개(HOT)가 트래픽 80% 점유
 *
 * ChartPeriod: "3M" | "1Y" 두 가지만 존재
 */

// 상위 10개 — 80% 확률
const HOT_STOCKS = [
    '005930', // 삼성전자
    '000660', // SK하이닉스
    '035420', // NAVER
    '035720', // 카카오
    '051910', // LG화학
    '006400', // 삼성SDI
    '068270', // 셀트리온
    '105560', // KB금융
    '055550', // 신한지주
    '005380', // 현대차
];

// 나머지 10개 — 20% 확률
const NORMAL_STOCKS = [
    '012330', // 현대모비스
    '034730', // SK
    '066570', // LG전자
    '003670', // 포스코퓨처엠
    '096770', // SK이노베이션
    '015760', // 한국전력
    '032830', // 삼성생명
    '086790', // 하나금융지주
    '207940', // 삼성바이오로직스
    '373220', // LG에너지솔루션
];

export const PERIODS = ['3M', '1Y'];

/**
 * Pareto 분포로 종목 선택
 * 80% → HOT_STOCKS (캐시 히트율 높음)
 * 20% → NORMAL_STOCKS
 */
export function pickStock() {
    if (Math.random() < 0.8) {
        return HOT_STOCKS[Math.floor(Math.random() * HOT_STOCKS.length)];
    }
    return NORMAL_STOCKS[Math.floor(Math.random() * NORMAL_STOCKS.length)];
}

export function pickPeriod() {
    return PERIODS[Math.floor(Math.random() * PERIODS.length)];
}