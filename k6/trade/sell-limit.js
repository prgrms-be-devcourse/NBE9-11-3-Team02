/*
docker compose up -d

docker compose -f docker-compose.k6.yml \
run --rm k6 run /scripts/trade/sell-limit.js

 */

import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL='http://host.docker.internal:8080';

export const options={

    scenarios:{
        limit:{
            executor:'ramping-arrival-rate',

            startRate:100,

            stages:[
                {target:300,duration:'30s'},
                {target:500,duration:'30s'},
                {target:1000,duration:'30s'}
            ],

            timeUnit:'1s',

            preAllocatedVUs:300,
            maxVUs:1000
        }
    }
};
export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/users/login`,
        JSON.stringify({ username: 'plus1', password: 'password01' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    console.log("Setup Login Status:", loginRes.status);
    console.log("Setup Login Body:", loginRes.body);

    const token = loginRes.json('data.accessToken');

    console.log("Parsed Token:", token);

    return { token };
}
export default function(data){

    const payload = JSON.stringify({
        stockId: 1,
        quantity: 1,
        expectedPrice: 70000
    });

    // 헤더에 인증 토큰(Bearer)과 동시성 처리를 위한 멱등성 키 추가
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${data.token}`, // setup에서 받은 토큰 바인딩
            'X-Idempotency-Key': uuidv4()           // 대량 요청 시 중복 처리 방지
        }
    };

    // 실제 매도 API 호출
    const res = http.post(
        `${BASE_URL}/api/trades/sell`,
        payload,
        params
    );

    // 높은 TPS(최대 1000) 테스트이므로 디버그용 console.log는 제외했습니다.
    // (로그가 너무 많이 찍히면 k6 실행 환경 자체가 느려질 수 있습니다)

    // 안정적인 모니터링을 위한 응답 검증(Check) 추가
    check(res, {
        'status is 200': (r) => r.status === 200,
        'not server error': (r) => r.status !== 500
    });

}