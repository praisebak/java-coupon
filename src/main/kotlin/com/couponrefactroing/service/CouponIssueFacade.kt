package com.couponrefactroing.service

import com.couponrefactroing.cache.CouponStockCacheService
import com.couponrefactroing.repository.CouponRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap

@Service
class CouponIssueFacade(private val couponIssuer: CouponIssuer,
                        private val objectMapper: ObjectMapper,
                        private val couponStockManager: CouponStockCacheService,
                        private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
                        private val couponRepository: CouponRepository,
                        private val transactionTemplate: TransactionTemplate
    ) {

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val topic = ChannelTopic("coupon-completion-topic")

    @PostConstruct
    fun startGlobalRedisListener() {
        reactiveRedisTemplate.listenTo(ChannelTopic("coupon-completion-topic"))
            .map { it.message }
            .doOnNext { message ->
                try {
                    val rootNode = objectMapper.readTree(message)
                    val correlationId = rootNode.path("correlationId").asText()

                    pendingRequests[correlationId]?.complete(message)
                } catch (e: Exception) {
                    println("Redis Listener Error: ${e.message}")
                }
            }
            .subscribe()
    }


    suspend fun issueCoupon(couponId: Long, memberId: Long, eventId: String) {
        //redis 장애는 롤백할 수 없다.
        //redis 장애는 안고가는것을 가정한다.
        //TTL을 작게 가져가거나(장애시의 문제를 최소화) - 스프링배치에서 후처리하던가
            //TTL 작게 가져간 경우,한개의 인스턴스가 redis만 가져가거나
            //스프링배치에서 후처리하기 - 성능 문제 없을지 생각해봐야함
        try{
            couponStockManager.validateAlreadyAssignedCoupon(couponId, memberId)
            couponStockManager.checkAndMark(couponId, memberId)
            couponStockManager.decreaseStock(couponId)

            withContext(Dispatchers.IO) {
                couponIssuer.issueCoupon(couponId, memberId, eventId)
            }

            sendCouponSuccessToRedis(eventId, couponId)
        }catch (e : Exception){
            sendCouponFailureToRedis(eventId, couponId)
            throw RuntimeException("쿠폰 발행중 예외가 발생했습니다. ${e.message}")
        }
    }

    suspend fun sendCouponSuccessToRedis(eventId: String, savedCouponId: Long?) {
        val successJson = """
            {
                "correlationId": "$eventId",
                "status": "RESULT",
                "data": { "couponId": $savedCouponId }
            }
        """.trimIndent()

        reactiveRedisTemplate.convertAndSend(topic.topic, successJson).awaitSingle()
    }

    private suspend fun sendCouponFailureToRedis(eventId: String, couponId: Long) {
        val failJson = """
            {
                "correlationId": "$eventId",
                "status": "RESULT",
                "message": "Fail $couponId"
            }
        """.trimIndent()

        reactiveRedisTemplate.convertAndSend(topic.topic, failJson).awaitSingle()
    }

    @Scheduled(cron = "5 * * * * *")
    fun fulfillCouponScheduler(){
        CoroutineScope(Dispatchers.IO).launch {
            val now = LocalDateTime.now()
            val coupons = couponRepository.findByValidStartedAtLessThanEqualAndValidEndedAtGreaterThanEqual(now,now)
            coupons.forEach { coupon ->
                coupon.id?.let { id ->
                    val total = coupon.totalQuantity
                    val remaining = total - coupon.issuedQuantity
                    couponStockManager.setStock(couponId = id, quantity = remaining)
                }
            }
        }
    }
}
