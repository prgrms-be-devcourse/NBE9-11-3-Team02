package com.back.together02be.global.exceptionHandler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.back.together02be.global.apiRes.ApiRes;
import com.back.together02be.global.exception.DuplicateRequestException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiRes<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.map(FieldError::getDefaultMessage)
			.findFirst()
			.orElse("입력값이 올바르지 않습니다.");

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiRes<>(message, null));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiRes<Void>> handleConstraintViolation(ConstraintViolationException e) {
		String message = e.getConstraintViolations()
			.stream()
			.findFirst()
			.map(v -> v.getMessage())
			.orElse("입력값이 올바르지 않습니다.");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiRes<>(message, null));
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ApiRes<Void>> handleEntityNotFound(EntityNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiRes<>(e.getMessage(), null));
	}

	@ExceptionHandler(DuplicateRequestException.class)
	public ResponseEntity<ApiRes<Void>> handleDuplicateRequest(DuplicateRequestException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiRes<>(e.getMessage(), null));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiRes<Void>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiRes<>(e.getMessage(), null));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiRes<Void>> handleIllegalState(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiRes<>(e.getMessage(), null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiRes<Void>> handleException(Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ApiRes<>("서버 오류가 발생했습니다.", null));
	}
}