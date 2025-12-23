# 프론트엔드 아키텍처 설계

## 기술 스택

### 핵심 프레임워크
- **React 18+** (Vite 기반)
- **TypeScript**
- **TanStack Query (React Query)** - 서버 상태 관리
- **Zustand** - 클라이언트 상태 관리 (선택)

### UI/스타일링
- **Tailwind CSS** - 유틸리티 CSS
- **shadcn/ui** - 컴포넌트 라이브러리
- **React Hook Form** - 폼 관리
- **Zod** - 스키마 검증

### API & 통신
- **Axios** - HTTP 클라이언트
- **React Query** - API 상태 관리 및 캐싱

### 개발 도구
- **ESLint** + **Prettier**
- **Vitest** - 테스팅
- **MSW** - API 모킹 (개발 환경)

## 프로젝트 구조

```
frontend/
├── src/
│   ├── api/
│   │   ├── client.ts              # Axios 인스턴스
│   │   ├── coupons.ts             # 쿠폰 API
│   │   └── memberCoupons.ts       # 멤버 쿠폰 API
│   ├── components/
│   │   ├── ui/                    # shadcn/ui 컴포넌트
│   │   ├── coupon/
│   │   │   ├── CouponCard.tsx
│   │   │   ├── CouponList.tsx
│   │   │   └── CouponIssueButton.tsx
│   │   └── layout/
│   │       ├── Header.tsx
│   │       └── Sidebar.tsx
│   ├── pages/
│   │   ├── CouponListPage.tsx     # 쿠폰 목록 (발급 가능)
│   │   ├── MyCouponsPage.tsx      # 내 쿠폰 보관함
│   │   └── PaymentPage.tsx        # 결제 페이지 (쿠폰 사용)
│   ├── hooks/
│   │   ├── useCoupons.ts          # 쿠폰 관련 커스텀 훅
│   │   └── useMemberCoupons.ts    # 멤버 쿠폰 관련 커스텀 훅
│   ├── stores/
│   │   └── authStore.ts           # 인증 상태 (Zustand)
│   ├── types/
│   │   └── api.types.ts           # API 타입 정의
│   ├── utils/
│   │   └── formatters.ts          # 날짜, 금액 포맷팅
│   └── App.tsx
├── package.json
└── vite.config.ts
```

## 화면 구성 상세

### 1. 쿠폰 목록 페이지 (`/coupons`)
**목적**: 발급 가능한 쿠폰 목록 조회 및 발급

**레이아웃**:
```
┌─────────────────────────────────────┐
│ Header                              │
├─────────────────────────────────────┤
│ 쿠폰 목록                              │
│                                     │
│ ┌──────────┐  ┌──────────┐        │
│ │ 쿠폰 카드1 │  │ 쿠폰 카드2 │        │
│ │          │  │          │        │
│ │ [발급받기] │  │ [발급받기] │        │
│ └──────────┘  └──────────┘        │
│                                     │
│ [더 보기]                            │
└─────────────────────────────────────┘
```

**쿠폰 카드 구성**:
- 쿠폰명
- 할인 금액 (예: "5,000원 할인")
- 최소 주문 금액
- 유효 기간
- 발급 버튼 (발급 완료 시 "발급 완료" 표시)

### 2. 내 쿠폰 보관함 (`/my-coupons`)
**목적**: 내가 보유한 쿠폰 목록 조회

**레이아웃**:
```
┌─────────────────────────────────────┐
│ Header                              │
├─────────────────────────────────────┤
│ 내 쿠폰                              │
│ [전체] [사용 가능] [사용 완료]           │
├─────────────────────────────────────┤
│ ┌──────────┐  ┌──────────┐        │
│ │ 쿠폰 카드1 │  │ 쿠폰 카드2 │        │
│ │ (사용가능) │  │ (사용완료) │        │
│ │          │  │          │        │
│ │ 유효기간   │  │ 사용일시   │        │
│ └──────────┘  └──────────┘        │
└─────────────────────────────────────┘
```

