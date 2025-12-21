package com.couponrefactroing.service

import com.couponrefactroing.domain.MemberCoupon
import org.springframework.stereotype.Service

@Service
class CouponIssuer {

    suspend fun issueCoupon(couponId: Long, memberId: Long): MemberCoupon {
        // TODO: 실제 발급 로직 구현 필요 (R2DBC 사용)
        return MemberCoupon(
            memberId = memberId,
            couponId = couponId
        )
    }
}
