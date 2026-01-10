package com.couponrefactroing.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import io.lettuce.core.api.StatefulConnection
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import java.time.Duration

@Configuration
class RedisConfig {

    @Value("\${spring.redis.host}")
    private lateinit var host: String

    @Value("\${spring.redis.port}")
    private var port: Int = 6379

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val configuration = RedisStandaloneConfiguration(host, port)

        // 커넥션 풀 설정
        val poolConfig = GenericObjectPoolConfig<Any>().apply {
            maxTotal = 100  // max-active
            maxIdle = 50
            minIdle = 10
            setMaxWait(Duration.ofMillis(10000)) // max-wait
        }

        val clientConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(5000))
            .clientOptions(
                ClientOptions.builder()
                    .socketOptions(SocketOptions.builder().keepAlive(true).build())
                    .build()
            )
            .poolConfig(poolConfig as GenericObjectPoolConfig<StatefulConnection<*, *>>)
            .build()

        // LettuceConnectionFactory 생성 시 shareNativeConnection을 false로 세팅
        val factory = LettuceConnectionFactory(configuration, clientConfig)
        factory.shareNativeConnection = false // [여기!] 이게 true면 풀 안씀

        return factory
    }
}
