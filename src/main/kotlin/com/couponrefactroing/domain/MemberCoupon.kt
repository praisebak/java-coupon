package com.couponrefactroing.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("member_coupon")
data class MemberCoupon(
    @Id
    @Column("id")
    var id: Long? = null,

    @Column("member_id")
    var memberId: Long? = null,

    @Column("coupon_id")
    var couponId: Long? = null,

    @Column("used_at")
    var usedAt: LocalDateTime? = null,

    @Column("created_at")
    var createdAt: LocalDateTime? = null,

    @Column("modified_at")
    var modifiedAt: LocalDateTime? = null
)

