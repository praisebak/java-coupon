# 도메인 관계 다이어그램

## ERD (Entity Relationship Diagram)

```mermaid
erDiagram
    Coupon ||--o{ MemberCoupon : "1:N"
    Member ||--o{ MemberCoupon : "1:N"
    MemberCoupon ||--|| MemberCouponUseHistory : "1:1"

    Coupon {
        Long id PK
        String title
        Int discountAmount
        Int minimumOrderPrice
        Int totalQuantity
        Int issuedQuantity
        LocalDateTime validStartedAt
        LocalDateTime validEndedAt
        Long version
        LocalDateTime createdAt
    }

    MemberCoupon {
        Long id PK
        Long memberId FK
        Long couponId FK
        LocalDateTime usedAt
        LocalDateTime createdAt
        LocalDateTime modifiedAt
    }

    MemberCouponUseHistory {
        Long id PK
        Long memberCouponId FK "UNIQUE"
        Long memberId
        LocalDateTime usedAt
        LocalDateTime createdAt
        Long optimisticLockVersion
    }

    Member {
        Long id PK
        string "외부 시스템"
    }
```

## 관계 설명

- **Coupon 1:N MemberCoupon**: 하나의 쿠폰은 여러 멤버에게 발급 가능
- **Member 1:N MemberCoupon**: 한 멤버는 여러 쿠폰을 발급받을 수 있음
- **MemberCoupon 1:1 MemberCouponUseHistory**: 하나의 멤버 쿠폰은 최대 한 번만 사용 가능 (UNIQUE 제약)

## 제약조건

- `MemberCoupon`: (coupon_id, member_id) UNIQUE - 중복 발급 방지
- `MemberCouponUseHistory`: member_coupon_id UNIQUE - 중복 사용 방지
