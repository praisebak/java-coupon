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
            val correlationId = UUID.randomUUID().toString()
            val logId = correlationId.substring(0, 8)

            // 로그를 모아둘 버퍼 (비동기 접근 고려하여 StringBuffer 사용)
            val traceLog = StringBuffer()

            // 내부 로깅용 헬퍼 함수
            fun addLog(msg: String) {
                traceLog.append("[$logId] $msg\n")
            }

            try {
                coroutineScope {
                    // [1] 시작 기록
                    addLog("[SSE Start] Member: $memberId - 요청 시작")

                    // [2] Redis 응답 대기 (Async)
                    val resultDeferred = async {
                        addLog("[SSE Async] Redis 응답 대기 시작")

                        val (response, duration) = measureTimedValue {
                            try {
                                couponIssuer.waitUntilSseResponse(correlationId)
                            } catch (e: TimeoutException) {
                                addLog("[SSE Async] 타임아웃으로 실패")
                                null
                            }
                        }

                        val elapsedMs = duration.inWholeMilliseconds
                        addLog("[SSE Async] (소요시간: ${elapsedMs}ms) Redis 응답 수신 완료")

                        if (response == null) {
                            return@async "SSE 타임아웃 - 클라이언트에서 재시작 요청 필요"
                        }
                        return@async response
                    }

                    // [3] 쿠폰 발급 요청 (Publisher)
                    val issueTime = measureTimeMillis {
                        couponIssuer.issueCoupon(request.couponId, request.memberId, correlationId)
                    }
                    addLog("[SSE Publish] (소요시간: ${issueTime}ms) 쿠폰 발급 요청 전송 완료")

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

                        addLog("[SSE Await] (대기시간: ${awaitDuration.inWholeMilliseconds}ms, 결과: $resultJson)")

                        emit(ServerSentEvent.builder<String>()
                            .event("RESULT")
                            .data(resultJson)
                            .build())

                        emit(ServerSentEvent.builder<String>().event("COMPLETE").data("END").build())

                    } catch (e: Exception) {
                        // 에러 발생 시에는 시간 관계없이 로그를 남겨야 하므로 error 레벨로 즉시 출력할 수도 있지만,
                        // 여기서는 흐름 파악을 위해 buffer 내용을 포함해서 출력
                        addLog("[SSE Error] 대기 중 에러 발생: ${e.message}")
                        log.error("[$logId] [SSE Error] 요청 처리 중 예외 발생\n$traceLog", e)

                        emit(ServerSentEvent.builder<String>()
                            .event("ERROR")
                            .data("오류 발생: ${e.message}")
                            .build())

                        emit(ServerSentEvent.builder<String>().event("COMPLETE").data("END").build())
                    }
                }
            } catch (e: Exception) {
                addLog("[SSE System Error] 시스템 오류: ${e.message}")
                log.error("[$logId] [SSE System Error]\n$traceLog", e)

                emit(ServerSentEvent.builder<String>()
                    .event("ERROR")
                    .data("시스템 오류: ${e.message}")
                    .build())
            } finally {
                val totalTime = System.currentTimeMillis() - totalStartTime
                addLog("[SSE End] 전체 요청 종료 (총 소요시간: ${totalTime}ms)")

                // [핵심 로직] 전체 소요 시간이 5초(5000ms)를 넘을 때만 로그 출력
                if (totalTime >= 5000) {
                    log.warn("========= SLOW REQUEST DETECTED ($totalTime ms) =========\n$traceLog")
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
