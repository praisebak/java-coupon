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
import kotlin.system.measureTimeMillis
import kotlin.time.measureTimedValue

@RestController
@RequestMapping("/member-coupons")
class MemberCouponController(
    private val memberCouponService: MemberCouponService,
    private val couponIssuer: CouponIssuer,
) {
    private val log: Logger = LoggerFactory.getLogger(MemberCouponController::class.java)

    @PostMapping("/stream/issue", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun issueCouponSse(@RequestBody request: IssueCouponRequest): Flow<ServerSentEvent<String>> = flow {
        val totalStartTime = System.currentTimeMillis()
        val memberId = request.memberId
        // 트랜잭션 추적 ID 생성
        val correlationId = UUID.randomUUID().toString()

        try {
            coroutineScope {
                // [1] 시작 로그
                log.info("[$correlationId] [SSE Start] Member: $memberId - 요청 시작")

                // [2] Redis 응답 대기 (Async)
                val resultDeferred = async {
                    log.info("[$correlationId] [SSE Async] Redis 응답 대기 시작")

                    val (response, duration) = measureTimedValue {
                        try {
                            couponIssuer.waitUntilSseResponse(correlationId)
                        } catch (e: TimeoutException) {
                            null
                        }
                    }

                    val elapsedMs = duration.inWholeMilliseconds
                    log.info("[$correlationId] [SSE Async] Redis 응답 수신 완료 (소요시간: ${elapsedMs}ms)")

                    if (response == null) {
                        return@async "SSE 타임아웃 - 클라이언트에서 재시작 요청 필요"
                    }
                    return@async response
                }

                // [3] 쿠폰 발급 요청 (Publisher)
                val issueTime = measureTimeMillis {
                    couponIssuer.issueCoupon(request.couponId, request.memberId, correlationId)
                }
                log.info("[$correlationId] [SSE Publish] 쿠폰 발급 요청 전송 완료 (소요시간: ${issueTime}ms)")

                // STATUS 이벤트 전송
                emit(ServerSentEvent.builder<String>()
                    .event("STATUS")
                    .data("접수 완료 ($correlationId). 처리 중입니다...")
                    .build())

                // [4] 결과 대기 (Await)
                try {
                    val (resultJson, awaitDuration) = measureTimedValue {
                        resultDeferred.await()
                    }

                    log.info("[$correlationId] [SSE Await] 최종 결과 수신 (대기시간: ${awaitDuration.inWholeMilliseconds}ms, 결과: $resultJson)")

                    emit(ServerSentEvent.builder<String>()
                        .event("RESULT")
                        .data(resultJson)
                        .build())

                    emit(ServerSentEvent.builder<String>().event("COMPLETE").data("END").build())

                } catch (e: Exception) {
                    // 에러 로그에도 ID 포함
                    log.error("[$correlationId] [SSE Error] 대기 중 에러 발생", e)

                    emit(ServerSentEvent.builder<String>()
                        .event("ERROR")
                        .data("시간 초과 또는 오류 발생: ${e.message}")
                        .build())

                    emit(ServerSentEvent.builder<String>().event("COMPLETE").data("END").build())
                }
            }
        } catch (e: Exception) {
            // 시스템 에러 로그에도 ID 포함
            log.error("[$correlationId] [SSE System Error]", e)

            emit(ServerSentEvent.builder<String>()
                .event("ERROR")
                .data("시스템 오류: ${e.message}")
                .build())
        } finally {
            val totalTime = System.currentTimeMillis() - totalStartTime
            log.info("[$correlationId] [SSE End] 전체 요청 종료 (총 소요시간: ${totalTime}ms)")
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
