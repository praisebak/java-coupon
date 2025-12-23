package com.couponrefactroing.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "members")
class Member(
    name: String,
    email: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long? = null

    @Column(name = "name", nullable = false)
    var name: String = name

    @Column(name = "email")
    var email: String? = email

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
}

