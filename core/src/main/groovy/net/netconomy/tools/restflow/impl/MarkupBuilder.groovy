package net.netconomy.tools.restflow.impl

import net.netconomy.tools.restflow.dsl.HTTP
import net.netconomy.tools.restflow.dsl.RequestConfig

class MarkupBuilder extends groovy.xml.MarkupBuilder {

    private final StringWriter out
    private final RequestConfig requestConfig
    private final boolean pretty
    private Object rootNode

    MarkupBuilder(StringWriter out, RequestConfig requestConfig, boolean pretty, String indent) {
        super(new IndentPrinter(out, pretty ? indent : '', pretty, false))
        this.out = out
        this.requestConfig = requestConfig
        this.pretty = pretty
        printer.println "<?xml version='1.0' encoding='${requestConfig.charset.name()}'?>"
    }

    @Override
    protected Object createNode(Object name) {
        updateRootNode(super.createNode(name))
    }

    @Override
    protected Object createNode(Object name, Object value) {
        updateRootNode(super.createNode(name, value))
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        return updateRootNode(super.createNode(name, attributes, value))
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        return updateRootNode(super.createNode(name, attributes))
    }

    private Object updateRootNode(Object node) {
        if (rootNode == null) {
            rootNode = node
        }
        node
    }

    @Override
    protected Object postNodeCompletion(Object parent, Object node) {
        def ret = super.postNodeCompletion(parent, node)
        if (node == rootNode) {
            printer.flush()
            requestConfig.contentType = HTTP.XmlType
            requestConfig.content = out.toString()
        }
        return ret
    }
}
