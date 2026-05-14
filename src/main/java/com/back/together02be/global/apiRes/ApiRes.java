package com.back.together02be.global.apiRes;

public record ApiRes<T>(
	String message,
	T data
) {
}
