package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Class

class ConstClassTerm(val `class`: Class) : Term("$`class`.class", TF.getRefType(`class`), listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}