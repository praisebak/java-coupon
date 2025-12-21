package com.couponrefactroing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = ["com.couponrefactroing.repository"])
class CouponRefactroingApplication

fun main(args: Array<String>) {
    runApplication<CouponRefactroingApplication>(*args)
}
