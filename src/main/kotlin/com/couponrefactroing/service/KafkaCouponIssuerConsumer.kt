package com.couponrefactroing.service

import com.couponrefactroing.dto.IssueCouponEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async // ✅ 올바른 import (보통 * 로 되어있으면 없어도 됨)
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
// ❌ import org.springframework.web.servlet.function.ServerResponse.async (삭제!)

@Service
class KafkaCouponIssuerConsumer(
    private val couponIssuer: CouponIssuer // ✅ [수정] private val 추가해야 메서드에서 사용 가능
) {

    @KafkaListener(
        topicPattern = "issue-coupon",
        containerFactory = "batchKafkaListenerContainerFactory",
        concurrency = "5"
    )
    suspend fun processCouponIssue(events: List<IssueCouponEvent>) {
        if (events.isEmpty()) return

        coroutineScope {
            events.map { event ->
                async(Dispatchers.IO) {
                    couponIssuer.issueCoupon(
                        couponId =event.couponId, memberId = event.memberId,eventId = event.eventId
                    )
                }
            }.awaitAll()
        }
    }
}
