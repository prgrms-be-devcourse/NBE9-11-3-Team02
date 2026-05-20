package com.back.together02be.global.exceptionHandler

import com.back.together02be.global.apiRes.ApiRes
import com.back.together02be.global.exception.DuplicateRequestException
import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ApiRes<Void>> {
        val message = e.bindingResult.fieldErrors
            .firstOrNull()
            ?.defaultMessage
            ?: "입력값이 올바르지 않습니다."

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiRes(message, null))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ApiRes<Void>> {
        val message = e.constraintViolations
            .firstOrNull()
            ?.message
            ?: "입력값이 올바르지 않습니다."

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiRes(message, null))
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(e: EntityNotFoundException): ResponseEntity<ApiRes<Void>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiRes(e.message ?: "리소스를 찾을 수 없습니다.", null))

    @ExceptionHandler(DuplicateRequestException::class)
    fun handleDuplicateRequest(e: DuplicateRequestException): ResponseEntity<ApiRes<Void>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiRes(e.message ?: "중복 요청입니다.", null))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ApiRes<Void>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiRes(e.message ?: "잘못된 요청입니다.", null))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ApiRes<Void>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiRes(e.message ?: "잘못된 상태입니다.", null))

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiRes<Void>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiRes("서버 오류가 발생했습니다.", null))

}