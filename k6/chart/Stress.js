/**
 * scripts/stress.js
 *
 * 목적: 한계점(breakpoint) 도출 — 어느 VU에서 시스템이 무너지는가
 * thresholds: 의도적으로 느슨하게 (버티는 지점까지 밀어붙이는 게 목적)
 *
 * 실행:
 *   k6 run --out json=results/cache-on-stress.json loadtest/k6/scripts/stress.js
 *   k6 run --out json=results/cache-off-stress.json loadtest/k6/scripts/stress.js
 *
 * 주의:
 *   - M1 Pro 단일 머신에서 VU 100+ 구간부터 k6 자체가 CPU 경합할 수 있음
 *   - --http-debug 옵션 절대 사용 금지 (k6 병목 유발)
 *   - 에러율이 50% 초과하면 측정 의미 없음 → 그 직전 VU를 한계점으로 기록
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { pickStock, pickPeriod } from '../data/stocks.js';

const chartLatency  = new Trend('chart_latency_ms', true);
const chartErrors   = new Counter('chart_errors');
const chartErrorRate = new Rate('chart_error_rate');

export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: 10  },
                { duration: '1m',  target: 30  },
                { duration: '1m',  target: 50  },
                { duration: '1m',  target: 80  },
                { duration: '1m',  target: 100 },
                { duration: '2m',  target: 100 },  // 100 VU sustain
                { duration: '1m',  target: 150 },
                { duration: '2m',  target: 150 },  // 150 VU sustain
                { duration: '1m',  target: 200 },
                { duration: '2m',  target: 200 },  // 200 VU sustain
                { duration: '1m',  target: 0   },  // 쿨다운
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        // 느슨하게 — 한계점 탐색이 목적
        http_req_failed:   ['rate<0.5'],
        http_req_duration: ['p(99)<30000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const stockCode = pickStock();
    const period    = pickPeriod();
    const url       = `${BASE_URL}/api/stocks/${stockCode}/chart?period=${period}`;

    const res = http.get(url, {
        tags: { name: 'GET /api/stocks/:code/chart', period },
        timeout: '15s',
    });

    chartLatency.add(res.timings.duration);

    const ok = check(res, {
        'status is 200':  (r) => r.status === 200,
        'body not empty': (r) => r.body && r.body.length > 0,
    });

    if (!ok) {
        chartErrors.add(1);
        chartErrorRate.add(1);
    } else {
        chartErrorRate.add(0);
    }

    sleep(Math.random() * 0.3 + 0.2);  // stress는 think time 짧게 (0.2~0.5초)
}