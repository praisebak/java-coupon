package com.couponrefactroing.service

import com.couponrefactroing.dto.IssueCouponEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async // ✅ 올바른 import (보통 * 로 되어있으면 없어도 됨)
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class CouponIssuerConsumer(
    private val couponIssueFacade: CouponIssueFacade
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
                    couponIssueFacade(event)
                }
            }.awaitAll()
        }
    }

    //redis 실패하면 얘도 실패하게하기 - 일관성 투자
    private suspend fun couponIssueFacade(event: IssueCouponEvent) {
        couponIssueFacade.issueCoupon(
            couponId = event.couponId, memberId = event.memberId, eventId = event.eventId
        )
    }

}
