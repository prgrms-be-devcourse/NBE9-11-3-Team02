package com.back.together02be.global.extend

fun <T : Any> T?.getOrThrow(exception: () -> Exception): T =
    this ?: throw exception()