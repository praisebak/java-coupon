package com.couponrefactroing.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ErrorControllerAdvice {

    @ExceptionHandler(value = [IllegalArgumentException::class, IllegalStateException::class])
    fun illegalException(e: RuntimeException): ResponseEntity<String> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(e.message)
    }

    @ExceptionHandler(value = [Exception::class])
    fun illegalException(e : Exception): ResponseEntity<String> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(e.message)
    }
}
