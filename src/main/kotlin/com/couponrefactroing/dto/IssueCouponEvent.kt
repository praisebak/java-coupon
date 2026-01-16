package com.couponrefactroing.dto

data class IssueCouponEvent(
    val memberId: Long,
    val couponId: Long,
    val eventId: String,
    val enqueuedAt: Long
) {

}
