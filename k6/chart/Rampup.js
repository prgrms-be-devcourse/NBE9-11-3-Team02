/**
 * scripts/rampup.js
 *
 * 목적: 캐시 ON vs OFF 성능 비교 — 메인 측정 스크립트
 * 측정 지표: p50/p95/p99 레이턴시, RPS, 에러율
 *
 * 실행:
 *   # 캐시 ON
 *   k6 run --out json=results/cache-on-rampup.json loadtest/k6/scripts/rampup.js
 *
 *   # 캐시 OFF (앱 재시작 후)
 *   k6 run --out json=results/cache-off-rampup.json loadtest/k6/scripts/rampup.js
 *
 *   # BASE_URL 변경 시
 *   k6 run -e BASE_URL=http://192.168.0.10:8080 --out json=... rampup.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { pickStock, pickPeriod } from '../data/stocks.js';

// 커스텀 메트릭 — k6 기본 http_req_duration과 별도로 태그별 분석용
const chartLatency = new Trend('chart_latency_ms', true);
const chartErrors  = new Counter('chart_errors');

export const options = {
    scenarios: {
        rampup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 10 },   // 워밍업
                { duration: '2m', target: 30 },   // 중간 부하
                { duration: '2m', target: 50 },   // 목표 부하로 상승
                { duration: '5m', target: 50 },   // sustain — 핵심 측정 구간
                { duration: '1m', target: 0 },    // 쿨다운
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        http_req_failed:    ['rate<0.01'],     // 에러율 1% 미만
        http_req_duration:  ['p(95)<2000', 'p(99)<5000'],
        chart_latency_ms:   ['p(95)<2000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const stockCode = pickStock();
    const period    = pickPeriod();
    const url       = `${BASE_URL}/api/stocks/${stockCode}/chart?period=${period}`;

    const res = http.get(url, {
        tags: {
            name:   'GET /api/stocks/:code/chart',
            period: period,
        },
    });

    chartLatency.add(res.timings.duration);

    const ok = check(res, {
        'status is 200':  (r) => r.status === 200,
        'body not empty': (r) => r.body && r.body.length > 0,
    });

    if (!ok) chartErrors.add(1);

    // think time: 0.5~1초 (현실적인 사용자 간격)
    sleep(Math.random() * 0.5 + 0.5);
}