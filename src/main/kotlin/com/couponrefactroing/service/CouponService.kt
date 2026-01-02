package com.couponrefactroing.service

import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.repository.CouponRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class CouponService(
    private val couponRepository: CouponRepository
) {
    suspend fun findAllCoupons(): List<Coupon> {
        return withContext(Dispatchers.IO) {
            couponRepository.findAll()
        }
    }
}



