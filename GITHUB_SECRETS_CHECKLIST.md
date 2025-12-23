# GitHub Secrets 설정 체크리스트 (OCI 배포용 - SSH PEM 방식)

## 📋 설정 방법

GitHub 저장소 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

---

## ✅ 필수 Secrets 목록

### 1. OCI 서버 접속 정보 (SSH)

| Secret 이름 | 값 예시 | 설명 |
|------------|---------|------|
| `OCI_HOST` | `129.213.45.67` 또는 `instance-name.subnet.vcn.oraclevcn.com` | OCI 인스턴스 Public IP 또는 DNS |
| `OCI_USER` | `opc` (Oracle Linux) 또는 `ubuntu` (Ubuntu) | OCI 인스턴스 SSH 사용자명 |
| `OCI_SSH_PRIVATE_KEY` | 전체 `.pem` 또는 `.key` 파일 내용 (아래 참고) | OCI SSH Private Key |

**SSH Private Key 설정 주의:**
- `.pem` 또는 `.key` 파일의 전체 내용을 복사
- 줄바꿈 포함하여 그대로 붙여넣기
- 예시:
  ```
  -----BEGIN RSA PRIVATE KEY-----
  MIIEpAIBAAKCAQEA...
  (전체 키 내용)
  ...
  -----END RSA PRIVATE KEY-----
  ```

**OCI 사용자명 확인:**
- Oracle Linux: `opc`
- Ubuntu: `ubuntu`
- CentOS: `opc`

---

### 2. 데이터베이스 설정

| Secret 이름 | 값 예시 | 설명 |
|------------|---------|------|
| `DB_HOST` | `localhost` 또는 `mysql` | OCI MySQL 인스턴스 또는 Docker 서비스명 |
| `DB_NAME` | `coupon_db` | 데이터베이스 이름 |
| `DB_USER` | `root` 또는 `admin` | 데이터베이스 사용자명 |
| `DB_PASSWORD` | `YourSecurePassword123!` | 데이터베이스 비밀번호 |

**참고:** Docker Compose에서 MySQL을 함께 실행하는 경우 `DB_HOST=mysql`로 설정

---

### 3. Redis 설정 (선택사항)

| Secret 이름 | 값 예시 | 설명 |
|------------|---------|------|
| `REDIS_HOST` | `redis` 또는 OCI Redis 엔드포인트 | Docker 서비스명 또는 OCI Redis 엔드포인트 |
| `REDIS_PORT` | `6379` | Redis 포트 |

**참고:** Docker Compose에서 Redis를 함께 실행하는 경우 `REDIS_HOST=redis`로 설정

---

## 📝 전체 Secrets 목록 (복사용)

다음 순서대로 GitHub Secrets에 추가:

```
OCI_HOST
OCI_USER
OCI_SSH_PRIVATE_KEY
DB_HOST
DB_NAME
DB_USER
DB_PASSWORD
REDIS_HOST (선택)
REDIS_PORT (선택)
```

**총 7개 (필수 6개 + 선택 1개)**

---

## 🔍 값 확인 방법

### OCI Public IP 확인
- OCI 콘솔 → Compute → Instances → 인스턴스 선택 → Public IP address

### OCI 사용자명 확인
- Oracle Linux: `opc`
- Ubuntu: `ubuntu`
- CentOS: `opc`

### SSH Private Key 파일 위치
- OCI 콘솔에서 인스턴스 생성 시 다운로드한 `.pem` 또는 `.key` 파일
- 또는 기존 SSH 키 쌍 사용

---

## ⚠️ 주의사항

1. **SSH Private Key**: 전체 내용을 복사해야 함 (줄바꿈 포함)
2. **비밀번호**: 강력한 비밀번호 사용 (특수문자, 숫자, 대소문자 포함)
3. **보안**: Secrets는 한 번 설정하면 값 확인 불가 (다시 설정해야 함)
4. **OCI 보안 그룹**: SSH (22), HTTP (80), 백엔드 포트 (8080) 허용 필요
5. **OCI 인스턴스 사전 설정**: Docker, Docker Compose, Git 설치 필요

---

## 🚀 설정 후 확인

모든 Secrets 설정 완료 후:

1. GitHub Actions 워크플로우 실행 확인
2. OCI 인스턴스에 SSH 접속하여 컨테이너 실행 확인
3. `docker-compose ps` 명령어로 컨테이너 상태 확인
4. 브라우저에서 `http://OCI_HOST` 접속 테스트

---

## 🔧 OCI 인스턴스 사전 설정 스크립트

OCI 인스턴스에 SSH 접속 후 다음 명령어 실행:

```bash
# Oracle Linux
sudo yum update -y
sudo yum install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -a -G docker opc

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Git 설치
sudo yum install git -y

# 프로젝트 디렉토리 생성
sudo mkdir -p /opt/coupon-system
sudo chown opc:opc /opt/coupon-system
```

**Ubuntu의 경우:**
```bash
sudo apt-get update
sudo apt-get install docker.io docker-compose git -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ubuntu
```
