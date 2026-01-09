package com.couponrefactroing.study

import com.couponrefactroing.service.CouponIssuer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.system.measureTimeMillis

@SpringBootTest
@Disabled
class CouponIssuerIntegrationTest {

    // 1. 테스트 대상: 실제 객체를 주입받습니다. (나머지 의존성은 스프링이 알아서 넣어줌)
    @Autowired
    lateinit var couponIssuer: CouponIssuer

    // 2. 조작이 필요한 빈만 가짜(@MockBean)로 교체합니다.
    // 주의: @MockBean은 Mockito 기반이라 Mockk 문법(every)과 섞어 쓰려면 설정이 좀 필요합니다.
    // 여기서는 편의상 SpringBootTest 환경에서 Mockk를 쓰기 위해 @SpykBean 대신
    // com.ninjasquad.springmockk 라이브러리를 쓰거나,
    // 그냥 Mockito를 쓰는게 편할 수 있습니다.
    // 하지만 위에서 MockK를 썼으니 MockkBean(SpringMockK 라이브러리 필요)을 쓴다고 가정합니다.

    // 만약 SpringMockK 라이브러리가 없다면 아래처럼 정의하고
    // @Test 안에서 ReflectionTestUtils 등으로 바꿔치기 해야 하는데 복잡하므로
    // 가장 현실적인 "SpringMockK" 라이브러리 사용을 가정합니다.
    // 👇 [핵심 1] 이름을 명시하여 @Qualifier("reactiveRedisTemplate")와 짝을 맞춰줍니다.
    @MockkBean(name = "reactiveRedisTemplate")
    lateinit var reactiveRedisTemplate: ReactiveRedisTemplate<String, String>

    // 👇 [핵심 2] Gateway 에러 방지용 (이름 명시 권장)
    @MockkBean(name = "reactiveStringRedisTemplate")
    lateinit var reactiveStringRedisTemplate: ReactiveStringRedisTemplate

    // 👇 [핵심 3] 생성자에 새로 추가하신 StringRedisTemplate도 Mocking 해야 합니다!
    // (안 하면 실제 Redis에 붙으려고 하거나 에러가 날 수 있음)
    @MockkBean(name = "stringRedisTemplate")
    lateinit var stringRedisTemplate: StringRedisTemplate
    // Redis 오퍼레이션 Mock (필요한 경우)
    @MockkBean
    lateinit var valueOps: ReactiveValueOperations<String, String>

    @MockkBean
    lateinit var stringValueOps: ValueOperations<String, String>
    // DB 연결 등이 부담스럽다면 다른 의존성들도 @MockkBean 처리하면 됩니다.
    // @MockkBean lateinit var repository: CouponRepository

    @Test
    fun `Redis pub_sub 전송이 완료될 때까지(awaitSingle) 기다려야 한다`() = runTest {
        // Given
        val eventId = "test-event-id"
        val couponId = 123L

        // 1. [동기] StringRedisTemplate: 그냥 아무 일 없이 통과시킴 (시간 0초 소요)
        every { stringRedisTemplate.opsForValue() } returns stringValueOps
        every { stringValueOps.set(any(), any(), any<Duration>()) } just Runs

        // 2. [비동기] ReactiveRedisTemplate: 여기가 핵심!
        // convertAndSend가 호출되면 "1초 뒤에 완료되는 Mono"를 리턴하도록 조작합니다.
        every { reactiveRedisTemplate.convertAndSend(any(), any()) } returns
                Mono.just(1L).delayElement(Duration.ofMillis(1000))

        // When
        val executionTime = measureTimeMillis {
            // 이 메소드 안의 awaitSingle()이 위에서 만든 1초짜리 Mono를 기다리는지 측정
            couponIssuer.sendCouponSuccessToRedis(eventId, couponId)
        }

        // Then
        println("실행 시간: ${executionTime}ms")

        // awaitSingle()이 있다면 1000ms 이상 걸려야 정상!
        // 만약 awaitSingle()을 빼먹었다면 코루틴이 Mono를 구독 안 하거나 기다리지 않고 0ms에 끝남.
        assertTrue(executionTime >= 1000, "awaitSingle()이 없어서 기다리지 않고 종료되었습니다! (실행시간: ${executionTime}ms)")

        // 검증
        verify { reactiveRedisTemplate.convertAndSend(any(), any()) }
    }
}
