package com.couponrefactroing.repository

import com.couponrefactroing.domain.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CouponRepository : JpaRepository<Coupon, Long>{
    fun findAllByTotalQuantityAfter(totalQuantity: Int) : List<Coupon>

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 WHERE c.id = :id AND c.issuedQuantity < c.totalQuantity")
    fun increaseIssuedQuantity(@Param("id") couponId: Long): Int
}
