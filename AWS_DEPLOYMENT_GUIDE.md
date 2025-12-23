# AWS 배포 가이드

## GitHub Secrets 설정 목록

### 필수 AWS 인증 정보

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `AWS_ACCESS_KEY_ID` | AWS Access Key ID | `AKIAIOSFODNN7EXAMPLE` |
| `AWS_SECRET_ACCESS_KEY` | AWS Secret Access Key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` (서울) |
| `AWS_ACCOUNT_ID` | AWS 계정 ID (12자리 숫자) | `123456789012` |

### ECR (Elastic Container Registry) 설정

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `ECR_REPOSITORY_BACKEND` | 백엔드 ECR 레포지토리 이름 | `coupon-backend` |
| `ECR_REPOSITORY_FRONTEND` | 프론트엔드 ECR 레포지토리 이름 | `coupon-frontend` |

### EC2 서버 접속 정보

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `EC2_HOST` | EC2 인스턴스 Public IP 또는 DNS | `54.180.123.45` 또는 `ec2-54-180-123-45.ap-northeast-2.compute.amazonaws.com` |
| `EC2_USER` | EC2 SSH 사용자명 | `ec2-user` (Amazon Linux) 또는 `ubuntu` (Ubuntu) |
| `EC2_SSH_PRIVATE_KEY` | EC2 SSH Private Key (전체 내용) | `-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----` |

### 데이터베이스 설정

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `DB_HOST` | RDS 엔드포인트 또는 EC2 내부 MySQL | `coupon-db.c1234567890.ap-northeast-2.rds.amazonaws.com` |
| `DB_NAME` | 데이터베이스 이름 | `coupon_db` |
| `DB_USER` | 데이터베이스 사용자명 | `admin` |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | `your-secure-password` |

### Redis 설정 (ElastiCache 사용 시)

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `REDIS_HOST` | ElastiCache 엔드포인트 | `coupon-redis.abc123.cache.amazonaws.com` |
| `REDIS_PORT` | Redis 포트 | `6379` |

## GitHub Secrets 설정 방법

### 1. GitHub 저장소에서 Secrets 추가

1. GitHub 저장소 페이지 접속
2. **Settings** → **Secrets and variables** → **Actions** 클릭
3. **New repository secret** 클릭
4. 위의 표에 있는 각 Secret을 하나씩 추가

### 2. SSH Private Key 설정 주의사항

`EC2_SSH_PRIVATE_KEY`는 다음과 같이 설정:

```
-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEA...
(전체 Private Key 내용)
...
-----END RSA PRIVATE KEY-----
```

**중요:**
- 키 파일의 전체 내용을 복사 (줄바꿈 포함)
- GitHub Secrets는 여러 줄 입력 가능 (New Line 포함)
- `.pem` 파일의 전체 내용을 그대로 복사

### 3. AWS 계정 ID 확인 방법

```bash
aws sts get-caller-identity --query Account --output text
```

또는 AWS 콘솔 우측 상단 계정 이름 클릭 → 계정 ID 확인

## AWS 리소스 사전 준비

### 1. ECR 레포지토리 생성

```bash
# 백엔드 레포지토리
aws ecr create-repository \
  --repository-name coupon-backend \
  --region ap-northeast-2

# 프론트엔드 레포지토리
aws ecr create-repository \
  --repository-name coupon-frontend \
  --region ap-northeast-2
```

### 2. EC2 인스턴스 설정

#### 필수 설치 패키지

EC2 인스턴스에 SSH 접속 후:

```bash
# Docker 설치
sudo yum update -y
sudo yum install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -a -G docker ec2-user

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# AWS CLI 설치 (ECR 로그인용)
sudo yum install aws-cli -y

# Git 설치
sudo yum install git -y

