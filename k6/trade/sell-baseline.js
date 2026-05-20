// 실행
// docker compose up -d
// docker compose -f docker-compose.k6.yml run --rm k6 run /scripts/trade/sell-baseline.js
// localhost:3001

import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = 'http://host.docker.internal:8080';

export const options = {
    scenarios: {
        baseline: {
            executor: 'constant-arrival-rate',

            rate: 100,     // 100 TPS
            timeUnit: '1s',
            duration: '1m',

            preAllocatedVUs: 50,
            maxVUs: 200
        }
    },

    thresholds: {
        http_req_duration: ['p(95)<200'],
        http_req_failed: ['rate<0.01']
    }
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/users/login`,
        JSON.stringify({ username: 'plus1', password: 'password01' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const token = loginRes.json('data.accessToken');
    return { token };
}
export default function (data) {

    const payload = JSON.stringify({
        stockId: 1,
        quantity: 1,
        expectedPrice: 70000
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${data.token}`,
            'X-Idempotency-Key': uuidv4()
        }
    };

    const res =
        http.post(
            `${BASE_URL}/api/trades/sell`,
            payload,
            params
        );

    console.log(
        "status=",res.status,
        "body=",res.body
    );

    check(res,{
        'status is 200': (r)=>r.status===200,
        'not server error': (r) => r.status !== 500
    });
}