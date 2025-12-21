package coupon.coupon.domain

enum class CouponStatus(val status: String) {
    REMAIN("사용 가능"),
    ISSUED("발급됨"),
    USED("사용됨"),
    EXPIRED("만료됨")
}

fun CouponStatus?.isNotIssuable(): Boolean {
    return this == null || this == CouponStatus.USED || this == CouponStatus.EXPIRED
}

fun CouponStatus?.isNotUsable(): Boolean {
    return this == null || this == CouponStatus.USED || this == CouponStatus.EXPIRED
}
