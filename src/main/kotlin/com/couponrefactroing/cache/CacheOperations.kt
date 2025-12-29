package com.couponrefactroing.cache

/**
 * Redis 캐시 연산을 추상화한 공통 인터페이스
 * 도메인 서비스에서 직접 Redis에 의존하지 않도록 중간 계층 제공
 */
interface CacheOperations {
    /**
     * 값 증가 (원자적 연산)
     */
    suspend fun increment(key: String, delta: Long = 1): Long
    
    /**
     * 값 감소 (원자적 연산)
     */
    suspend fun decrement(key: String, delta: Long = 1): Long
    
    /**
     * 값 조회
     */
    suspend fun get(key: String): String?
    
    /**
     * 값 저장
     */
    suspend fun set(key: String, value: String, ttlSeconds: Long? = null)
    
    /**
     * 값 존재 여부 확인
     */
    suspend fun exists(key: String): Boolean
    
    /**
     * 값이 없을 때만 저장 (분산 락, 중복 체크에 활용)
     */
    suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean
    
    /**
     * 값 삭제
     */
    suspend fun delete(key: String): Boolean
}

