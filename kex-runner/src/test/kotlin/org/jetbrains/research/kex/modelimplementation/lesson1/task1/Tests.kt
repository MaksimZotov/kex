package org.jetbrains.research.kex.modelimplementation.lesson1.task1

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class Tests {

    @Test
    @Tag("Example")
    fun someFun() {
        assertEquals(0.0, someFun(0, 256, false))
        assertEquals(0.1, someFun(1, 0, true))
        assertEquals(0.2, someFun(2, 0, false))
        assertEquals(0.3, someFun(2, 1, true))
        assertEquals(0.4, someFun(3, 2, true))
        assertEquals(0.5, someFun(0, 256, true))
    }
}