**필터링**:
- 전체
- 사용 가능 (usedAt === null)
- 사용 완료 (usedAt !== null)

### 3. 결제 페이지 (`/payment`)
**목적**: 결제 시 쿠폰 선택 및 사용

**레이아웃**:
```
┌─────────────────────────────────────┐
│ 상품 정보                            │
│ 총 금액: 50,000원                     │
├─────────────────────────────────────┤
│ 쿠폰 선택                            │
│ ┌─────────────────────────────────┐ │
│ │ 쿠폰 선택 드롭다운                  │ │
│ │ [5,000원 할인 쿠폰] ▼              │ │
│ └─────────────────────────────────┘ │
│                                     │
│ 적용 금액: 45,000원                   │
│                                     │
│ [결제하기]                            │
└─────────────────────────────────────┘
```

## API 통신 설계

### API 클라이언트 설정

```typescript
// src/api/client.ts
import axios from 'axios';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 (인증 토큰 추가)
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 응답 인터셉터 (에러 처리)
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // 에러 처리 로직
    return Promise.reject(error);
  }
);
```

### React Query 훅

```typescript
// src/hooks/useMemberCoupons.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getMemberCoupons, useCoupon, issueCoupon } from '@/api/memberCoupons';

export const useMemberCoupons = (memberId: number) => {
  return useQuery({
    queryKey: ['memberCoupons', memberId],
    queryFn: () => getMemberCoupons(memberId),
    enabled: !!memberId,
  });
};

export const useIssueCoupon = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: ({ couponId, memberId }: { couponId: number; memberId: number }) =>
      issueCoupon(couponId, memberId),
    onSuccess: (_, variables) => {
      // 쿼리 무효화하여 목록 갱신
      queryClient.invalidateQueries({ queryKey: ['memberCoupons', variables.memberId] });
    },
  });
};

export const useUseCoupon = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: ({ memberCouponId, memberId }: { memberCouponId: number; memberId: number }) =>
      useCoupon(memberCouponId, memberId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['memberCoupons', variables.memberId] });
    },
  });
};
```

## 주요 컴포넌트 예시

### CouponCard 컴포넌트

```typescript
// src/components/coupon/CouponCard.tsx
interface CouponCardProps {
  coupon: Coupon;
  memberId: number;
  isIssued?: boolean;
  onIssue?: () => void;
}

export const CouponCard: React.FC<CouponCardProps> = ({
  coupon,
  memberId,
  isIssued,
  onIssue,
}) => {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{coupon.title}</CardTitle>
        <CardDescription>
          {coupon.discountAmount.toLocaleString()}원 할인
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p>최소 주문금액: {coupon.minimumOrderPrice.toLocaleString()}원</p>
        <p>유효기간: {formatDate(coupon.validStartedAt)} ~ {formatDate(coupon.validEndedAt)}</p>
      </CardContent>
      <CardFooter>
        {!isIssued ? (
          <Button onClick={onIssue}>발급받기</Button>
        ) : (
          <Button disabled>발급 완료</Button>
        )}
      </CardFooter>
    </Card>
  );
};
```

## 상태 관리 전략

### 서버 상태
- **TanStack Query**로 관리
- 캐싱, 자동 재시도, 백그라운드 업데이트

### 클라이언트 상태
- **Zustand** 또는 **Context API**
- 인증 정보, UI 상태 (모달 열림/닫힘 등)

## 성능 최적화

1. **코드 스플리팅**: React.lazy()로 페이지별 동적 import
2. **이미지 최적화**: WebP 포맷, lazy loading
3. **가상 스크롤**: 긴 목록의 경우 react-window 사용
4. **디바운싱**: 검색/필터 입력 시 디바운싱 적용

## 에러 처리

- React Query의 `onError`로 전역 에러 처리
- 에러 바운더리 (Error Boundary)로 예외 상황 처리
- 사용자 친화적인 에러 메시지 표시

## 접근성 (A11y)

- ARIA 라벨 추가
- 키보드 네비게이션 지원
- 색상 대비율 준수
- 스크린 리더 지원

