package com.couponrefactroing.service

import com.couponrefactroing.cache.CouponIssueDuplicateChecker
import com.couponrefactroing.cache.CouponStockCacheService
import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.dto.CouponAddRequest
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.repository.MemberCouponRepository
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 쿠폰 발급 로직을 담당하는 컴포넌트
 * Redis 캐시를 활용한 대용량 트래픽 처리 최적화
 */
@Component
@ConditionalOnProperty(value = ["coupon.cache.enable"], havingValue = "true")
class CouponIssuer(
    private val couponRepository: CouponRepository,
    private val memberCouponRepository: MemberCouponRepository,
    private val memberFrontmen: MemberFrontMen,
    private val stockCache: CouponStockCacheService,
    private val duplicateChecker: CouponIssueDuplicateChecker,
    private val redisTemplate: StringRedisTemplate
) {

    @PostConstruct
    fun afterInit(){
        println("enabled coupon cached")
    }

    @Transactional
    suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long {
        return withContext(Dispatchers.IO) {
            // 멤버 존재 확인
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
            savedCoupon.totalQuantity?.let {
                stockCache.initializeStock(couponId, it)
            }

            couponId
        }
    }

    /**
     * 멤버에게 쿠폰 발급 (Redis 캐시 우선 전략)
     *
     * 동시성 제어 전략:
     * 1. Redis에서 원자적 재고 차감
     * 2. Redis에서 중복 발급 체크
     * 3. DB에 발급 내역 저장 (비동기 가능)
     */
    @Transactional
    suspend fun issueCoupon(couponId: Long, memberId: Long): MemberCoupon {
        return withContext(Dispatchers.IO) {
            // 1. 멤버 존재 확인
            memberFrontmen.validateExistMember(memberId)

            // 2. Redis에서 중복 발급 체크 (원자적)
            val canIssue = duplicateChecker.checkAndMark(couponId, memberId)
            if (!canIssue) {
                throw IllegalStateException("이미 발급받은 쿠폰입니다.")
            }
            //setIfAbsent 동작안한경우에는 어떻게되는지 추적

            try {
                stockCache.decreaseStock(couponId)

                // 4. 쿠폰 조회 (유효성 검증용)
                val coupon = couponRepository.findById(couponId)
                    .orElseThrow { IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: $couponId") }

                // 5. DB에 재고 차감 반영 (낙관적 락)
                coupon.decreaseQuantity()
                val savedCoupon = couponRepository.save(coupon)

                if(savedCoupon.issuedQuantity == savedCoupon.totalQuantity){
                    throw IllegalStateException("이미 모두 발급된 쿠폰입니다.");
                }


                // 6. MemberCoupon 생성 및 저장
                val now = LocalDateTime.now()
                val memberCoupon = MemberCoupon(
                    memberId = memberId,
                    couponId = couponId,
                    usedAt = null, // 발급 시에는 사용 전 상태
                    createdAt = now,
                    modifiedAt = now
                )
                memberCouponRepository.save(memberCoupon)
            } catch (e: Exception) {
                // 실패 시 중복 체크 마킹 제거 (보상 트랜잭션)
                duplicateChecker.clearMark(couponId, memberId)
                throw e
            }
        }
    }

    @Scheduled(cron = "0 * * * * *")
    fun fulfillCouponScheduler(){
        //0 이상인거 가져와서 처리해야함
        //중복은 분산락 걸어서 해결

        CoroutineScope(Dispatchers.IO).launch {
            fulfillCoupon()
        }
    }

    private suspend fun fulfillCoupon() {
        val coupons = couponRepository.findAllByTotalQuantityAfter(0)
        coupons.forEach { coupon ->
            coupon.id?.let { couponId ->
                println(couponId)
                stockCache.initializeStock(couponId = couponId, coupon.totalQuantity - coupon.issuedQuantity)
            }
        }
    }
}
