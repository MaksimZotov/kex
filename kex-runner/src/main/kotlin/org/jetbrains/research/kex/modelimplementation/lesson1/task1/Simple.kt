@file:Suppress("UNUSED_PARAMETER")

package org.jetbrains.research.kex.modelimplementation.lesson1.task1

fun testIf(a: Int, b: Int): Int {
    val res = if (a > b) {
        a - b
    } else {
        a + b
    }
    println(res)
    return res
}