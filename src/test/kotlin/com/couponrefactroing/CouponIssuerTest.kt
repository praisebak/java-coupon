package com.couponrefactroing

import com.couponrefactroing.domain.Coupon
import com.couponrefactroing.dto.IssueCouponEvent
import com.couponrefactroing.repository.CouponRepository
import com.couponrefactroing.service.CouponIssuer
import com.couponrefactroing.service.CouponService
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.core.KafkaTemplate
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponServiceSpec(
    private val couponIssuer: CouponIssuer,
    private val couponRepository: CouponRepository,

    @MockkBean
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,

    @MockkBean
    private val kafkaTemplate: KafkaTemplate<String, IssueCouponEvent>
) : BehaviorSpec({

    fun CouponRepository.getByCouponId(id: Long): Coupon {
        return findByIdOrNull(id) ?: throw IllegalArgumentException("쿠폰 없음")
    }

    val couponRepository = mockk<CouponRepository>()
    val service = CouponService(couponRepository)

    Given("유저가 쿠폰 발급을 시도할 때") {
        val userId = 100L

        When("정상적인 요청이라면") {
            val couponId = 1L
            val prevIssuedQuantity = couponRepository.getByCouponId(couponId).issuedQuantity

            couponIssuer.processCouponIssue(listOf(IssueCouponEvent(1L,couponId,"eventId-1",1)))

            Then("쿠폰의 발급된 개수가 늘어난다.") {
                eventually(5.seconds) {
                    val currQuantity = couponRepository.getByCouponId(couponId).issuedQuantity
                    currQuantity shouldBe prevIssuedQuantity + 1
                }
            }
        }

        //다음 테스트에서 어떻게 데이터의 무결성을 지킬것인가
        When("이미 발급받은 유저라면") {
            Then("에러를 던져야 한다") {
                // assertThrows<DuplicateException> { ... }
            }
        }

        When("이미 모두 발급됐다면") {
            Then("에러를 던져야 한다") {
                // assertThrows<DuplicateException> { ... }
            }
        }
    }
})