# 프로젝트 디렉토리 생성
sudo mkdir -p /opt/coupon-system
sudo chown ec2-user:ec2-user /opt/coupon-system
```

#### EC2 IAM 역할 설정 (권장)

EC2 인스턴스에 IAM 역할을 부여하여 Access Key 없이 ECR 접근:

1. IAM 역할 생성:
   - 역할 이름: `EC2ECRAccessRole`
   - 정책: `AmazonEC2ContainerRegistryReadOnly`

2. EC2 인스턴스에 역할 연결:
   - EC2 콘솔 → 인스턴스 선택 → Actions → Security → Modify IAM role

3. GitHub Secrets에서 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` 제거 가능
   - (단, GitHub Actions에서 EC2 접속할 때는 여전히 필요할 수 있음)

### 3. RDS 데이터베이스 생성 (선택)

```bash
aws rds create-db-instance \
  --db-instance-identifier coupon-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --master-username admin \
  --master-user-password YourPassword123! \
  --allocated-storage 20 \
  --region ap-northeast-2
```

### 4. ElastiCache Redis 생성 (선택)

```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id coupon-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --region ap-northeast-2
```

## 보안 그룹 설정

### EC2 보안 그룹

| 타입 | 프로토콜 | 포트 | 소스 |
|------|---------|------|------|
| SSH | TCP | 22 | GitHub Actions IP (또는 특정 IP) |
| HTTP | TCP | 80 | 0.0.0.0/0 |
| Custom TCP | TCP | 8080 | 0.0.0.0/0 (또는 특정 IP) |

### RDS 보안 그룹

| 타입 | 프로토콜 | 포트 | 소스 |
|------|---------|------|------|
| MySQL/Aurora | TCP | 3306 | EC2 보안 그룹 ID |

### ElastiCache 보안 그룹

| 타입 | 프로토콜 | 포트 | 소스 |
|------|---------|------|------|
| Custom TCP | TCP | 6379 | EC2 보안 그룹 ID |

## 배포 테스트

### 로컬에서 Docker 이미지 빌드 테스트

```bash
# 백엔드
docker build -f Dockerfile.backend -t coupon-backend:test .

# 프론트엔드
docker build -f frontend/Dockerfile -t coupon-frontend:test ./frontend
```

### ECR에 직접 푸시 테스트

```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com

# 태그 설정
docker tag coupon-backend:test <AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/coupon-backend:test

# 푸시
docker push <AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/coupon-backend:test
```

## 트러블슈팅

### GitHub Actions 로그 확인

1. GitHub 저장소 → **Actions** 탭
2. 실행된 워크플로우 클릭
3. 각 Job의 로그 확인

### EC2 접속 문제

```bash
# SSH 키 권한 확인
chmod 400 ~/.ssh/id_rsa

# 연결 테스트
ssh -i ~/.ssh/id_rsa ec2-user@<EC2_HOST>
```

### ECR 푸시 실패

- IAM 권한 확인: `AmazonEC2ContainerRegistryFullAccess` 정책 필요
- 리전 일치 확인: AWS_REGION과 실제 ECR 리전 일치

### Docker Compose 실행 실패

EC2에 SSH 접속 후:

```bash
# 로그 확인
cd /opt/coupon-system
docker-compose -f docker-compose.aws.yml logs

# 컨테이너 상태 확인
docker ps -a
```

## 비용 최적화

### 무료 티어 활용

- **EC2**: t2.micro (1년 무료)
- **RDS**: db.t2.micro (12개월 무료)
- **ECR**: 저장소 당 500MB 무료 (이미지 스토리지)

### 예상 월 비용 (무료 티어 이후)

- EC2 t2.micro: ~$10
- RDS db.t2.micro: ~$15
- 데이터 전송: ~$5
- **총 약 $30/월**

### 비용 절감 팁

1. 스팟 인스턴스 사용 (프로덕션 비권장)
2. RDS 대신 EC2에 MySQL 직접 설치
3. ElastiCache 대신 EC2에 Redis 직접 설치
4. 사용하지 않을 때 인스턴스 중지

