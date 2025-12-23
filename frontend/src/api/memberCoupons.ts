import { apiClient } from './client'
import { MemberCoupon, IssueCouponRequest, UseCouponRequest } from '../types/api'

export const getMemberCoupons = async (memberId: number): Promise<MemberCoupon[]> => {
  const response = await apiClient.get<MemberCoupon[]>('/member-coupons/by-member-id', {
    params: { memberId },
  })
  return response.data
}

export const issueCoupon = async (couponId: number, memberId: number): Promise<number> => {
  const response = await apiClient.post<number>('/member-coupons', {
    couponId,
    memberId,
  } as IssueCouponRequest)
  return response.data
}

export const useCoupon = async (memberCouponId: number, memberId: number): Promise<void> => {
  await apiClient.post(`/member-coupons/${memberCouponId}/use`, {
    memberCouponId,
    memberId,
  } as UseCouponRequest)
}

