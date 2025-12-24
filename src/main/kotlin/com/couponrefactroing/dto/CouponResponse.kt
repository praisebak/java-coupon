package com.couponrefactroing.dto

import com.couponrefactroing.domain.Coupon
import java.time.LocalDateTime

data class CouponResponse(
    val id: Long?,
    val title: String,
    val discountAmount: Int,
    val minimumOrderPrice: Int,
    val totalQuantity: Int?,
    val issuedQuantity: Int,
    val validStartedAt: LocalDateTime,
    val validEndedAt: LocalDateTime
) {
    companion object {
        fun from(coupon: Coupon): CouponResponse {
            return CouponResponse(
                id = coupon.id,
                title = coupon.title,
                discountAmount = coupon.discountAmount,
                minimumOrderPrice = coupon.minimumOrderPrice,
                totalQuantity = coupon.totalQuantity,
                issuedQuantity = coupon.issuedQuantity,
                validStartedAt = coupon.validStartedAt,
                validEndedAt = coupon.validEndedAt
            )
        }
    }
}

