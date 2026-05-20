/*
docker compose up -d

docker compose -f docker-compose.k6.yml \
run --rm k6 run /scripts/trade/sell-peak.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL='http://host.docker.internal:8080';

export const options={

    scenarios:{

        peak:{

            executor:'ramping-arrival-rate',

            startRate:100,

            stages:[
                {target:300,duration:'1m'},
                {target:500,duration:'2m'}
            ],

            timeUnit:'1s',

            preAllocatedVUs:100,
            maxVUs:500
        }
    },

    thresholds:{
        http_req_duration:['p(95)<500'],
        http_req_failed:['rate<0.01']
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


    const params={
        headers:{
            'Content-Type':'application/json',
            'Authorization': `Bearer ${data.token}`, // setup에서 받은 토큰 바인딩
            'X-Idempotency-Key': uuidv4()
        }
    };

    const res=http.post(
        `${BASE_URL}/api/trades/sell`,
        payload,
        params
    );

    check(res,{
        'status 200':r=>r.status===200,
        'not server error': (r) => r.status !== 500
    });

}