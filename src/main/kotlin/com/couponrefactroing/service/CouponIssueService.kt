package com.couponrefactroing.service

import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.dto.CouponAddRequest
import com.couponrefactroing.dto.CouponIssueState
import kotlinx.coroutines.flow.Flow

/**
 * 쿠폰 발급 서비스 인터페이스
 * 구현체: CouponIssuer (Redis 캐싱), CouponIssuerNonCached (순수 DB)
 */
interface CouponIssueService {
    /**
     * 쿠폰 생성
     */
    suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long

    /**
     * 쿠폰 발급
     */
    suspend fun issueCoupon(couponId: Long, memberId: Long): String
}

