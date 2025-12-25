# 백엔드 컨테이너 재시작 원인 분석

## 발견된 문제점

### 1. 포트 불일치 (가장 가능성 높음)
- **application.properties**: `server.port=8081`
- **docker-compose.prod.yml**: `8080:8080`
- **Health Check**: `8080`

애플리케이션이 8081 포트에서 실행되지만, docker-compose와 health check는 8080을 기대합니다.

### 2. 환경변수 이름 불일치
- **application.properties**: `spring.datasource.url=jdbc:mysql://${DATABASE_URL:localhost}:3306/...`
- **docker-compose.prod.yml**: `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/${DB_NAME}`

Spring Boot는 `SPRING_DATASOURCE_URL` 환경변수를 직접 사용할 수 있지만, application.properties에서 `${DATABASE_URL}`을 참조하고 있습니다.

### 3. Redis 포트 불일치
- **application.properties**: `spring.data.redis.port=6380`
- **docker-compose.prod.yml**: Redis 컨테이너는 `6379` 포트 사용

애플리케이션이 6380 포트를 찾지만, Redis는 6379에서 실행 중입니다.

### 4. Redis Host 설정 오류
- **application.properties**: `spring.data.redis.host=${DATABASE_URL:localhost}`
- Redis host인데 `DATABASE_URL`을 사용하는 것은 버그로 보입니다.

### 5. DB 사용자명 하드코딩
- **application.properties**: `spring.datasource.username=user`
- **docker-compose.prod.yml**: `${DB_USER}` 사용

환경변수로 주입하지 않고 하드코딩되어 있습니다.

## 해결 방법

### 방법 1: application.properties 수정 (권장)
환경변수를 우선 사용하도록 수정:

```properties
server.port=${SERVER_PORT:8080}

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/coupon_db?serverTimezone=UTC&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:user}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:1234}

spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}
spring.data.redis.port=${SPRING_DATA_REDIS_PORT:6379}
```

### 방법 2: docker-compose.prod.yml의 환경변수 수정
application.properties가 기대하는 환경변수 이름으로 변경:

```yaml
environment:
  - SERVER_PORT=8080
  - DATABASE_URL=mysql
  - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
  - SPRING_DATA_REDIS_HOST=redis
  - SPRING_DATA_REDIS_PORT=6379
```

## 가장 가능성 높은 실패 원인 (우선순위)

1. **포트 불일치 (8081 vs 8080)** - 애플리케이션은 8081에서 실행되지만 헬스체크는 8080에서 시도
2. **Redis 연결 실패** - 포트 불일치 (6380 vs 6379) 및 host 설정 오류
3. **DB 연결 실패** - 환경변수 이름 불일치로 인한 연결 정보 오류
4. **환경변수 미설정** - .env 파일이 제대로 로드되지 않았을 가능성

## 즉시 확인 사항

```bash
# 1. 컨테이너 로그 확인
docker logs coupon-backend

# 2. 포트 확인
docker exec coupon-backend netstat -tulpn | grep LISTEN

# 3. 환경변수 확인
docker exec coupon-backend env | grep -E "SPRING|SERVER|DATABASE|REDIS"
```


