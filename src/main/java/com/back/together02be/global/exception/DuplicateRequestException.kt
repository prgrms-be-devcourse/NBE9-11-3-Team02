package com.back.together02be.global.exception

class DuplicateRequestException(override val message: String) : RuntimeException(message)
