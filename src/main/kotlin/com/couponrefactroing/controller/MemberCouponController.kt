package com.couponrefactroing.controller

import com.couponrefactroing.dto.IssueCouponRequest
import com.couponrefactroing.dto.MemberCouponResponse
import com.couponrefactroing.dto.UseCouponRequest
import com.couponrefactroing.service.CouponIssuer
import com.couponrefactroing.service.MemberCouponService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.Logger
import org.springframework.web.bind.annotation.*
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import kotlin.jvm.java
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/member-coupons")
class MemberCouponController(
    private val memberCouponService: MemberCouponService,
    private val couponIssuer: CouponIssuer,
) {
    private val log: Logger = LoggerFactory.getLogger(MemberCouponController::class.java)

    @PostMapping("/stream/issue", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun issueCouponSse(@RequestBody request: IssueCouponRequest): Flow<ServerSentEvent<String>> = flow {
        val startTime = System.currentTimeMillis()
        val memberId = request.memberId

        try {
            coroutineScope {
                log.info("[SSE Start] Member: $memberId - 요청 시작")

                val correlationId = UUID.randomUUID().toString()

                val resultDeferred = async {
                    try {
                        val waitUntilSseResponse = couponIssuer.waitUntilSseResponse(correlationId)
                        if(waitUntilSseResponse == null){
                            return@async "SSE 타임아웃 - 클라이언트에서 재시작 요청 필요"
                        }

                    } catch (e: TimeoutException) {
                        return@async "SSE 타임아웃 - 클라이언트에서 재시작 요청 필요"
                    }
                    return@async "성공"
                }

                couponIssuer.issueCoupon(request.couponId, request.memberId, correlationId)

                // STATUS 이벤트 전송
                val statusEvent = ServerSentEvent.builder<String>()
                    .event("STATUS")
                    .data("접수 완료 ($correlationId). 처리 중입니다...")
                    .build()
                emit(statusEvent)

                // 결과 대기
                try {
                    val resultJson: String = resultDeferred.await()

                    // RESULT 이벤트 전송
                    val resultEvent = ServerSentEvent.builder<String>()
                        .event("RESULT")
                        .data(resultJson)
                        .build()
                    emit(resultEvent)

                    // ✅ 핵심: 명시적으로 완료 신호 전송 (SSE 스펙 준수)
                    emit(ServerSentEvent.builder<String>()
                        .event("COMPLETE")
                        .data("END")
                        .build())

                } catch (e: Exception) {
                    emit(ServerSentEvent.builder<String>()
                        .event("ERROR")
                        .data("시간 초과 또는 오류 발생: ${e.message}")
                        .build())

                    // ✅ 에러 시에도 완료 신호 전송
                    emit(ServerSentEvent.builder<String>()
                        .event("COMPLETE")
                        .data("END")
                        .build())
                }
            }
        } catch (e: Exception) {
            emit(ServerSentEvent.builder<String>()
                .event("ERROR")
                .data("시스템 오류: ${e.message}")
                .build())
        }
        // ✅ Flow가 자동으로 완료됨 (명시적 종료 불필요하지만, COMPLETE 이벤트로 클라이언트에게 알림)
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
