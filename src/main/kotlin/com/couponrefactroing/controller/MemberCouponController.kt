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

        coroutineScope {
            log.info("[SSE Start] Member: $memberId - 요청 시작")

            val correlationId = UUID.randomUUID().toString()

            val resultDeferred = async {
                try {
                    couponIssuer.waitUntilSseResponse(correlationId)
                } catch (e: TimeoutException) {
                    if(!couponIssuer.checkSseResponse(correlationId)){
                        throw e
                    }
                }
                return@async "성공"
            }

            couponIssuer.issueCoupon(request.couponId, request.memberId,correlationId)

            val step1Time = System.currentTimeMillis()
            log.info("[Step 1] Member: $memberId ($correlationId) - Kafka 발급 요청 완료 (소요: ${step1Time - startTime}ms)")

            // 2. STATUS 이벤트 전송
            val statusEvent = ServerSentEvent.builder<String>()
                .event("STATUS")
                .data("접수 완료 ($correlationId). 처리 중입니다...")
                .build()
            emit(statusEvent)

            val step2Time = System.currentTimeMillis()
            log.info("[Step 2] Member: $memberId ($correlationId) - STATUS 이벤트 전송 완료 (누적: ${step2Time - startTime}ms)")

            // 3. 결과 대기 (여기가 가장 오래 걸리는 구간 - Redis Polling/Sub)
            try {
                // 대기 시작 시간 기록
                val waitStart = System.currentTimeMillis()

                val resultJson : String = resultDeferred.await()

                val waitEnd = System.currentTimeMillis()
                // [중요] 대기 시간(Latency)만 따로 계산
                log.info("[Step 3] Member: $memberId ($correlationId) - 결과 수신 완료 (대기 시간: ${waitEnd - waitStart}ms / 전체 누적: ${waitEnd - startTime}ms)")

                // 4. RESULT 이벤트 전송
                val resultEvent = ServerSentEvent.builder<String>()
                    .event("RESULT")
                    .data(resultJson)
                    .build()
                emit(resultEvent)

                val step4Time = System.currentTimeMillis()
                log.info("[Step 4] Member: $memberId ($correlationId) - RESULT 전송 및 종료 (총 소요: ${step4Time - startTime}ms)")

            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis()
                log.error("[Error] Member: $memberId ($correlationId) - 실패 (총 소요: ${errorTime - startTime}ms) / 원인: ${e.message}")

                emit(ServerSentEvent.builder<String>()
                    .event("ERROR")
                    .data("시간 초과 또는 오류 발생: ${e.message}")
                    .build())
            }
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
