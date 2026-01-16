package com.couponrefactroing.service

import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.domain.MemberCouponUseHistory
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.repository.MemberCouponRepository
import com.couponrefactroing.repository.MemberCouponUseHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MemberCouponService(
    private val memberCouponRepository: MemberCouponRepository,
    private val memberCouponUseHistoryRepository: MemberCouponUseHistoryRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private suspend fun <T> measureMillisSuspend(
        label: String,
        key: String,
        block: suspend () -> T
    ): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            log.info(
                "PERF label={} key={} elapsedMs={}",
                label,
                key,
                elapsedMs
            )
        }
    }

    suspend fun findUsableMemberCoupons(memberId: Long): List<MemberCoupon> {
        return measureMillisSuspend("db_find_member_coupons_ms", memberId.toString()) {
            withContext(Dispatchers.IO) {
                memberCouponRepository.findByMemberId(memberId)
            }
        }
    }

    //형식상 붙인것
    @Transactional
    suspend fun addMemberCoupon(memberCouponId: Long, memberId: Long) {
        measureMillisSuspend("db_add_member_coupon_tx_ms", "$memberId:$memberCouponId") {
            withContext(Dispatchers.IO) {
                val memberCoupon = memberCouponRepository.findById(memberCouponId)
                    .orElseThrow { IllegalArgumentException("Member coupon not found") }

                if (!memberCoupon.isSameMember(memberId)) {
                    throw IllegalArgumentException("Member coupon does not belong to this member")
                }

                // 이미 사용된 쿠폰인지 체크
                if (memberCoupon.isUsed()) {
                    throw IllegalStateException("이미 사용된 쿠폰입니다.")
                }

                val now = LocalDateTime.now()
                memberCoupon.use(now)
                memberCouponRepository.save(memberCoupon)

                memberCouponUseHistoryRepository.save(MemberCouponUseHistory(
                    memberCouponId = memberCouponId,
                    memberId = memberId,
                    usedAt = now,
                    createdAt = now
                ))
            }
        }
    }

    //형식상 붙인것
    @Transactional
    suspend fun useCoupon(memberId: Long, memberCouponId: Long) {
        measureMillisSuspend("db_use_coupon_tx_ms", "$memberId:$memberCouponId") {
            withContext(Dispatchers.IO) {
                val memberCoupon = memberCouponRepository.findById(memberCouponId)
                    .orElseThrow { IllegalArgumentException("Member coupon not found") }

                if (!memberCoupon.isSameMember(memberId)) {
                    throw IllegalArgumentException("Member coupon does not belong to this member")
                }

                // 이미 사용된 쿠폰인지 체크
                if (memberCoupon.isUsed()) {
                    throw IllegalStateException("이미 사용된 쿠폰입니다.")
                }

                // 중복 사용 이력 체크 (DB 제약 조건 보완)
                val existingHistory = memberCouponUseHistoryRepository.findByMemberCouponId(memberCouponId)
                if (existingHistory.isNotEmpty()) {
                    throw IllegalStateException("이미 사용된 쿠폰입니다.")
                }

                val now = LocalDateTime.now()
                memberCoupon.use(now)
                memberCouponRepository.save(memberCoupon)

                memberCouponUseHistoryRepository.save(MemberCouponUseHistory(
                    memberCouponId = memberCouponId,
                    memberId = memberId,
                    usedAt = now,
                    createdAt = now
                ))
            }
        }
    }
}
