package net.netconomy.tools.restflow.impl


import groovy.json.JsonOutput
import groovy.xml.XmlUtil
import net.netconomy.tools.restflow.dsl.HTTP
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType

import java.nio.charset.Charset
import java.util.function.Function

class HttpBody {

    public static final EMPTY = new HttpBody()

    private static final Set<String> KNOWN_TEXT_TYPES
    static {
        def types = new HashSet<String>()
        types.add(HTTP.JsonType)
        types.add(HTTP.UrlEncodedFormType)
        types.addAll(XmlTypes.XML_TYPES)
        KNOWN_TEXT_TYPES = types.asImmutable()
    }
    private static final Map<String, Function<? super String, String>> PRETTY_PRINTERS
    static {
        def pretty = new HashMap<String, Function<? super String, String>>()
        pretty.put(HTTP.JsonType, {String s -> JsonOutput.prettyPrint(s)})
        XmlTypes.XML_TYPES.each {
            pretty.put(it, {String s -> XmlUtil.serialize(s)})
        }
        PRETTY_PRINTERS = pretty.asImmutable()
    }

    private final String mimeType
    private final byte[] rawContent
    private final Charset charset
    private final String content
    private final Function<? super String, String> prettyPrinter

    HttpBody(HttpEntity entity, Charset defaultCharset) {
        rawContent = entity.content.getBytes()
        def ct = ContentType.parse(entity.contentType?.value ?: HTTP.AnyType)
        mimeType = ct.mimeType
        if (mimeType.startsWith('text/') || KNOWN_TEXT_TYPES.contains(mimeType)) {
            charset = ct.charset ?: defaultCharset
            content = new String(rawContent, charset)
        } else {
            charset = null
            content = null
        }
        prettyPrinter = PRETTY_PRINTERS.get(mimeType)
    }

    private HttpBody() {
        mimeType = null
        rawContent = null
        charset = null
        content = null
        prettyPrinter = null
    }

    boolean isEmpty() {
        return rawContent == null
    }

    String getMimeType() {
        return mimeType
    }

    byte[] getRawContent() {
        if (rawContent == null) {
            return null
        } else {
            def c = new byte[rawContent.length]
            System.arraycopy(rawContent, 0, c, 0, rawContent.length)
            return c
        }
    }

    Charset getCharset() {
        return charset
    }

    String getContent() {
        return content
    }

    boolean isPrettyPrintable() {
        return content && prettyPrinter
    }

    String getPrettyContent() {
        if (content) {
            try {
                return prettyPrinter ? prettyPrinter.apply(content) : content
            } catch (Exception e) {
                StringWriter str = new StringWriter()
                PrintWriter print = new PrintWriter(str)
                e.printStackTrace(print)
                return "Cannot pretty-print: $str\n$content"
            }
        } else {
            return content
        }
    }
}
