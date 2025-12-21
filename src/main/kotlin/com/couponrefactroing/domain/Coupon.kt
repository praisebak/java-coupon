package coupon.coupon.domain

import jakarta.persistence.*
import lombok.AccessLevel
import lombok.Getter
import lombok.NoArgsConstructor
import java.time.LocalDateTime

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private var id: Long? = null

    @Column(name = "discount_amount")
    private var discountAmount = 0

    @Column(name = "minimum_order_price")
    private var minimumOrderPrice = 0

    @Column(name = "coupon_status", columnDefinition = "VARCHAR(30)")
    @Enumerated(value = EnumType.STRING)
    private var couponStatus: CouponStatus? = null

    @Column(name = "issuable", columnDefinition = "BOOLEAN")
    private var issuable = false

    @Column(name = "usable", columnDefinition = "BOOLEAN")
    private var usable = false

    @Column(name = "issue_started_at", columnDefinition = "DATETIME(6)")
    private var issueStartedAt: LocalDateTime? = null

    @Column(name = "issue_ended_at", columnDefinition = "DATETIME(6)")
    private var issueEndedAt: LocalDateTime? = null

    @Column(name = "limit_type", columnDefinition = "VARCHAR(20)")
    @Enumerated(value = EnumType.STRING)
    private var limitType: CouponLimitType? = null

    @Column(name = "issue_limit")
    private var issueLimit: Long? = null

    @Column(name = "issue_count")
    private var issueCount: Long? = null

    @Column(name = "use_limit")
    private var useLimit: Long? = null

    @Column(name = "use_count")
    private var useCount: Long? = null

    @Column(name = "created_at", columnDefinition = "DATETIME(6)")
    private var createdAt: LocalDateTime? = null

    @Column(name = "modified_at", columnDefinition = "DATETIME(6)")
    private var modifiedAt: LocalDateTime? = null

    fun issue() {
        require(!(this.issueStartedAt!!.isAfter(LocalDateTime.now()) || this.issueEndedAt!!.isBefore(LocalDateTime.now()))) { "쿠폰을 발급할 수 없는 시간입니다." }
        require(!(couponStatus.isNotIssuable() || !this.issuable)) { "쿠폰을 발급할 수 없는 상태입니다." }
        if (this.limitType.isNotIssueCountLimit()) {
            return
        }
        require(this.issueLimit!! > this.issueCount!!) { "쿠폰을 더 이상 발급할 수 없습니다." }
        this.issueCount = this.issueCount!! + 1
    }

    fun isIssuableCoupon(localDateTime: LocalDateTime?): Boolean {
        if (this.issueStartedAt!!.isAfter(localDateTime) || this.issueEndedAt!!.isBefore(localDateTime)) {
            return false
        }
        if (couponStatus.isNotIssuable() || !this.issuable) {
            return false
        }
        if (this.limitType.isNotIssueCountLimit()) {
            return true
        }
        return this.issueLimit!! > this.issueCount!!
    }

    fun use() {
        require(!(couponStatus.isNotUsable() || !this.usable)) { "쿠폰 사용이 불가능합니다." }
        if (this.limitType.isNotUseCountLimit()) {
            return
        }
        require(this.useLimit!! > this.useCount!!) { "쿠폰을 더 이상 사용할 수 없습니다." }
    }

    val isUsableCoupon: Boolean
        get() {
            if (couponStatus.isNotUsable() || !this.usable) {
                return false
            }
            if (this.limitType.isNotUseCountLimit()) {
                return true
            }
            return this.useLimit!! > this.useCount!!
        }

    fun setStatus(couponStatus: CouponStatus) {
        this.couponStatus = couponStatus
    }

    fun setIssuable(issuable: Boolean) {
        this.issuable = issuable
    }

    fun setUsable(usable: Boolean) {
        this.usable = usable
    }
}
