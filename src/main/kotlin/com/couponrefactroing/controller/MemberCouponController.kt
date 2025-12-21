package com.couponrefactroing.controller

import com.couponrefactroing.dto.IssueCouponRequest
import com.couponrefactroing.dto.MemberCouponResponse
import com.couponrefactroing.dto.UseCouponRequest
import com.couponrefactroing.service.CouponIssuer
import com.couponrefactroing.service.MemberCouponService
import org.springframework.web.bind.annotation.*
import kotlinx.coroutines.reactor.mono

@RestController
@RequestMapping("/member-coupons")
class MemberCouponController(
    private val couponIssuer: CouponIssuer,
    private val memberCouponService: MemberCouponService
) {

    @PostMapping
    suspend fun issueCoupon(@RequestBody request: IssueCouponRequest): Long {
        return couponIssuer.issueCoupon(request.couponId, request.memberId).id 
            ?: throw IllegalStateException("쿠폰 발급 실패")
    }

    @GetMapping("/by-member-id")
    suspend fun getMemberCoupons(@RequestParam("memberId") memberId: Long?): List<MemberCouponResponse> {
        return memberCouponService.findUsableMemberCoupons(memberId)
            .map { MemberCouponResponse.from(it) }
    }

    @PostMapping("/{memberCouponId:^\\d+$}/use")
    suspend fun useCoupon(
        @PathVariable memberCouponId: Long, 
        @RequestBody useCouponRequest: UseCouponRequest
    ) {
        require(memberCouponId == useCouponRequest.memberCouponId) { 
            "잘못된 쿠폰 번호입니다." 
        }
        
        memberCouponService.useCoupon(
            useCouponRequest.memberId, 
            useCouponRequest.memberCouponId
        )
    }
}
