package com.couponrefactroing.service

import com.couponrefactroing.exception.UserFlowException
import com.couponrefactroing.repository.MemberRepository
import org.springframework.stereotype.Component

@Component
class MemberFrontMen(
    private val memberRepository: MemberRepository
) {
    fun validateExistMember(memberId: Long) {
        memberRepository.findById(memberId).orElseThrow {
            UserFlowException("존재하지 않는 멤버입니다.")
        }
    }
}
