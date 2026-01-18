package com.couponrefactroing.service

import com.couponrefactroing.dto.CouponAddRequest

interface CouponIssueService {
    suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long

    suspend fun issueCoupon(couponId: Long, memberId: Long, eventId : String)
}

