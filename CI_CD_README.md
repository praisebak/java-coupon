# CI/CD 파이프라인 가이드

## 개요

이 프로젝트는 GitHub Actions를 사용한 CI/CD 파이프라인을 포함하고 있습니다.

## 워크플로우 구조

### 1. `ci-cd.yml` - 메인 CI/CD 파이프라인

#### 트리거
- `main`, `develop` 브랜치에 push
- `main`, `develop` 브랜치로 Pull Request

#### Jobs

**backend**
- 백엔드 빌드 및 테스트
- MySQL, Redis 서비스 컨테이너 실행
- Gradle 빌드 및 테스트 실행

**frontend**
- 프론트엔드 빌드 및 테스트
- Node.js 의존성 설치
- TypeScript 타입 체크
- 프로덕션 빌드

**build** (main/develop 브랜치 push 시)
- 백엔드 JAR 파일 생성
- 프론트엔드 빌드 아티팩트 다운로드

**docker-build** (main 브랜치만)
- 백엔드 Docker 이미지 빌드 및 푸시
- 프론트엔드 Docker 이미지 빌드 및 푸시

**deploy-staging** (develop 브랜치)
- 스테이징 환경 배포

**deploy-production** (main 브랜치)
- 프로덕션 환경 배포

### 2. `test.yml` - PR 전용 테스트

- Pull Request 생성 시 자동 실행
- 백엔드 및 프론트엔드 테스트만 실행
- 배포 없음

## 필요한 GitHub Secrets

### Docker 배포용
```
DOCKER_USERNAME        # Docker Hub 사용자명
DOCKER_PASSWORD        # Docker Hub 비밀번호
DOCKER_REGISTRY        # Docker 레지스트리 주소 (예: docker.io/yourusername)
```

### 배포용 (실제 환경에 맞게 추가)
```
STAGING_SSH_KEY        # 스테이징 서버 SSH 키
PROD_SSH_KEY           # 프로덕션 서버 SSH 키
KUBERNETES_CONFIG      # Kubernetes 설정 파일
```

## 로컬 테스트

### Docker Compose로 전체 스택 실행

```bash
# 환경 변수 설정
export DB_USER=user
export DB_PASSWORD=password
export DB_ROOT_PASSWORD=root

# 프로덕션 모드로 실행
docker-compose -f docker-compose.prod.yml up -d
```

### 개별 빌드 테스트

**백엔드:**
```bash
# Docker 이미지 빌드
docker build -f Dockerfile.backend -t coupon-backend:test .

# 실행
docker run -p 8080:8080 coupon-backend:test
```

**프론트엔드:**
```bash
cd frontend

# Docker 이미지 빌드
docker build -t coupon-frontend:test .

# 실행
docker run -p 80:80 coupon-frontend:test
```

## GitHub Actions 로컬 테스트 (선택사항)

```bash
# act 설치 (macOS)
brew install act

# 워크플로우 실행
act -j backend
act -j frontend
```

## 환경별 설정

### 개발 환경
- H2 인메모리 DB
- 로컬 실행

### 스테이징 환경
- MySQL 서버
- Redis 캐시
- 테스트 데이터

### 프로덕션 환경
- MySQL 클러스터 (또는 RDS)
- Redis 클러스터 (또는 ElastiCache)
- 모니터링 설정
- 로그 수집

## 배포 전략

### Blue-Green 배포 (권장)
1. 새 버전을 Green 환경에 배포
2. 헬스체크 확인
3. 트래픽을 Green으로 전환
4. Blue 환경 유지 (롤백 대비)

### Rolling 배포
1. 새 버전을 일부 인스턴스에 배포
2. 점진적으로 모든 인스턴스 업데이트

## 모니터링 및 알림

### GitHub Actions 알림
- Slack 웹훅 연동
- 이메일 알림 (실패 시)

### 애플리케이션 모니터링
- Spring Boot Actuator 엔드포인트
- Prometheus 메트릭 수집
- Grafana 대시보드

## 트러블슈팅

### 빌드 실패
1. 로그 확인: GitHub Actions 페이지에서 상세 로그 확인
2. 로컬 재현: 동일한 환경에서 로컬 빌드 테스트
3. 캐시 초기화: GitHub Actions 캐시 삭제

### 배포 실패
1. 환경 변수 확인
2. 네트워크 연결 확인
3. 이전 버전으로 롤백

## 참고사항

- Docker 이미지는 레지스트리에 푸시되므로 저장 공간 관리 필요
- 민감한 정보는 절대 코드에 커밋하지 말고 Secrets 사용
- 프로덕션 배포는 수동 승인 프로세스 추가 권장

