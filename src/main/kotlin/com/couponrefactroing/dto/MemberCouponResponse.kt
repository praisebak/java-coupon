package com.couponrefactroing.dto

import com.couponrefactroing.domain.MemberCoupon

data class MemberCouponResponse(
    val id: Long?,
    val memberId: Long?,
    val couponId: Long?
) {
    companion object {
        fun from(memberCoupon: MemberCoupon): MemberCouponResponse {
            return MemberCouponResponse(
                id = memberCoupon.id,
                memberId = memberCoupon.memberId,
                couponId = memberCoupon.couponId
            )
        }
    }
}

