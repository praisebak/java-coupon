import { useQuery } from '@tanstack/react-query'
import { getAllCoupons } from '../api/coupons'

export const useCoupons = () => {
  return useQuery({
    queryKey: ['coupons'],
    queryFn: getAllCoupons,
  })
}


