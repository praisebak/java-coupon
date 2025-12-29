package com.couponrefactroing.repository

import com.couponrefactroing.domain.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CouponRepository : JpaRepository<Coupon, Long>{
    fun findAllByTotalQuantityAfter(totalQuantity: Int) : List<Coupon>
}
