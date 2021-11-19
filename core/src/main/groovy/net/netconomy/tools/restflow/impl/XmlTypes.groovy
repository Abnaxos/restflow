package net.netconomy.tools.restflow.impl

final class XmlTypes {

    static final Set<String> XML_TYPES = ([
            'application/xml', 'text/xml',
            'application/xhtml+xml', 'application/atom+xml',
            'application/rss+xml', 'application/mathml+xml']
            as Set).asImmutable()

    private XmlTypes() {
    }

    static boolean isXml(String type) {
        return XML_TYPES.contains(type)
    }
}
