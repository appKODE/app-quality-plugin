package ru.kode.android.app.quality.plugin.foundation.utils

import groovy.lang.Closure
import groovy.lang.DelegatingMetaClass
import groovy.lang.MetaClass
import org.codehaus.groovy.runtime.InvokerHelper
import ru.kode.android.gradle.commons.util.configureGroovy

private val CLOSURE_METHOD_NAMES = Closure::class.java.methods.mapTo(HashSet()) { it.name }

/**
 * [configureGroovy] that also binds a nested block named like a [Closure] method to [target].
 * Groovy resolves a closure's own methods before its delegate, so a bare `compose { }` inside
 * `detekt { }` would call `Closure.compose` and drop the block silently.
 */
internal fun <T : Any> configureDsl(
    closure: Closure<in T>,
    target: T,
) {
    closure.metaClass = TargetFirstMetaClass(closure.metaClass, target)
    configureGroovy(closure, target)
}

private class TargetFirstMetaClass(
    closureMetaClass: MetaClass,
    private val target: Any,
) : DelegatingMetaClass(closureMetaClass) {
    init {
        initialize()
    }

    override fun invokeMethod(
        receiver: Any?,
        methodName: String,
        arguments: Array<out Any?>?,
    ): Any? =
        if (methodName in CLOSURE_METHOD_NAMES && targetResponds(methodName, arguments)) {
            InvokerHelper.invokeMethod(target, methodName, arguments)
        } else {
            super.invokeMethod(receiver, methodName, arguments)
        }

    private fun targetResponds(
        methodName: String,
        arguments: Array<out Any?>?,
    ): Boolean = InvokerHelper.getMetaClass(target).respondsTo(target, methodName, arguments).isNotEmpty()
}
