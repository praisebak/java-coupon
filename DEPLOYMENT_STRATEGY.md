# 학습 프로젝트 배포 전략 가이드

## 추천 배포 전략 (학습 목적)

### 옵션 1: Vercel + Railway/Render (가장 추천) ⭐

**장점:**
- 완전 무료 티어 제공
- 설정이 매우 간단
- 프론트엔드와 백엔드를 분리해서 배포 가능
- 자동 HTTPS, 도메인 제공

**구성:**
- **프론트엔드**: Vercel (무료, Next.js 최적화이지만 React도 가능)
- **백엔드**: Railway 또는 Render (무료 티어 있음)

**추천 이유:**
- 학습하기 가장 쉬움
- 실제 회사에서도 많이 사용
- GitHub 연동으로 자동 배포
- 비용 부담 없음

---

### 옵션 2: Docker Compose + 클라우드 VM (실무 학습용) ⭐⭐

**장점:**
- 실제 프로덕션 환경과 유사
- Docker Compose로 전체 스택 관리 학습
- 인프라 구성 경험

**구성:**
- **전체 스택**: Docker Compose
- **서버**: AWS EC2 (1년 무료), GCP Compute Engine, 또는 Oracle Cloud (영구 무료)

**추천 이유:**
- 실제 회사 환경과 가장 유사
- Docker, Linux, 네트워크 등 실무 기술 학습
- 포트폴리오로 어필 가능

---

### 옵션 3: GitHub Pages + Netlify Functions / Vercel Functions (최소 비용)

**장점:**
- 완전 무료
- 정적 호스팅 + 서버리스 백엔드

**단점:**
- Spring Boot 백엔드를 서버리스로 변환 필요 (학습 복잡도 증가)

**추천 이유:**
- 비용 걱정 없음
- 서버리스 아키텍처 학습

---

### 옵션 4: 로컬 개발만 (초기 학습)

**장점:**
- 설정 최소화
- 빠른 개발 사이클

**추천 상황:**
- 초기 학습 단계
- 기능 개발에 집중하고 싶을 때

---

## 단계별 추천 학습 경로

### Phase 1: 로컬 개발 (현재)
```
✅ 로컬에서 개발 및 테스트
✅ Docker Compose로 로컬 스택 구성
```

### Phase 2: 간단한 배포 (Vercel + Railway)
```
1. 프론트엔드 → Vercel 배포
2. 백엔드 → Railway 배포
3. 환경 변수 연결
4. GitHub Actions로 자동 배포 설정
```

### Phase 3: 인프라 학습 (Docker + 클라우드 VM)
```
1. Docker Compose 전체 스택 구성
2. 클라우드 VM에 배포
3. Nginx 리버스 프록시 설정
4. SSL 인증서 설정 (Let's Encrypt)
5. 모니터링 설정 (선택)
```

---

## 실제 구현 예시 (옵션 2: Docker + VM 기준)

### Step 1: GitHub Actions 간소화

```yaml
# .github/workflows/deploy.yml
name: Deploy to VM

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Deploy via SSH
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.VM_HOST }}
          username: ${{ secrets.VM_USER }}
          key: ${{ secrets.VM_SSH_KEY }}
          script: |
            cd /opt/coupon-system
            git pull origin main
            docker-compose down
            docker-compose up -d --build
```

### Step 2: VM 초기 설정 스크립트

```bash
#!/bin/bash
# setup-vm.sh

# Docker 설치
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 프로젝트 클론
git clone https://github.com/your-username/coupon-refactroing.git /opt/coupon-system
cd /opt/coupon-system

# 환경 변수 설정
cp .env.example .env
# .env 파일 편집 필요

# 실행
docker-compose -f docker-compose.prod.yml up -d
```

---

## 각 옵션별 상세 가이드

### 옵션 1: Vercel + Railway (가장 쉬움)

**프론트엔드 (Vercel):**
1. Vercel 계정 생성
2. GitHub 저장소 연결
3. Root Directory: `frontend` 설정
4. Build Command: `npm run build`
5. Output Directory: `dist`
6. Environment Variables: `VITE_API_BASE_URL` 설정

**백엔드 (Railway):**
1. Railway 계정 생성
2. New Project → Deploy from GitHub
3. 저장소 선택
4. 환경 변수 설정:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
   - `SPRING_DATA_REDIS_HOST`
5. 포트: 8080 자동 할당

**연결:**
- Vercel에서 `VITE_API_BASE_URL`을 Railway 백엔드 URL로 설정

---

### 옵션 2: Docker + VM (실무 학습)

**VM 선택:**
- **Oracle Cloud**: 영구 무료 (2개 VM)
- **AWS EC2**: 1년 무료 티어 (t2.micro)
- **GCP**: $300 크레딧

**구성:**
```
[사용자] 
  ↓ HTTPS
[Nginx (포트 443)]
  ↓ 프록시
[Frontend (포트 80)]
[Backend (포트 8080)]
  ↓
[MySQL + Redis]
```

**Nginx 설정 예시:**
```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # Frontend
    location / {
        proxy_pass http://localhost:80;
    }

    # Backend API
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

---

## 비용 비교

| 옵션 | 월 비용 | 특징 |
|------|---------|------|
| Vercel + Railway | 무료 (제한 있음) | 가장 쉬움 |
| Docker + Oracle Cloud | 무료 | 영구 무료 |
| Docker + AWS EC2 | 1년 무료 후 ~$10 | 실무와 유사 |
| Docker + GCP | $300 크레딧 | 학습용 충분 |

---

## 추천: 단계적 접근

### Week 1-2: 로컬 완성
- 기능 개발 완료
- Docker Compose 로컬 테스트

### Week 3: Vercel + Railway 배포
- 빠르게 배포 성공 경험
- 실제 동작 확인

### Week 4+: Docker + VM 배포
- 인프라 구성 학습
- 포트폴리오로 활용

---

## 학습 목적으로 추천하는 최종 전략

**1단계: 빠른 성공 (Vercel + Railway)**
- 최소 설정으로 배포 성공
- 동작 확인 및 공유

**2단계: 실무 학습 (Docker + Oracle Cloud)**
- 실제 서버 환경 구성
- 인프라 지식 습득
- 포트폴리오 완성

이렇게 하면 학습 효과를 최대화하면서도 비용 부담 없이 진행할 수 있습니다.

