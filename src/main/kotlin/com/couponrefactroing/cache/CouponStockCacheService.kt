package com.couponrefactroing.cache

import org.springframework.stereotype.Service

/**
 * 쿠폰 재고 관리 전용 캐시 서비스
 * Redis를 활용한 원자적 재고 차감으로 동시성 문제 해결
 */
@Service
class CouponStockCacheService(
    private val cacheOps: CacheOperations
) {

    /**
     * 쿠폰 재고 차감 (원자적 연산)
     * @return 차감 후 남은 재고 수량
     * @throws IllegalStateException 재고가 부족한 경우
     */
    suspend fun decreaseStock(couponId: Long): Long {
        val remaining = cacheOps.decrement(stockKey(couponId))

        if (remaining < 0) {
            cacheOps.increment(stockKey(couponId))
            throw IllegalStateException("쿠폰 재고가 부족합니다.")
        }

        return remaining
    }

    /**
     * 쿠폰 재고 초기화
     * 쿠폰 생성 시 또는 재고 리셋 시 사용
     */
    suspend fun setStock(couponId: Long, quantity: Int) {
        cacheOps.set(stockKey(couponId), quantity.toString())
    }

    /**
     * 현재 재고 조회
     */
    suspend fun getStock(couponId: Long): Long? {
        return cacheOps.get(stockKey(couponId))?.toLongOrNull()
    }

    private fun duplicateKey(couponId: Long, memberId: Long) =
        "coupon:issue:$couponId:member:$memberId"

    suspend fun validateAlreadyAssignedCoupon(couponId: Long, memberId: Long) {
        val key = duplicateKey(couponId, memberId)
        val successSet = cacheOps.setIfAbsent(key, "1", ttlSeconds = TTL_SECONDS)

        if(!successSet){
            throw IllegalArgumentException("이미 발급받은 쿠폰입니다.")
        }
    }

    suspend fun checkAndMark(couponId: Long, memberId: Long): Boolean {
        val key = duplicateKey(couponId, memberId)
        return cacheOps.setIfAbsent(key, "1", ttlSeconds = TTL_SECONDS)
    }

    private fun stockKey(couponId: Long) = "coupon:stock:$couponId"

    companion object {
        private const val TTL_SECONDS = 86400L
    }
}



