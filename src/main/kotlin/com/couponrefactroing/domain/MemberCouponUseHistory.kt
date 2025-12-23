package com.couponrefactroing.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "member_coupon_use_history",
    uniqueConstraints = [
        UniqueConstraint(name = "member_coupon_use_history_constraint", columnNames = arrayOf("member_coupon_id"))
    ]
)

data class MemberCouponUseHistory( 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "member_coupon_id")
    var memberCouponId: Long? = null,

    @Column(name = "member_id")
    var memberId: Long? = null,

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,

    @Version
    @Column(name = "optimistic_lock_version")
    var optimisticLockVersion: Long? = null,
)

