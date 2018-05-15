package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class CallTerm : Term {
    val method: Method
    val isStatic: Boolean

    fun getObjectRef() = if (isStatic) null else subterms[0]
    fun getArguments() = if (isStatic) subterms else subterms.drop(1).toTypedArray()

    constructor(type: Type, method: Method, operands: Array<Term>)
            : super("", type, operands) {
        this.method = method
        isStatic = true
    }

    constructor(type: Type, method: Method, objectRef: Term, operands: Array<Term>)
            : super("", type, arrayOf(objectRef).plus(operands)) {
        this.method = method
        isStatic = false
    }

    override fun print(): String {
        val sb = StringBuilder()
        if (isStatic) sb.append("${method.`class`.name}.")
        else sb.append("${getObjectRef()}.")
        sb.append("${method.name}(")
        val arguments = getArguments()
        arguments.take(1).forEach { sb.append(it) }
        arguments.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val objectRef = if (isStatic) null else t.transform(getObjectRef()!!)
        val arguments = getArguments().map { t.transform(it) }.toTypedArray()
        return when {
            objectRef == getObjectRef() && arguments.contentEquals(getArguments()) -> this
            objectRef == null -> t.tf.getCall(method, arguments)
            else -> t.tf.getCall(method, objectRef, arguments)
        }
    }
}