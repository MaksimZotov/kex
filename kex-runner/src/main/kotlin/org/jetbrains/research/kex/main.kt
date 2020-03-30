package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer

@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    //Kex(args).main()

    val kex: Kex = Kex(arrayOf(
            "--jar",
            "C:\\Users\\Maksim\\IdeaProjects\\KotlinAsFirst2019\\target\\kfirst-19.0.2.jar",
            "--config",
            "C:\\Users\\Maksim\\IdeaProjects\\kex\\kex.ini"
    ))
    kex.generateInputs("lesson2.task1.IfElseKt::rookOrBishopThreatens")
    val inputs = kex.inputs
}