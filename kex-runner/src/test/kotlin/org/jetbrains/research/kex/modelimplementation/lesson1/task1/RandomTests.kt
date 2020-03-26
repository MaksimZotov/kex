package org.jetbrains.research.kex.modelimplementation.lesson1.task1

import kotlinx.serialization.ImplicitReflectionSerializer
import org.jetbrains.research.kex.Kex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

object ModelImplementation {

    fun someFun(a: Int, b: Int, c: Boolean): Double =
        if (a > b) {
            when {
                a == 1 && c -> 0.1
                a == 2 -> if (b == 0) 0.2 else 0.3
                else -> 0.4
            }
        } else if (c) 0.5
        else 0.0
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RandomTests {
    val packageName = "lesson1/task1"
    val kex: Kex = Kex(arrayOf(
        "--jar",
        "C:\\Users\\Maksim\\IdeaProjects\\kex\\kfirst-19.0.2.jar",
        "--target",
        "lesson1.task1.*"
    ))

    @BeforeAll
    @ImplicitReflectionSerializer
    fun start() {
        kex.main()
    }

    @Test
    @Tag("Example")
    fun someFun() {
        val methodName = "someFun(int, int, bool): double"
        for (input in kex.inputs[packageName + methodName]!!) {
            val expected = ModelImplementation.someFun(input[0] as Int, input[1] as Int, input[2] as Boolean)
            val actual = someFun(input[0] as Int, input[1] as Int, input[2] as Boolean)
            println("expected: $expected actual: $actual")
            assertEquals(expected, actual)
        }
    }
}