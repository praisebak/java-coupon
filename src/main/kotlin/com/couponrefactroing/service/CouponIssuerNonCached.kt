//package com.couponrefactroing.service
//
//import com.couponrefactroing.domain.Coupon
//import com.couponrefactroing.domain.MemberCoupon
//import com.couponrefactroing.dto.CouponAddRequest
//import com.couponrefactroing.repository.CouponRepository
//import com.couponrefactroing.repository.MemberCouponRepository
//import jakarta.annotation.PostConstruct
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.hibernate.exception.ConstraintViolationException
//import org.springframework.dao.DataIntegrityViolationException
//import org.springframework.orm.ObjectOptimisticLockingFailureException
//import org.springframework.stereotype.Component
//import org.springframework.transaction.annotation.Transactional
//import java.time.LocalDateTime
//
///**
// * 쿠폰 발급 로직 - 순수 DB 버전 (Redis 캐시 미사용)
// *
// * 동시성 제어 전략:
// * 1. 낙관적 락 (@Version)으로 재고 차감 동시성 제어
// * 2. DB 유니크 제약조건 (member_coupon_constraint)으로 중복 발급 방지
// *
// * 장점:
// * - Redis 의존성 없음 (인프라 단순화)
// * - 데이터 정합성 보장
// *
// * 단점:
// * - DB 부하가 높음 (대용량 트래픽 시 성능 저하)
// * - 낙관적 락 충돌 시 재시도 필요
// */
//@Component
//class CouponIssuerNonCached(
//    private val couponRepository: CouponRepository,
//    private val memberCouponRepository: MemberCouponRepository,
//    private val memberFrontmen: MemberFrontMen
//) : CouponIssueService {
//
//    @PostConstruct
//    fun afterInit() {
//        println("✓ CouponIssuerNonCached enabled (순수 DB 모드)")
//    }
//
//    /**
//     * 쿠폰 생성
//     */
//    @Transactional
//    override suspend fun addCoupon(memberId: Long, couponInformation: CouponAddRequest): Long {
//        return withContext(Dispatchers.IO) {
//            // 멤버 존재 확인
//            memberFrontmen.validateExistMember(memberId)
//
//            val now = LocalDateTime.now()
//            val defaultValidEndAt = now.plusYears(1)
//
//            val coupon = Coupon(
//                title = couponInformation.couponSummery,
//                discountAmount = couponInformation.subtractAmount.toInt(),
//                minimumOrderPrice = 0,
//                totalQuantity = Int.MAX_VALUE,
//                validStartedAt = now,
//                validEndedAt = defaultValidEndAt
//            )
//
//            val savedCoupon = couponRepository.save(coupon)
//            savedCoupon.id ?: throw IllegalStateException("쿠폰 생성 실패")
//        }
//    }
//
//    /**
//     * 쿠폰 발급 (순수 DB 버전)
//     *
//     * 동시성 제어:
//     * 1. 낙관적 락으로 재고 차감 (coupon.version 증가)
//     * 2. DB 유니크 제약조건으로 중복 발급 방지
//     *
//     * @throws IllegalArgumentException 쿠폰이 없거나, 이미 발급된 경우
//     * @throws IllegalStateException 재고 부족, 기간 만료 등
//     * @throws ObjectOptimisticLockingFailureException 동시성 충돌 (재시도 필요)
//     */
//    @Transactional
//    override fun issueCoupon(couponId: Long, memberId: Long, eventId: String) {
//        return withContext(Dispatchers.IO) {
//            try {
//                validateIssueCoupon(memberId, couponId)
//
//                val coupon = getCouponById(couponId)
//
//                coupon.decreaseQuantity()
//                couponRepository.save(coupon) // @Version 자동 증가
//
//                val now = LocalDateTime.now()
//                val memberCoupon = MemberCoupon(
//                    memberId = memberId,
//                    couponId = couponId,
//                    usedAt = null,
//                    createdAt = now,
//                    modifiedAt = now
//                )
//
//                val savedMemberCoupon = memberCouponRepository.save(memberCoupon)
//                savedMemberCoupon
//            } catch (e: Exception) {
//                throw IllegalArgumentException("유효하지 않은 쿠폰입니다: ${e.message}", e)
//            }
//        }
//    }
//
//    private suspend fun validateIssueCoupon(memberId: Long, couponId: Long) {
//        memberFrontmen.validateExistMember(memberId)
//        validateMemberCoupon(couponId, memberId)
//    }
//
//    private fun getCouponById(couponId: Long): Coupon = couponRepository.findById(couponId)
//        .orElseThrow { IllegalArgumentException("쿠폰을 찾을 수 없습니다. couponId: $couponId") }
//
//    private fun validateMemberCoupon(
//        couponId: Long,
//        memberId: Long,
//    ) {
//        memberCouponRepository
//            .findByCouponIdAndMemberId(couponId, memberId)
//            .ifPresent { throw RuntimeException("이미 발급받은 쿠폰입니다.") }
//    }
//}
//
