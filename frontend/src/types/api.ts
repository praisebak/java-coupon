export interface Coupon {
  id: number
  title: string
  discountAmount: number
  minimumOrderPrice: number
  totalQuantity: number | null
  issuedQuantity: number
  validStartedAt: string
  validEndedAt: string
}

export interface MemberCoupon {
  id: number
  memberId: number
  couponId: number
  usedAt: string | null
  createdAt: string
  modifiedAt: string
}

export interface IssueCouponRequest {
  couponId: number
  memberId: number
}

export interface UseCouponRequest {
  memberId: number
  memberCouponId: number
}

