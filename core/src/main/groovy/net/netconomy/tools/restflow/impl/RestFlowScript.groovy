package net.netconomy.tools.restflow.impl
/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
abstract class RestFlowScript extends DelegatingScript {

    private RestFlowScripts.Context context = null

    RestFlowScript() {
        super()
    }

    RestFlowScripts.Context getContext() {
        return context
    }

    void setContext(RestFlowScripts.Context context) {
        if (this.context) {
            throw new ReadOnlyPropertyException('context', getClass())
        } else {
            this.context = context
            super.setDelegate(context.restFlow)
            super.setBinding(context.binding)
        }
    }

    @Override
    void setDelegate(Object delegate) {
        throw new ReadOnlyPropertyException('delegate', getClass())
    }

    @Override
    void setBinding(Binding binding) {
        if (context == null) {
            super.setBinding(binding)
        }else {
            throw new ReadOnlyPropertyException('binding', getClass())
        }
    }
}
