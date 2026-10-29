package com.jas.montaj.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Undo / Redo tarixi. Slider surilganda har bir kichik o'zgarish alohida qadam bo'lmasligi uchun
 * [coalesceMillis] ichidagi ketma-ket o'zgarishlar bitta qadamga birlashtiriladi.
 */
class History<T>(private val limit: Int = 100, private val coalesceMillis: Long = 600) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    private var lastRecord: TimeMark? = null

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** O'zgarishdan oldingi holatni yozib qo'yadi. */
    fun record(before: T) {
        val mark = lastRecord
        val recent = mark != null && mark.elapsedNow().inWholeMilliseconds < coalesceMillis
        lastRecord = TimeSource.Monotonic.markNow()
        future.clear()
        if (!recent || past.isEmpty()) {
            past.addLast(before)
            if (past.size > limit) past.removeFirst()
        }
        refresh()
    }

    fun undo(current: T): T? {
        val previous = past.removeLastOrNull() ?: return null
        future.addLast(current)
        lastRecord = null
        refresh()
        return previous
    }

    fun redo(current: T): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        lastRecord = null
        refresh()
        return next
    }

    fun clear() {
        past.clear()
        future.clear()
        lastRecord = null
        refresh()
    }

    private fun refresh() {
        canUndo = past.isNotEmpty()
        canRedo = future.isNotEmpty()
    }
}
