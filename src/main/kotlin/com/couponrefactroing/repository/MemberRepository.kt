package com.couponrefactroing.repository

import com.couponrefactroing.domain.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : JpaRepository<Member, Long>
