@file:Suppress("UNUSED_PARAMETER")

package org.jetbrains.research.kex.modelimplementation.lesson1.task1

fun someFun(a: Int, b: Int, c: Boolean): Double = // TODO
    if (a > b) {
        when {
            a == 1 && c -> 0.1
            a == 2 -> if (b == 0) 0.2 else 0.3
            else -> 0.4
        }
    } else if (c) 0.5
    else 0.0