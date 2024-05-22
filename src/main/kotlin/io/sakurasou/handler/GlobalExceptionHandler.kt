package io.sakurasou.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.exception.ImageFetchException
import io.sakurasou.exception.WrongParameterException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * @author mashirot
 * 2024/5/16 13:23
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = KotlinLogging.logger { this::class.java }

    @ExceptionHandler(WrongParameterException::class)
    fun handleWrongParameterException(e: WrongParameterException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.message)
    }

    @ExceptionHandler(ImageFetchException::class)
    fun handleImageFetchException(e: ImageFetchException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.message)
    }

    @ExceptionHandler
    fun handleException(e: Exception): ResponseEntity<String> {
        logger.error(e) { "handle server error: ${e.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.message)
    }

}