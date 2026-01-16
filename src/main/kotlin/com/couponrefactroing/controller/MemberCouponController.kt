package com.couponrefactroing.controller

import com.couponrefactroing.dto.IssueCouponRequest
import com.couponrefactroing.dto.MemberCouponResponse
import com.couponrefactroing.dto.UseCouponRequest
import com.couponrefactroing.service.CouponIssuer
import com.couponrefactroing.service.MemberCouponService
import com.couponrefactroing.util.PerfTraceRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.Logger
import org.springframework.web.bind.annotation.*
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/member-coupons")
class MemberCouponController(
    private val memberCouponService: MemberCouponService,
    private val couponIssuer: CouponIssuer,
    private val objectMapper: ObjectMapper,
    private val perfTraceRegistry: PerfTraceRegistry
) {
    private val log: Logger = LoggerFactory.getLogger(MemberCouponController::class.java)

    @PostMapping("/stream/issue", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun issueCouponSse(@RequestBody request: IssueCouponRequest): Flow<ServerSentEvent<String>> = flow {
        val correlationId = UUID.randomUUID().toString()
        val start = System.nanoTime()
        var outcome = "SUCCESS"

        try {
            val resultJson = couponIssuer.issueWithWait(
                request.couponId,
                request.memberId,
                correlationId
            )
            emit(sse("RESULT", resultJson))

        } catch (e: Exception) {
            outcome = "ERROR"
            val (code, message) = when (e) {
                is TimeoutCancellationException -> "TIMEOUT" to "시간 초과"
                is IllegalStateException -> "SOLD_OUT" to (e.message ?: "재고 없음")
                else -> "SYSTEM_ERROR" to "오류 발생: ${e.message}"
            }
            val errorJson = objectMapper.writeValueAsString(mapOf("code" to code, "message" to message))
            emit(sse("ERROR", errorJson))
        } finally {
            val totalElapsedMs = (System.nanoTime() - start) / 1_000_000
            perfTraceRegistry.summarizeAndClear(correlationId, outcome, totalElapsedMs)
            emit(sse("COMPLETE", "END"))
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

    private fun sse(event: String, data: String) = ServerSentEvent.builder<String>()
        .event(event)
        .data(data)
        .build()
}
