package org.jetbrains.research.kex.modelimplementation.lesson1.task1

import org.jetbrains.research.kex.Kex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

object ModelImplementation {

    fun testIf(a: Int, b: Int): Int {
        val res = if (a > b) {
            a - b
        } else {
            a + b
        }
        println(res)
        return res
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RandomTests {
    val packageName = "lesson1/task1"
    val kex: Kex = Kex(arrayOf(
        "--jar",
        "C:\\Users\\Maksim\\IdeaProjects\\kex\\kfirst-19.0.2.jar",
        "--config",
        "C:\\Users\\Maksim\\IdeaProjects\\kex\\kex.ini"
    ))

    @Test
    @Tag("Example")
    fun testIf() {
        kex.generateInputs("lesson1.task1.*")
        /*
        val methodName = "someFun(int, int): int"
        for (input in kex.inputs[packageName + methodName]!!) {
            val expected = ModelImplementation.testIf(input[0] as Int, input[1] as Int)
            val actual = testIf(input[0] as Int, input[1] as Int)
            println("expected: $expected actual: $actual")
            assertEquals(expected, actual)
        }
         */
    }
}