package com.couponrefactroing.service

import com.couponrefactroing.cache.CouponIssueDuplicateChecker
import com.couponrefactroing.cache.CouponStockCacheService
import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.dto.CouponAddRequest
import com.couponrefactroing.dto.IssueCouponEvent
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.repository.MemberCouponRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.collect
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

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
    private val stockCache: CouponStockCacheService,
    private val duplicateChecker: CouponIssueDuplicateChecker,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val kafkaTemplate: KafkaTemplate<String, IssueCouponEvent>,
    private val objectMapper: ObjectMapper
) : CouponIssueService {

    private val log = LoggerFactory.getLogger(this::class.java)

    // [핵심] 대기 중인 요청을 저장하는 우편함 (Key: correlationId, Value: 응답받을 Future)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val topic = ChannelTopic("coupon-completion-topic")

    /**
     * [초기화] Redis 리스너를 단 하나만 실행 (Dispatcher 역할)
     * 메시지가 오면 JSON을 파싱해서 ID를 찾고, 해당 ID를 기다리는 요청자에게 전달
     */
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
            .subscribe() // 구독 시작 (WebFlux 방식)
    }

    /**
     * JSON 라이브러리 대신 단순 문자열 연산으로 ID 추출 (CPU 절약)
     * 예: { "correlationId": "abc-123", ... } -> "abc-123"
     */
    private fun extractCorrelationId(json: String): String {
        val key = "\"correlationId\": \""
        val start = json.indexOf(key)
        if (start == -1) return ""

        val valueStart = start + key.length
        val valueEnd = json.indexOf("\"", valueStart)
        return if (valueEnd != -1) json.substring(valueStart, valueEnd) else ""
    }

    // --- [1] 쿠폰 생성 로직 (기존 유지) ---
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

            stockCache.initializeStock(couponId, savedCoupon.totalQuantity)
            couponId
        }
    }

    // --- [2] 쿠폰 발급 요청 (Producer) ---
    override suspend fun issueCoupon(couponId: Long, memberId: Long, correlationId: String): String {
        val event = IssueCouponEvent(memberId, couponId, correlationId)
        kafkaTemplate.send("issue-coupon", event)
        return correlationId
    }

    // --- [3] 쿠폰 발급 처리 (Consumer) ---
    @KafkaListener(topicPattern = "issue-coupon", concurrency = "20")
    suspend fun processCouponIssue(issueCouponEvent: IssueCouponEvent) {
        // KafkaListener는 기본적으로 별도 스레드지만, IO 작업을 명시
        withContext(Dispatchers.IO) {
            val memberId = issueCouponEvent.memberId
            val couponId = issueCouponEvent.couponId
            val eventId = issueCouponEvent.eventId

            try {
                // 1. 유효성 검사 (필요시 주석 해제)
                // memberFrontmen.validateExistMember(memberId)
                // validateAlreadyAssignedCoupon(couponId, memberId)

                // 2. 재고 감소 (Redis)
                stockCache.decreaseStock(couponId)

                // 3. DB 저장 (테스트 위해 주석 처리 상태 유지)
                /*
                transactionTemplate.execute {
                    decreaseStockDB(couponId)
                    saveMemberCoupon(memberId, couponId)
                }
                */

                // 4. 성공 알림 전송
                sendCouponSuccessToRedis(eventId, couponId)

            } catch (e: Exception) {
                duplicateChecker.clearMark(couponId, memberId)
                sendCouponFailureToRedis(eventId, couponId)
            }
        }
    }

    // --- [4] 결과 전송 및 대기 로직 (최적화됨) ---

    suspend fun sendCouponSuccessToRedis(eventId: String, savedCouponId: Long?) {
        val successJson = """
            {
                "correlationId": "$eventId",
                "status": "RESULT",
                "data": { "couponId": $savedCouponId }
            }
        """.trimIndent()

        // awaitSingle()을 사용하여 전송 완료를 보장
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

    /**
     * [핵심 변경] O(N^2) -> O(1) 성능 개선
     * Map에 내 요청을 등록하고, 리스너가 채워주기를 기다림
     */
    suspend fun waitUntilSseResponse(correlationId: String): String {
        // 1. 빈 약속 상자(Deferred) 생성
        val deferred = CompletableDeferred<String>()

        // 2. 맵에 등록 ("이 ID로 연락 오면 이 상자에 넣어주세요")
        pendingRequests[correlationId] = deferred

        try {
            // 3. 60초 동안 답이 올 때까지 '일시 중단' (스레드는 다른 일 하러 감!)
            return withTimeout(60000) {
                deferred.await() // 여기서 답이 올 때까지 멈춤 (Blocking 아님)
            }
        } catch (e: TimeoutCancellationException) {
            throw TimeoutException("Redis 응답 시간 초과 (15초)")
        } finally {
            // 4. 성공하든 실패하든 맵에서 제거 (메모리 누수 방지)
            pendingRequests.remove(correlationId)
        }
    }

    suspend fun issueWithWait(
        couponId: Long,
        memberId: Long,
        correlationId: String
    ): String = coroutineScope {

        val responseDeferred = async {
            withTimeout(5000) {
                waitUntilSseResponse(correlationId)
            }
        }

        issueCoupon(couponId, memberId, correlationId)

        return@coroutineScope responseDeferred.await()
    }

    // --- [5] Helper Methods (기존 로직 유지) ---
    /*
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
    */

    // 필요 시 스케줄러 복구
    /*
    @Scheduled(cron = "0 * * * * *")
    fun fulfillCouponScheduler(){
        CoroutineScope(Dispatchers.IO).launch {
            val coupons = couponRepository.findAllByTotalQuantityAfter(0)
            // ... 로직
        }
    }
    */
}
