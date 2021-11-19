package net.netconomy.tools.restflow.impl

import groovy.transform.ThreadInterrupt
import net.netconomy.tools.restflow.dsl.RestFlow
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

final class RestFlowScripts {

    public static final String DEFAULT_CODEBASE = "/restflow/script"

    static CompilerConfiguration newCompilerConfiguration(boolean threadInterrupt = true) {
        def config = new CompilerConfiguration()
        config.scriptBaseClass = RestFlowScript.name
        if (threadInterrupt) {
            config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt))
        }
        config
    }

    static newGroovyShell(Binding binding,
                          ClassLoader parentClassLoader = RestFlowScript.classLoader,
                          boolean threadInterrupt = true) {
        newGroovyShell(binding, newCompilerConfiguration(threadInterrupt), parentClassLoader)
    }

    static GroovyShell newGroovyShell(Binding binding,
                                      CompilerConfiguration config,
                                      ClassLoader parentClassLoader = RestFlowScript.classLoader) {
        new GroovyShell(parentClassLoader, binding, config)
    }

    static RestFlowScript parse(RestFlow restFlow, String script, String fileName) {
        parse(newGroovyShell(new ReadOnlyBinding()), restFlow, script, fileName)
    }

    static RestFlowScript parse(RestFlow restFlow, File file) {
        parse(newGroovyShell(new ReadOnlyBinding()), restFlow, file)
    }

    static RestFlowScript parse(RestFlow restFlow, URI uri) {
        parse(newGroovyShell(new ReadOnlyBinding()), restFlow, uri)
    }

    static RestFlowScript parse(GroovyShell shell, RestFlow restFlow, String script, String fileName) {
        parse(shell, new ReadOnlyBinding(), restFlow, script, fileName)
    }

    static RestFlowScript parse(GroovyShell shell, RestFlow restFlow, File file) {
        parse(shell, new ReadOnlyBinding(), restFlow, file)
    }

    static RestFlowScript parse(GroovyShell shell, RestFlow restFlow, URI uri) {
        parse(shell, new ReadOnlyBinding(), restFlow, uri)
    }

    static RestFlowScript parse(GroovyShell shell, Binding binding, RestFlow restFlow, String script, String fileName) {
        def source = new GroovyCodeSource(script, fileName, DEFAULT_CODEBASE)
        init(shell, shell.parse(source) as RestFlowScript, binding, restFlow)
    }

    static RestFlowScript parse(GroovyShell shell, Binding binding, RestFlow restFlow, File file) {
        init(shell, shell.parse(file) as RestFlowScript, binding, restFlow)
    }

    static RestFlowScript parse(GroovyShell shell, Binding binding, RestFlow restFlow, URI uri) {
        init(shell, shell.parse(uri) as RestFlowScript, binding, restFlow)
    }

    static RestFlowScript init(GroovyShell shell, RestFlowScript script, Binding binding, RestFlow restFlow) {
        ((RestFlowScript)script).setContext(new Context(restFlow, binding, shell.classLoader))
        return script
    }

    static run(RestFlowScript script) {
        def thread = Thread.currentThread()
        def prevContextClassLoader = thread.getContextClassLoader()
        thread.setContextClassLoader(script.context.classLoader)
        try {
            script.run()
        } finally {
            thread.setContextClassLoader(prevContextClassLoader)
        }
    }

    static <T> T withShell(GroovyShell shell, Closure<? extends T> closure) {
        def thread = Thread.currentThread()
        def prevContextClassLoader = thread.getContextClassLoader()
        thread.setContextClassLoader(shell.classLoader)
        try {
            closure.call()
        } finally {
            thread.setContextClassLoader(prevContextClassLoader)
        }
    }

    static class Context {
        private final RestFlow restFlow
        private final Binding binding
        private final ClassLoader classLoader
        Context(RestFlow restFlow, Binding binding, ClassLoader classLoader) {
            this.restFlow = restFlow
            this.binding = binding
            this.classLoader = classLoader
        }
        RestFlow getRestFlow() {
            return restFlow
        }
        Binding getBinding() {
            return binding
        }
        ClassLoader getClassLoader() {
            return classLoader
        }
    }

    static class ReadOnlyBinding extends Binding {
        ReadOnlyBinding(Map<String, Object> presetBindings = Collections.emptyMap()) {
            if (presetBindings) {
                for (b in presetBindings.entrySet()) {
                    super.setVariable(b.key, b.value)
                }
            }
        }
        @Override
        void setVariable(String name, Object value) {
            switch (name) {
//            case '_':
//            case '__':
//            case 'args':
//                super.setVariable(name, value)
//                break
            default:
                throw new ReadOnlyPropertyException(name, getClass())
            }
        }
    }
}
