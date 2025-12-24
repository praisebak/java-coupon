package com.couponrefactroing.service

import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.domain.MemberCoupon
import com.couponrefactroing.dto.CouponAddRequest
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.repository.MemberCouponRepository
import com.couponrefactroing.service.MemberFrontMen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class CouponIssuer(
    private val couponRepository : CouponRepository,
    private val memberCouponRepository: MemberCouponRepository,
    private val memberFrontmen : MemberFrontMen
) {
    @Transactional
    suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long {
        return withContext(Dispatchers.IO) {
            // 멤버 존재 확인
            memberFrontmen.validateExistMember(memberId)

            // CouponAddRequest를 Coupon 엔티티로 매핑
            // 부족한 필드는 기본값으로 설정
            val now = LocalDateTime.now()
            val defaultValidEndAt = now.plusYears(1) // 기본 유효기간: 1년

            val coupon = Coupon(
                title = couponInformation.couponSummery, // couponSummery -> title
                discountAmount = couponInformation.subtractAmount.toInt(), // subtractAmount -> discountAmount
                minimumOrderPrice = 0, // 기본값: 최소 주문 금액 0원
                totalQuantity = Int.MAX_VALUE, // 기본값: 무제한 발급 (실제로는 null을 받을 수 없으므로 큰 값 사용)
                validStartedAt = now, // 기본값: 현재 시간부터
                validEndedAt = defaultValidEndAt // 기본값: 1년 후까지
            )

            couponRepository.save(coupon).id ?: throw IllegalStateException("쿠폰 생성 실패")
        }
    }

    @Transactional
    suspend fun issueCoupon(couponId: Long, memberId: Long): MemberCoupon {
        return withContext(Dispatchers.IO) {
            // 1. 멤버 존재 확인
            memberFrontmen.validateExistMember(memberId)

            // 2. 쿠폰 조회
            val coupon = couponRepository.findById(couponId)
                .orElseThrow { IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: $couponId") }

            // 3. 중복 발급 체크
            val existingMemberCoupon = memberCouponRepository.findByCouponIdAndMemberId(couponId, memberId)
            if (existingMemberCoupon != null) {
                throw IllegalStateException("이미 발급받은 쿠폰입니다.")
            }

            // 4. 쿠폰 재고 차감 (낙관적 락으로 동시성 제어)
            coupon.decreaseQuantity()
            couponRepository.save(coupon)

            // 5. MemberCoupon 생성 및 저장
            val now = LocalDateTime.now()
            val memberCoupon = MemberCoupon(
                memberId = memberId,
                couponId = couponId,
                usedAt = now,
                createdAt = now,
                modifiedAt = now
            )
            memberCouponRepository.save(memberCoupon)
        }
    }
}
