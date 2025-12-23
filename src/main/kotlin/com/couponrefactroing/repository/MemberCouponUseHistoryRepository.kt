package com.couponrefactroing.repository

import com.couponrefactroing.domain.MemberCouponUseHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberCouponUseHistoryRepository : JpaRepository<MemberCouponUseHistory, Long> {
    fun findByMemberCouponId(memberCouponId: Long): List<MemberCouponUseHistory>
    fun findByMemberId(memberId: Long): List<MemberCouponUseHistory>
}

