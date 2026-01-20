package com.fantamomo.mc.amongus.util

import kotlin.random.Random

fun <E> Collection<E>.randomListWithDuplicates(
    count: Int,
    random: Random = Random
): List<E> {
    require(count >= 0) { "Count must be positive" }
    if (count == 0) return emptyList()
    if (isEmpty()) return emptyList()

    return List(count) { random(random) }
}

fun <E> Collection<E>.randomListDistinct(
    count: Int,
    random: Random = Random
): List<E> {
    require(count >= 0) { "Count must be positive" }

    if (count == 0) return emptyList()
    if (count >= size) return toList()

    val list = toMutableList()

    for (i in 0 until count) {
        val j = random.nextInt(i, list.size)
        list[i] = list[j].also { list[j] = list[i] }
    }

    return list.subList(0, count)
}