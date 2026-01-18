package com.couponrefactroing.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties

@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id:coupon-group}")
    private val groupId: String
) {

    @Bean
    fun issueCouponTopic(): NewTopic {
        return TopicBuilder.name("issue-coupon")
            .partitions(4)
            .replicas(1)
            .build()
    }

    @Bean("batchKafkaListenerContainerFactory")
    fun batchKafkaListenerContainerFactory(
        batchConsumerFactory: ConsumerFactory<String, Any>
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = batchConsumerFactory
        factory.setBatchListener(true) // 배치 모드 활성화
        factory.containerProperties.ackMode = ContainerProperties.AckMode.BATCH // 배치 단위로 ACK
        factory.setConcurrency(20)
        return factory
    }

    @Bean
    fun batchConsumerFactory(): ConsumerFactory<String, Any> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to org.springframework.kafka.support.serializer.JsonDeserializer::class.java,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "100", // 50 → 100 (더 큰 배치)
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to "1", // 1024 → 1 (최소 대기 제거)
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to "0", // 500 → 0 (대기 없음)
            org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES to "com.couponrefactroing.dto",
            org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE to "com.couponrefactroing.dto.IssueCouponEvent",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.FETCH_MAX_BYTES_CONFIG to "52428800", // 50MB (기본값)
            ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG to "1048576" // 1MB (기본값)
        )
        return DefaultKafkaConsumerFactory(props)
    }
}
