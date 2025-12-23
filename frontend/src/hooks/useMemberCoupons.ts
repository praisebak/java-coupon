import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getMemberCoupons, issueCoupon, useCoupon } from '../api/memberCoupons'

export const useMemberCoupons = (memberId: number) => {
  return useQuery({
    queryKey: ['memberCoupons', memberId],
    queryFn: () => getMemberCoupons(memberId),
    enabled: !!memberId,
  })
}

export const useIssueCoupon = () => {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: ({ couponId, memberId }: { couponId: number; memberId: number }) =>
      issueCoupon(couponId, memberId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['memberCoupons', variables.memberId] })
    },
  })
}

export const useUseCoupon = () => {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: ({ memberCouponId, memberId }: { memberCouponId: number; memberId: number }) =>
      useCoupon(memberCouponId, memberId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['memberCoupons', variables.memberId] })
    },
  })
}

