package com.couponrefactroing.service

import com.couponrefactroing.dto.IssueCouponEvent
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

@Component
class CouponIssueManager(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val kafkaTemplate: KafkaTemplate<String, IssueCouponEvent>,
) {
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()

    @PostConstruct
    fun startGlobalRedisListener() {
        reactiveRedisTemplate.listenTo(ChannelTopic("coupon-completion-topic"))
            .map { it.message }
            .doOnNext { message ->
                val correlationId = extractCorrelationId(message) // ID 추출 로직
                pendingRequests[correlationId]?.complete(message)
            }
            .subscribe()
    }

    suspend fun wait(correlationId: String, timeoutMs: Long = 60000): String {
        val deferred = CompletableDeferred<String>()
        pendingRequests[correlationId] = deferred

        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (exception : TimeoutException) {
            throw TimeoutException("Redis 응답 시간 초과")
        }finally {
            pendingRequests.remove(correlationId)
        }
    }

    suspend fun issueCoupon(couponId: Long, memberId : Long, correlationId: String) = coroutineScope {
        val responseDeferred = async {
            wait(correlationId)
        }

        publishCouponEvent(couponId,memberId,correlationId)

        return@coroutineScope responseDeferred.await()
    }

    fun publishCouponEvent(couponId: Long, memberId: Long, correlationId: String): String {
        val event = IssueCouponEvent(
            memberId = memberId,
            couponId = couponId,
            eventId = correlationId,
            enqueuedAt = System.currentTimeMillis()
        )

        kafkaTemplate.send("issue-coupon", event)
        return correlationId
    }

    private fun extractCorrelationId(json: String): String {
        val key = "\"correlationId\": \""
        val start = json.indexOf(key)
        if (start == -1) return ""

        val valueStart = start + key.length
        val valueEnd = json.indexOf("\"", valueStart)

        return if (valueEnd != -1) json.substring(valueStart, valueEnd) else ""
    }
}
