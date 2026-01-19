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
import jakarta.transaction.Transactional
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
    private val memberCouponRepository: MemberCouponRepository
) : CouponIssueService {

    private val log = LoggerFactory.getLogger(this::class.java)

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

    @Transactional
    override fun issueCoupon(couponId: Long, memberId: Long, eventId: String) {
            memberFrontmen.validateExistMember(memberId)

            decreaseStockDB(couponId)
            saveMemberCoupon(memberId, couponId)
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
}
