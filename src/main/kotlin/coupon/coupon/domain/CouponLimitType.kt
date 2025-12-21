package coupon.coupon.domain

enum class CouponLimitType {
    NONE,
    ISSUE_COUNT,
    USE_COUNT
}

fun CouponLimitType?.isNotIssueCountLimit(): Boolean {
    return this == null || this == CouponLimitType.NONE || this == CouponLimitType.USE_COUNT
}

fun CouponLimitType?.isNotUseCountLimit(): Boolean {
    return this == null || this == CouponLimitType.NONE || this == CouponLimitType.ISSUE_COUNT
}



