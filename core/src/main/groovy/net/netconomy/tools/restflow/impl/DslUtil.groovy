package net.netconomy.tools.restflow.impl

import org.codehaus.groovy.runtime.InvokerHelper

/**
 * @since 2018-10-12
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class DslUtil {

    static <T> T invokeDelegateFirst(Closure<T> closure, Object delegate) {
        invokeConfigClosure(closure, delegate, Closure.DELEGATE_FIRST)
    }

    private static <T> T invokeConfigClosure(Closure<T> closure, Object delegate, int resolveStrategy) {
        if (closure != null) {
            prepareConfigClosure(closure, delegate, resolveStrategy).call(delegate)
        } else {
            null
        }
    }

    static <T> Closure<T> prepareConfigClosure(Closure<T> closure, delegate, int resolveStrategy) {
        closure = (Closure)closure.clone()
        closure.delegate = delegate
        closure.resolveStrategy = resolveStrategy
        closure
    }

    static void tryPropertyOnMethodMissing(Object self, String methodName, Object argsObject) {
        Object[] args = InvokerHelper.asArray(argsObject)
        if (args.length != 1) {
            self.metaClass.getMetaProperty(methodName)?.with { p ->
                p.setProperty(self, args[1])
                return
            }
        }
        throw new MissingMethodException(methodName, self.getClass(), args)
    }

}
