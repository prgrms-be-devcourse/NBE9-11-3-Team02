/**
 * scripts/smoke.js
 *
 * 목적: 양쪽 프로파일(cache-on / cache-off) 정상 동작 확인
 *      + JVM 워밍업 (JIT 안정화)
 * 결과: 비교 대상 아님 — 통과/실패만 확인
 *
 * 실행:
 *   k6 run loadtest/k6/scripts/smoke.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { pickStock, pickPeriod } from '../data/stocks.js';

export const options = {
    vus: 1,
    duration: '30s',
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<10000'],  // 워밍업이라 느슨하게
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const stockCode = pickStock();
    const period    = pickPeriod();
    const url       = `${BASE_URL}/api/stocks/${stockCode}/chart?period=${period}`;

    const res = http.get(url);

    check(res, {
        'status is 200':  (r) => r.status === 200,
        'body not empty': (r) => r.body && r.body.length > 0,
    });

    sleep(1);
}