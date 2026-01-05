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
        println("🚀 [Controller] SSE 요청 수신: couponId=${request.couponId}, memberId=${request.memberId}")

        // 1. 쿠폰 발급 시작
        val correlationId = couponIssuer.issueCoupon(request.couponId, request.memberId)
        println("📋 [Controller] correlationId 생성: $correlationId")

        // 2. STATUS 이벤트 전송
        val statusEvent = ServerSentEvent.builder<String>()
            .event("STATUS")
            .data("접수 완료 ($correlationId). 처리 중입니다...")
            .build()
        println("📤 [Controller] STATUS emit: ${statusEvent.data()}")
        emit(statusEvent)

        // 3. 결과 대기
        println("⏳ [Controller] 결과 대기 시작...")
        try {
            val resultJson = couponIssuer.waitUntilSseResponse(correlationId)
            println("✅ [Controller] 결과 수신: $resultJson")

            // 4. RESULT 이벤트 전송
            val resultEvent = ServerSentEvent.builder<String>()
                .event("RESULT")
                .data(resultJson)
                .build()
            println("📤 [Controller] RESULT emit: ${resultEvent.data()}")
            emit(resultEvent)

        } catch (e: Exception) {
            println("❌ [Controller] 에러 발생: ${e.message}")
            emit(ServerSentEvent.builder<String>()
                .event("ERROR")
                .data("시간 초과 또는 오류 발생: ${e.message}")
                .build())
        }
        
        println("✅ [Controller] SSE 스트림 종료")
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
