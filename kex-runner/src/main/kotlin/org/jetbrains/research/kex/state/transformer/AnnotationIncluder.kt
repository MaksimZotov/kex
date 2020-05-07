package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.assert.unreachable
import org.jetbrains.research.kex.annotations.AnnotatedCall
import org.jetbrains.research.kex.annotations.AnnotatedParam
import org.jetbrains.research.kex.annotations.AnnotationsLoader
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.ConstClassTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.DoubleType
import org.jetbrains.research.kfg.type.IntType
import org.jetbrains.research.kfg.type.Integral
import org.jetbrains.research.kfg.type.Type
import org.objectweb.asm.tree.ClassNode
import java.util.*
import kotlin.collections.ArrayList

class AnnotationIncluder(val annotations: AnnotationsLoader, val method: Method) : RecollectingTransformer<AnnotationIncluder> {

    override val builders = ArrayDeque<StateBuilder>().apply { push(StateBuilder()) }

    override fun apply(ps: PredicateState): PredicateState {
        handleMethodParameters()
        return super.apply(ps)
    }

    fun getArgKexType(type: Type): KexType? = when (type) {
        is DoubleType -> KexDouble()
        is IntType -> KexInt()
        else -> null
    }

    fun getStatesBasedOnParams(args: List<Term>, annotatedCall: AnnotatedCall): MutableList<PredicateState> {
        val states = mutableListOf<PredicateState>()
        for ((i, param) in annotatedCall.params.withIndex()) {
            for (annotation in param.annotations) {
                val arg = args[i]
                annotation.preciseValue(arg)?.run { states += this }
                states += annotation.preciseParam(args[i], i) ?: continue
            }
        }
        return states
    }

    fun concatenate(states: MutableList<PredicateState>) {
        val predicates = mutableListOf<Predicate>()
        for (state in states) {
            val ps = expand(state)
            @Suppress("UNCHECKED_CAST")
            when (ps) {
                is List<*> -> predicates += ps as List<Predicate>
                is PredicateState -> {
                    currentBuilder += BasicState(predicates.toList())
                    currentBuilder += ps
                    predicates.clear()
                }
            }
        }
        if (predicates.isNotEmpty())
            currentBuilder += BasicState(predicates.toList())
    }

    fun handleMethodParameters() {
        val args = arrayListOf<Term>()
        val argTypes = method.argTypes
        for (i in argTypes.indices) {
            val type = getArgKexType(argTypes[i]) ?: continue
            args.add(ArgumentTerm(type, i))
        }

        val annotatedCall = annotations.getExactCall("${method.`class`}.${method.name}",
                *Array(argTypes.size) { argTypes[it].name }) ?: return

        concatenate(getStatesBasedOnParams(args, annotatedCall))
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val method = call.method
        val args = call.arguments
        val argTypes = method.argTypes

        val annotatedCall = annotations.getExactCall("${method.`class`}.${method.name}",
                *Array(argTypes.size) { argTypes[it].name }) ?: return predicate

        val states = getStatesBasedOnParams(args, annotatedCall)

        for (annotation in annotatedCall.annotations)
            states += annotation.preciseBeforeCall(predicate) ?: continue
        states += BasicState(Collections.singletonList(predicate))
        val returnValue = predicate.lhvUnsafe
        for (annotation in annotatedCall.annotations) {
            annotation.preciseAfterCall(predicate)?.run { states += this }
            if (returnValue !== null) {
                annotation.preciseValue(returnValue)?.run { states += this }
                states += annotation.preciseReturn(returnValue) ?: continue
            }
        }
        // Concatenate some States for better presentation
        concatenate(states)
        return nothing()
    }

    private fun expand(ps: PredicateState): Any = when (ps) {
        is ChainState -> expandChain(ps)
        is BasicState -> ps.predicates
        is ChoiceState -> ps
        else -> unreachable {  }
    }

    @Suppress("UNCHECKED_CAST")
    private fun expandChain(ps: ChainState): Any {
        val base = expand(ps.base)
        val curr = expand(ps.curr)
        if (base is List<*> && curr is List<*>)
            return base + curr
        if (base is List<*> && curr is PredicateState)
            return ChainState(BasicState(base as List<Predicate>), curr)
        if (base is PredicateState && curr is List<*>)
            return ChainState(base, BasicState(curr as List<Predicate>))
        if (base is PredicateState && curr is PredicateState)
            return ChainState(base, curr)
        return unreachable { }
    }
}