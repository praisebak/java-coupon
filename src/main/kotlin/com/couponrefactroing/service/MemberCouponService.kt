package com.couponrefactroing.service

import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.repository.MemberCouponRepository
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class MemberCouponService(
    private val memberCouponRepository: MemberCouponRepository
) {
    suspend fun findUsableMemberCoupons(memberId: Long?): List<MemberCoupon> {
        // R2DBC는 non-blocking이므로 withContext 불필요
        // Flux를 Flow로 변환 후 List로 수집
        return memberCouponRepository.findByMemberId(memberId)
            .asFlow()
            .toList()
    }

    suspend fun useCoupon(memberId: Long, memberCouponId: Long) {
        // TODO: 실제 쿠폰 사용 로직 구현 필요
    }
}
