package com.couponrefactroing.repository

import com.couponrefactroing.domain.MemberCoupon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.repository.query.Param

interface MemberCouponRepository : R2dbcRepository<MemberCoupon, Long> {
    @Query("SELECT * FROM member_coupon WHERE member_id = :memberId")
    fun findByMemberId(@Param("memberId") memberId: Long?): reactor.core.publisher.Flux<MemberCoupon>
}



