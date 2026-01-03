package com.couponrefactroing.dto

sealed interface CouponIssueState {
    data class InProgress(val correlationId: String, val message: String) : CouponIssueState
    data class Complete(val resultJson: String) : CouponIssueState
    data class Error(val message: String) : CouponIssueState
}
