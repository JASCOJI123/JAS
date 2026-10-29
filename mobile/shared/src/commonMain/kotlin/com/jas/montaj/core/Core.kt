package com.jas.montaj.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/** Platformadan mustaqil tasodifiy identifikator. */
fun newId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString { repeat(16) { append(chars[Random.nextInt(chars.length)]) } }
}

/** Kotlin common'da String.format yo'q — kerakli formatlar shu yerda. */
object Format {
    /** 75.4 -> "1:15" */
    fun clock(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).roundToInt()
        return "${total / 60}:${pad2(total % 60)}"
    }

    /** 3.25 -> "0:03.3" */
    fun preciseClock(seconds: Double): String {
        val tenths = (seconds.coerceAtLeast(0.0) * 10).roundToLong()
        val minutes = tenths / 600
        val rest = tenths % 600
        return "$minutes:${pad2((rest / 10).toInt())}.${rest % 10}"
    }

    fun seconds(value: Double): String = "${decimal(value, 1)} s"
    fun speed(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()}x" else "${decimal(value, 2).trimEnd('0')}x"
    fun percent(value: Double): String = "${(value * 100).roundToInt()}%"
    fun degrees(value: Double): String = "${value.roundToInt()}°"
    fun pixels(value: Double): String = "${value.roundToInt()} px"
    fun twoDecimals(value: Double): String = decimal(value, 2)

    fun decimal(value: Double, digits: Int): String {
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val scaled = (abs(value) * factor).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        val whole = scaled / factor
        if (digits == 0) return "$sign$whole"
        val fraction = (scaled % factor).toString().padStart(digits, '0')
        return "$sign$whole.$fraction"
    }

    private fun pad2(value: Int) = value.toString().padStart(2, '0')
}

/** ARGB rang (0xAARRGGBB). */
object Colors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val ORANGE = 0xFFFF5E3A.toInt()
    const val YELLOW = 0xFFFFD91A.toInt()

    val palette = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFFF5E3A, 0xFFFFD91A, 0xFFFF4040, 0xFF33D9FF,
        0xFF3A7BFF, 0xFF8E44FF, 0xFFFF4FB8, 0xFF2ECC71, 0xFF7F8C8D, 0xFF1E1E2E,
    ).map { it.toInt() }
}
