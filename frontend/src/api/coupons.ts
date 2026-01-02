import { apiClient } from './client'
import { Coupon } from '../types/api'

export const getAllCoupons = async (): Promise<Coupon[]> => {
  const response = await apiClient.get<Coupon[]>('/coupons')
  return response.data
}



