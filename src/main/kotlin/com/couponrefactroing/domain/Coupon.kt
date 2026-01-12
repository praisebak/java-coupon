package com.couponrefactroing.domain

import jakarta.persistence.*
import org.springframework.core.annotation.MergedAnnotationPredicates.unique
import java.time.LocalDateTime

@Entity
@Table(name = "coupons", indexes = [
    Index(name = "idx_coupon_valid_period", columnList = "validEndedAt, validStartedAt")
])
class Coupon(
    title: String,
    discountAmount: Int,
    minimumOrderPrice: Int,
    totalQuantity: Int,
    validStartedAt: LocalDateTime,
    validEndedAt: LocalDateTime
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false)
    var title: String = title

    @Column(nullable = false)
    var discountAmount: Int = discountAmount

    @Column(nullable = false)
    var minimumOrderPrice: Int = minimumOrderPrice

    // 전체 발행 가능한 수량 (재고)
    @Column(nullable = true)
    var totalQuantity: Int = Integer.MAX_VALUE

    // 현재까지 발행된 수량
    @Column(nullable = false)
    var issuedQuantity: Int = 0

    @Column(nullable = false)
    var validStartedAt: LocalDateTime = validStartedAt

    @Column(nullable = false)
    var validEndedAt: LocalDateTime = validEndedAt

    // 낙관적 락을 위한 버전 필드
    @Version
    var version: Long = 0

    // 생성 시점 자동 기록
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    /**
     * 비즈니스 로직: 쿠폰 발급 시도
     * - 기간 확인
     * - 재고 확인 (낙관적 락에 의해 동시성 제어됨)
     * - 수량 증가
     */
    fun decreaseQuantity() { // 혹은 issue() 라고 명명
        verifyExpiration()
        verifyQuantity()
        this.issuedQuantity += 1
    }

    /**
     * 검증 로직: 발급 가능한 시간인지 확인
     */
    private fun verifyExpiration() {
        val now = LocalDateTime.now()
        if (now.isBefore(validStartedAt) || now.isAfter(validEndedAt)) {
            throw IllegalStateException("쿠폰 발급 기간이 아닙니다.")
        }
    }

    /**
     * 검증 로직: 재고가 남아있는지 확인
     */
    private fun verifyQuantity() {
        if (totalQuantity == null) return // 무제한 쿠폰인 경우

        if (issuedQuantity >= totalQuantity!!) {
            throw IllegalStateException("준비된 쿠폰이 모두 소진되었습니다.")
        }
    }
}
