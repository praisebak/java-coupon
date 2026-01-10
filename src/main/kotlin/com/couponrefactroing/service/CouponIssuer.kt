package com.couponrefactroing.service

import com.couponrefactroing.cache.CouponIssueDuplicateChecker
import com.couponrefactroing.cache.CouponStockCacheService
import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.dto.CouponAddRequest
import com.couponrefactroing.dto.IssueCouponEvent
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.repository.MemberCouponRepository
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 쿠폰 발급 로직 - Redis 캐시 버전
 * Redis 캐시를 활용한 대용량 트래픽 처리 최적화
 *
 * 동시성 제어 전략:
 * 1. Redis에서 원자적 재고 차감
 * 2. Redis에서 중복 발급 체크
 * 3. DB에 발급 내역 저장
 */
@Component
class CouponIssuer(
    private val couponRepository: CouponRepository,
    private val memberCouponRepository: MemberCouponRepository,
    private val memberFrontmen: MemberFrontMen,
    private val stockCache: CouponStockCacheService,
    private val duplicateChecker: CouponIssueDuplicateChecker,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val stringRedisTemplate: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, IssueCouponEvent>,
    private val transactionTemplate: TransactionTemplate
) : CouponIssueService {

    @PostConstruct
    fun afterInit(){
        println("✓ CouponIssuer enabled (Redis 캐시 모드)")
    }

    @Transactional
    override suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long {
        return withContext(Dispatchers.IO) {
            memberFrontmen.validateExistMember(memberId)

            val now = LocalDateTime.now()
            val defaultValidEndAt = now.plusYears(1)

            val coupon = Coupon(
                title = couponInformation.couponSummery,
                discountAmount = couponInformation.subtractAmount.toInt(),
                minimumOrderPrice = 0,
                totalQuantity = Int.MAX_VALUE,
                validStartedAt = now,
                validEndedAt = defaultValidEndAt
            )

            val savedCoupon = couponRepository.save(coupon)
            val couponId = savedCoupon.id ?: throw IllegalStateException("쿠폰 생성 실패")

            // Redis에 재고 초기화
            stockCache.initializeStock(couponId, savedCoupon.totalQuantity)

            couponId
        }
    }

    //issue coupon 이벤트 발급 -> 다른 인스턴스들이 받아서 처리함 -> 완료 처리
    override suspend fun issueCoupon(couponId: Long, memberId: Long, correlationId : String): String {
        val event = IssueCouponEvent(memberId,couponId,correlationId)

        kafkaTemplate.send("issue-coupon",event)

        return correlationId
    }

    @KafkaListener(
        topicPattern = "issue-coupon",
        concurrency = "10"
    )
    suspend fun issueCoupon(issueCouponEvent : IssueCouponEvent){
        withContext(Dispatchers.IO) {
            val start = Instant.now()

            val memberId = issueCouponEvent.memberId
            val couponId = issueCouponEvent.couponId
            val eventId = issueCouponEvent.eventId

            try {
//                memberFrontmen.validateExistMember(memberId)
//                validateAlreadyAssignedCoupon(couponId, memberId)
                stockCache.decreaseStock(couponId)
                transactionTemplate.execute { status ->
                    decreaseStock(couponId)
                    saveMemberCoupon(memberId, couponId)
                }

                sendCouponSuccessToRedis(eventId, couponId)

                val end = Instant.now()
                val duration = Duration.between(start, end)
                if(duration.seconds >= 1){
                    println("소요된 시간 = $duration")
                }

            } catch (e: RuntimeException) {
                duplicateChecker.clearMark(couponId, memberId)
                sendCouponFailureToRedis(eventId,couponId)
                when (e) {
                    is ObjectOptimisticLockingFailureException, is IllegalStateException -> {
//                        throw IllegalArgumentException("유효하지 않은 쿠폰 입니다." + e.message);
                    }
                    //TODO 다시 기록해야함
//                    else -> throw e
                }
            }
        }
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

    private fun decreaseStock(couponId: Long) {
        val updatedRows = couponRepository.increaseIssuedQuantity(couponId)

        if (updatedRows != 1) {
            throw IllegalStateException("이미 모두 발급된 쿠폰입니다.");
        }
    }

    private suspend fun validateAlreadyAssignedCoupon(couponId: Long, memberId: Long) {
        val canIssue = duplicateChecker.checkAndMark(couponId, memberId)
        if (!canIssue) {
            throw IllegalArgumentException("이미 발급받은 쿠폰입니다.")
        }
    }

    @Scheduled(cron = "0 * * * * *")
    fun fulfillCouponScheduler(){
        CoroutineScope(Dispatchers.IO).launch {
            fulfillCoupon()
        }
    }

    suspend fun fulfillCoupon() {
        val coupons = couponRepository.findAllByTotalQuantityAfter(0)
        coupons.forEach { coupon ->
            coupon.id?.let { couponId ->
                stockCache.initializeStock(couponId = couponId, coupon.totalQuantity - coupon.issuedQuantity)
            }
        }
    }

    suspend fun sendCouponSuccessToRedis(eventId: String, savedCouponId: Long?) {
        val successJson = """
                {
                    "correlationId": "$eventId",
                    "status": "RESULT",
                    "data": { "couponId": ${savedCouponId} }
                }
            """.trimIndent()

        reactiveRedisTemplate.convertAndSend("coupon-completion-topic", successJson).subscribe()
    }

    private suspend fun sendCouponFailureToRedis(eventId: String, couponId: Long) {
        // JSON 깨짐 방지를 위해 따옴표(")는 작은따옴표(')로 치환하거나 이스케이프 처리
        val failJson = """
        {
            "correlationId": "$eventId",
            "status": "RESULT",
            "message": "실패하는 쿠폰 id $couponId"
        }
    """.trimIndent()

        // Redis로 발사!
        reactiveRedisTemplate.convertAndSend("coupon-completion-topic", failJson).subscribe()
    }

    suspend fun waitUntilSseResponse(correlationId: String): String? {
        val topic = ChannelTopic("coupon-completion-topic")

        return reactiveRedisTemplate.listenTo(topic)
            .map { it.message }
            .filter { it.contains(correlationId) }
            .next()
            .timeout(Duration.of(5, ChronoUnit.SECONDS))
            .awaitSingleOrNull()
    }
}
