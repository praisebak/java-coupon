package com.couponrefactroing.controller

import com.couponrefactroing.dto.IssueCouponRequest
import com.couponrefactroing.dto.MemberCouponResponse
import com.couponrefactroing.dto.UseCouponRequest
import com.couponrefactroing.service.CouponIssuer
import com.couponrefactroing.service.MemberCouponService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.web.bind.annotation.*
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent

@RestController
@RequestMapping("/member-coupons")
class MemberCouponController(
    private val memberCouponService: MemberCouponService,
    private val couponIssuer: CouponIssuer
) {

    @PostMapping("/stream/issue", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun issueCouponSse(@RequestBody request: IssueCouponRequest): Flow<ServerSentEvent<String>> = flow {

        // 1. [요청] 서비스에게 일 시키고 '접수 번호(ID)'를 받습니다.
        // (이때 서비스는 Kafka에 메시지만 던지고 바로 리턴합니다.)
        val correlationId = couponIssuer.issueCoupon(request.couponId, request.memberId)

        // 2. [1차 응답] 사용자에게 "일단 접수됐다"고 즉시 알려줍니다.
        emit(ServerSentEvent.builder<String>()
            .event("STATUS")
            .data("접수 완료 ($correlationId). 처리 중입니다...")
            .build())

        // 3. [대기] 결과가 나올 때까지 여기서 잠시 멈춥니다. (Suspend)
        // resultWaiter가 Redis를 감시하다가, 결과가 뜨면 낚아채서 가져옵니다.
        try {
            val resultJson = couponIssuer.waitUntilSseResponse(correlationId)

            // 4. [2차 응답] 결과를 받으면 사용자에게 최종 발송합니다.
            emit(ServerSentEvent.builder<String>()
                .event("RESULT")
                .data(resultJson)
                .build())

        } catch (e: Exception) {
            emit(ServerSentEvent.builder<String>()
                .event("ERROR")
                .data("시간 초과 또는 오류 발생")
                .build())
        }
    }

    @GetMapping("/by-member-id")
    suspend fun getMemberCoupons(@RequestParam("memberId") memberId: Long?): List<MemberCouponResponse> {
        requireNotNull(memberId) { "memberId는 필수입니다." }
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
