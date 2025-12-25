# K6 부하테스트 가이드

## 목적
쿠폰 발급 API의 동시성 처리 성능을 검증합니다. 낙관적 락을 통한 재고 관리가 정상 동작하는지 확인합니다.

## 사전 준비

### 1. K6 설치
```bash
# macOS
brew install k6

# Linux
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Windows
# https://k6.io/docs/getting-started/installation/
```

### 2. 테스트 데이터 준비
- 쿠폰 ID 100번이 존재해야 함
- 멤버 ID 1~10000 사이의 멤버가 존재해야 함

### 3. 백엔드 서버 실행
```bash
# 로컬에서 실행
./gradlew bootRun

# 또는 docker-compose
docker-compose up
```

## 테스트 실행

### 기본 실행
```bash
k6 run k6-load-test.js
```

### 옵션 커스터마이징
```bash
# 쿠폰 ID 변경
k6 run -e COUPON_ID=100 k6-load-test.js

# 요청률 변경 (초당 1000 요청)
k6 run --vus 1000 --rate 1000 k6-load-test.js

# 지속 시간 변경 (5분)
k6 run --duration 5m k6-load-test.js
```

### 결과 파일 저장
```bash
# JSON 형식으로 저장
k6 run --out json=load-test-results.json k6-load-test.js

# InfluxDB로 전송 (선택적)
k6 run --out influxdb=http://localhost:8086/k6 k6-load-test.js
```

## 테스트 시나리오 설명

### constant-arrival-rate 실행자
- **초당 1666 요청**: 약 100,000 요청/분
- **1분 지속**: 총 약 100,000 요청
- **VUs**: 1000~5000개 가상 사용자

### 예상 결과
1. **성공 (200/201)**: 정상 발급
2. **409 Conflict**: 재고 부족 또는 중복 발급 (정상 동작)
3. **400 Bad Request**: 잘못된 요청 (멤버/쿠폰 없음)
4. **500 Server Error**: 서버 에러 (문제 발생)

## 성능 임계값

```javascript
thresholds: {
  http_req_duration: ['p(95)<2000', 'p(99)<5000'], // 응답 시간
  http_req_failed: ['rate<0.05'],                   // 에러율 5% 미만
  coupon_success_rate: ['rate>0.8'],                // 성공률 80% 이상
}
```

## 메트릭 설명

### 기본 메트릭
- `http_req_duration`: HTTP 요청 지속 시간
- `http_req_failed`: HTTP 요청 실패율
- `vus`: 활성 가상 사용자 수

### 커스텀 메트릭
- `coupon_success_issued`: 성공적으로 발급된 쿠폰 수
- `coupon_fail_sold_out`: 재고 부족으로 실패한 요청 수
- `coupon_fail_duplicate`: 중복 발급으로 실패한 요청 수
- `coupon_fail_server_error`: 서버 에러 수
- `coupon_success_rate`: 전체 성공률

## 결과 해석

### 정상 동작 시
- 성공률: 80% 이상 (재고 부족으로 일부 실패는 정상)
- 응답 시간 p95: 2초 이내
- 409 에러: 정상 (낙관적 락으로 인한 재시도 필요)
- 500 에러: 0 또는 최소화

### 문제 발생 시
- 500 에러가 많음: 서버 코드/DB 문제
- 응답 시간 초과: DB 커넥션 풀 부족 또는 쿼리 최적화 필요
- 성공률 낮음: 재고 관리 로직 문제 가능

## 스크립트 커스터마이징

### 쿠폰 ID 변경
```javascript
const COUPON_ID = 100; // 원하는 쿠폰 ID로 변경
```

### User ID 범위 변경
```javascript
const MIN_USER_ID = 1;
const MAX_USER_ID = 10000; // 원하는 범위로 변경
```

### 요청률 조정
```javascript
rate: 1666,  // 초당 요청 수
duration: '1m',  // 테스트 지속 시간
```

### API URL 변경 (프로덕션 테스트)
```javascript
const API_URL = 'http://your-server:8080/member-coupons';
```

## 주의사항

1. **데이터 준비**: 테스트 전 쿠폰과 멤버 데이터가 충분히 준비되어 있어야 함
2. **서버 모니터링**: 테스트 중 서버 리소스(CPU, 메모리, DB 커넥션) 모니터링 권장
3. **DB 연결**: DB 커넥션 풀 설정 확인 필요
4. **네트워크**: 로컬 테스트 시 localhost 사용, 원격은 적절한 URL 설정

## 트러블슈팅

### "connection refused" 에러
- 백엔드 서버가 실행 중인지 확인
- API_URL이 올바른지 확인

### 높은 에러율
- DB 커넥션 풀 크기 확인
- 서버 리소스 확인 (CPU, 메모리)
- 로그에서 스택 트레이스 확인

### 느린 응답 시간
- DB 쿼리 최적화 필요
- 인덱스 확인
- 캐시 활용 검토


