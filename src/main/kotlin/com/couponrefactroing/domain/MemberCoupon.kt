package com.couponrefactroing.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "member_coupon",
    uniqueConstraints = [
        UniqueConstraint(name = "member_coupon_constraint", columnNames = arrayOf("coupon_id","member_id"))
    ]
)
class MemberCoupon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long? = null,

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long? = null,

    @Column(name = "used_at", nullable = false)
    var usedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "modified_at", nullable = false)
    var modifiedAt: LocalDateTime? = null
) {
    fun isSameMember(memberId: Long): Boolean {
        return this.memberId == memberId
    }

    fun isUsed(): Boolean {
        return usedAt != null
    }

    fun use(usedAt: LocalDateTime = LocalDateTime.now()) {
        if (this.usedAt != null) {
            throw IllegalStateException("이미 사용된 쿠폰입니다.")
        }
        this.usedAt = usedAt
        this.modifiedAt = LocalDateTime.now()
    }
}

