package com.fantamomo.mc.amongus.util.coroutines

import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

fun Flow<StreamFrame>.toLineFlow(): Flow<String> = flow {
    val buffer = StringBuilder()

    var lastIndex = 0

    collect { frame ->
        when (frame) {
            is StreamFrame.TextComplete -> {

                if (lastIndex != 0) {
                    buffer.append(frame.text.substring(lastIndex))
                }

                emitLinesIfPossible(buffer)
            }

            is StreamFrame.TextDelta -> {
                buffer.append(frame.text)
                frame.index?.let { lastIndex = it }

                emitLinesIfPossible(buffer)
            }

            is StreamFrame.End -> {
                if (buffer.isNotEmpty()) {
                    emit(buffer.toString())
                    buffer.clear()
                }
            }

            else -> {}
        }
    }
}

private suspend fun FlowCollector<String>.emitLinesIfPossible(buffer: StringBuilder) {
    if (buffer.isEmpty()) return
    while (true) {
        val newLineIndex = buffer.indexOf('\n')
        if (newLineIndex == -1) break

        val line = buffer.substring(0, newLineIndex)
        emit(line)

        buffer.delete(0, newLineIndex + 1)
    }
}