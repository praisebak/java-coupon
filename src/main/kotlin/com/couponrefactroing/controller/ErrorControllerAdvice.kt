package com.couponrefactroing.controller

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ErrorControllerAdvice {
    private val log: Logger = LoggerFactory.getLogger(ErrorControllerAdvice::class.java)


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
