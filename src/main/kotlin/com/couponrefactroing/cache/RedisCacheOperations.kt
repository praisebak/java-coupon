package com.couponrefactroing.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 캐시 연산 구현체
 */
@Component
class RedisCacheOperations(
    private val redisTemplate: StringRedisTemplate
) : CacheOperations {
    
    override suspend fun increment(key: String, delta: Long): Long {
        return withContext(Dispatchers.IO) {
            redisTemplate.opsForValue().increment(key, delta) ?: 0L
        }
    }
    
    override suspend fun decrement(key: String, delta: Long): Long {
        return withContext(Dispatchers.IO) {
            redisTemplate.opsForValue().decrement(key, delta) ?: 0L
        }
    }
    
    override suspend fun get(key: String): String? {
        return withContext(Dispatchers.IO) {
            redisTemplate.opsForValue().get(key)
        }
    }
    
    override suspend fun set(key: String, value: String, ttlSeconds: Long?) {
        withContext(Dispatchers.IO) {
            if (ttlSeconds != null) {
                redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS)
            } else {
                redisTemplate.opsForValue().set(key, value)
            }
        }
    }
    
    override suspend fun exists(key: String): Boolean {
        return withContext(Dispatchers.IO) {
            redisTemplate.hasKey(key)
        }
    }
    
    override suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean {
        return withContext(Dispatchers.IO) {
            redisTemplate.opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS) ?: false
        }
    }
    
    override suspend fun delete(key: String): Boolean {
        return withContext(Dispatchers.IO) {
            redisTemplate.delete(key)
        }
    }
}

