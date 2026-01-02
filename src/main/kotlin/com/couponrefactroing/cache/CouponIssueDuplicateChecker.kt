package com.couponrefactroing.cache

import org.springframework.stereotype.Service

/**
 * 쿠폰 중복 발급 체크 전용 캐시 서비스
 * Redis의 SETNX(setIfAbsent)를 활용한 원자적 중복 체크
 */
@Service
class CouponIssueDuplicateChecker(
    private val cacheOps: CacheOperations
) {
    /**
     * 중복 발급 체크 및 마킹 (원자적 연산)
     * @return true: 최초 발급 가능, false: 이미 발급됨
     */
    suspend fun checkAndMark(couponId: Long, memberId: Long): Boolean {
        val key = duplicateKey(couponId, memberId)
        // setIfAbsent: 키가 없으면 true 반환 (최초 발급), 있으면 false (이미 발급됨)
        return cacheOps.setIfAbsent(key, "1", ttlSeconds = TTL_SECONDS)
    }
    
    /**
     * 발급 이력 삭제 (테스트 또는 관리 목적)
     */
    suspend fun clearMark(couponId: Long, memberId: Long): Boolean {
        return cacheOps.delete(duplicateKey(couponId, memberId))
    }
    
    /**
     * 발급 여부 확인 (조회만)
     */
    suspend fun isAlreadyIssued(couponId: Long, memberId: Long): Boolean {
        return cacheOps.exists(duplicateKey(couponId, memberId))
    }
    
    private fun duplicateKey(couponId: Long, memberId: Long) = 
        "coupon:issue:$couponId:member:$memberId"
    
    companion object {
        // 중복 체크 데이터 보관 기간: 24시간
        // (실제 발급 내역은 DB에 영구 저장되므로, 캐시는 빠른 중복 체크용)
        private const val TTL_SECONDS = 86400L
    }
}


