# 백엔드 컨테이너 재시작 문제 해결 가이드

## 문제 증상
```
CONTAINER ID   IMAGE             COMMAND                  CREATED         STATUS
1151a41f2192   coupon-backend    "java -jar app.jar"      5 minutes ago   Restarting (1)
```

## 진단 방법

### 1. 컨테이너 로그 확인
```bash
# 최근 로그 확인
docker logs coupon-backend

# 실시간 로그 확인
docker logs -f coupon-backend

# 마지막 100줄만 확인
docker logs --tail 100 coupon-backend
```

### 2. 컨테이너 상태 상세 확인
```bash
docker inspect coupon-backend | grep -A 10 "State"
```

## 가능한 원인 및 해결 방법

### 원인 1: app.jar 파일이 없음
```bash
# 원격 서버에서 확인
cd /home/ubuntu/coupon
ls -lh app.jar

# 파일이 없거나 크기가 0이면
# CI/CD 배포 과정에서 JAR 파일 복사가 실패했을 수 있음
```

**해결:**
- CI/CD 로그에서 JAR 파일 복사 단계 확인
- 수동으로 다시 복사: `scp app.jar user@server:/home/ubuntu/coupon/`

### 원인 2: 환경 변수가 설정되지 않음
```bash
# .env 파일 확인
cat /home/ubuntu/coupon/.env

# docker-compose 실행 시 환경 변수 확인
cd /home/ubuntu/coupon
docker-compose -f docker-compose.prod.yml config
```

**해결:**
- `.env` 파일이 있으면: `docker-compose --env-file .env -f docker-compose.prod.yml up -d`
- `.env` 파일이 없으면: CI/CD 스크립트에서 생성하는 단계 확인

### 원인 3: DB 연결 실패
```bash
# MySQL 컨테이너 상태 확인
docker logs coupon-mysql

# 네트워크 연결 확인
docker exec coupon-backend ping mysql
```

**해결:**
- MySQL 컨테이너가 정상 실행 중인지 확인
- 데이터베이스 이름, 사용자, 비밀번호가 올바른지 확인
- MySQL이 완전히 준비될 때까지 대기하도록 depends_on 조건 추가

### 원인 4: 포트 충돌
```bash
# 8080 포트 사용 확인
sudo netstat -tulpn | grep 8080
# 또는
sudo lsof -i :8080
```

**해결:**
- 다른 프로세스가 8080 포트를 사용 중이면 종료
- 또는 docker-compose.yml에서 포트 변경

### 원인 5: 볼륨 마운트 문제
```bash
# 컨테이너 내부에서 파일 확인
docker exec coupon-backend ls -lh /app/app.jar

# 호스트에서 파일 확인
ls -lh /home/ubuntu/coupon/app.jar
```

**해결:**
- 파일 권한 확인: `chmod 644 app.jar`
- 파일 소유자 확인: `chown ubuntu:ubuntu app.jar`

## 즉시 실행 가능한 해결 스크립트

```bash
#!/bin/bash
cd /home/ubuntu/coupon

# 1. 로그 확인
echo "=== 컨테이너 로그 ==="
docker logs --tail 50 coupon-backend

# 2. 파일 확인
echo "=== 파일 확인 ==="
ls -lh app.jar
echo "=== .env 확인 ==="
cat .env 2>/dev/null || echo ".env 파일 없음"

# 3. 환경 변수 확인
echo "=== 환경 변수 확인 ==="
docker exec coupon-backend env | grep SPRING

# 4. 컨테이너 재시작
echo "=== 컨테이너 재시작 ==="
docker-compose -f docker-compose.prod.yml restart backend
sleep 5
docker logs --tail 30 coupon-backend
```

## docker-compose.prod.yml 개선 제안

환경 변수를 .env 파일에서 읽도록 명시:

```yaml
services:
  backend:
    # ...
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/${DB_NAME}
      # ...
```

또는 환경 변수를 직접 참조하지 않고 .env 파일의 값 사용:

```yaml
services:
  backend:
    # ...
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
      - SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}
      - SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD}
      # ...
```


