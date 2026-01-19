package com.couponrefactroing.repository

import com.couponrefactroing.domain.MemberCoupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface MemberCouponRepository : JpaRepository<MemberCoupon, Long> {
    fun findByMemberId(memberId: Long): List<MemberCoupon>
    fun findByCouponIdAndMemberId(couponId: Long, memberId: Long): Optional<MemberCoupon>
}



