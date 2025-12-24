package com.couponrefactroing.controller

import com.couponrefactroing.dto.CouponResponse
import com.couponrefactroing.service.CouponService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService
) {

    @GetMapping
    suspend fun getAllCoupons(): List<CouponResponse> {
        return couponService.findAllCoupons()
            .map { CouponResponse.from(it) }
    }
}
