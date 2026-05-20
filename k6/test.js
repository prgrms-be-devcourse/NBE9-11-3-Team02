// 실행 명령어
// docker compose up -d
// docker compose -f docker-compose.k6.yml run --rm k6 run /scripts/test.js
// localhost:3001 접속
// 도메인별로 폴더 나눠서 테스트 진행해주세요.
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 3,
    duration: '10s',
};

export default function () {
    const res = http.get('https://httpbin.org/get');
    check(res, { 'status 200': (r) => r.status === 200 });
}