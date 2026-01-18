package com.couponrefactroing.service

import com.couponrefactroing.cache.CouponIssueDuplicateChecker
import com.couponrefactroing.cache.CouponStockCacheService
import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.dto.CouponAddRequest
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.repository.MemberCouponRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 쿠폰 발급 로직 - 성능 최적화 버전 (Map Dispatcher 적용)
 * * 변경사항:
 * 1. 응답 대기 로직 변경: Flux.filter(전수조사) -> ConcurrentHashMap(1:1 매칭)
 * 2. 복잡도 개선: O(N^2) -> O(1)로 변경하여 CPU 과부하 및 타임아웃 해결
 */
@Component
class CouponIssuer(
    private val couponRepository: CouponRepository,
    private val memberFrontmen: MemberFrontMen,
    private val couponStockManager: CouponStockCacheService,
    private val duplicateChecker: CouponIssueDuplicateChecker,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val memberCouponRepository: MemberCouponRepository,
    private val couponStockCacheService: CouponStockCacheService,
    private val transactionTemplate: TransactionTemplate,
) : CouponIssueService {

    private val log = LoggerFactory.getLogger(this::class.java)
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

    override suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long {
        return withContext(Dispatchers.IO) {
            memberFrontmen.validateExistMember(memberId)
            val now = LocalDateTime.now()

            val coupon = Coupon(
                title = couponInformation.couponSummery,
                discountAmount = couponInformation.subtractAmount.toInt(),
                minimumOrderPrice = 0,
                totalQuantity = Int.MAX_VALUE,
                validStartedAt = now,
                validEndedAt = now.plusYears(1)
            )

            val savedCoupon = couponRepository.save(coupon)
            val couponId = savedCoupon.id ?: throw IllegalStateException("쿠폰 생성 실패")

            couponStockManager.setStock(couponId, savedCoupon.totalQuantity)
            couponId
        }
    }

    override suspend fun issueCoupon(couponId: Long,
                            memberId: Long,
                            eventId: String) {
        val processingStart = System.nanoTime()

        try {
            try {
                memberFrontmen.validateExistMember(memberId)
                validateAlreadyAssignedCoupon(couponId, memberId)

                coroutineScope {
                    val redisDeferred = async {
                        couponStockManager.decreaseStock(couponId)
                    }

                    val dbDeferred = async {
                        transactionTemplate.execute {
                            decreaseStockDB(couponId)
                            saveMemberCoupon(memberId, couponId)
                        }
                    }

                    redisDeferred.await()
                    dbDeferred.await()
                }

                sendCouponSuccessToRedis(eventId, couponId)
            } catch (e: Exception) {
                duplicateChecker.clearMark(couponId, memberId)
                sendCouponFailureToRedis(eventId, couponId)
            }
        } finally {
            val processingMs = (System.nanoTime() - processingStart) / 1_000_000
            if (processingMs >= 1_000L) {
                log.info(
                    "PERF_CONSUMER_PROCESS eventId={} processMs={}",
                    eventId,
                    processingMs
                )
            }
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

    private fun saveMemberCoupon(memberId: Long, couponId: Long) {
        val now = LocalDateTime.now()
        val memberCoupon = MemberCoupon(
            memberId = memberId,
            couponId = couponId,
            usedAt = null,
            createdAt = now,
            modifiedAt = now
        )
        memberCouponRepository.save(memberCoupon)
    }

    private fun decreaseStockDB(couponId: Long) {
        val updatedRows = couponRepository.increaseIssuedQuantity(couponId)
        if (updatedRows != 1) {
            throw IllegalStateException("이미 모두 발급된 쿠폰입니다.")
        }
    }

    private suspend fun validateAlreadyAssignedCoupon(couponId: Long, memberId: Long) {
        val canIssue = duplicateChecker.checkAndMark(couponId, memberId)
        if (!canIssue) {
            throw IllegalArgumentException("이미 발급받은 쿠폰입니다.")
        }
    }

    @Scheduled(cron = "5 * * * * *")
    fun fulfillCouponScheduler(){
        CoroutineScope(Dispatchers.IO).launch {
            val now = LocalDateTime.now();
            val coupons = couponRepository.findByValidStartedAtLessThanEqualAndValidEndedAtGreaterThanEqual(now,now)
            coupons.forEach { coupon ->
                coupon.id?.let { id ->
                    val total = coupon.totalQuantity
                    val remaining = total - coupon.issuedQuantity
                    couponStockCacheService.setStock(couponId = id, quantity = remaining)
                }
            }
        }
    }
}
