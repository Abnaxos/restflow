package net.netconomy.tools.restflow.impl

import net.netconomy.tools.restflow.dsl.HTTP
import net.netconomy.tools.restflow.dsl.RequestConfig
import net.netconomy.tools.restflow.dsl.Response
import net.netconomy.tools.restflow.dsl.RestFlow
import org.apache.http.HttpEntity
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.protocol.BasicHttpContext

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.function.Function

final class RequestExecution {

    private static final Set<String> NO_CHARSET_TYPES = [HTTP.JsonType] as Set
    private static final ThreadLocal<Set<PreRequestHandler>> nowExecutingHandlers = new ThreadLocal<>()

    static Response execute(HttpClient httpBackend, HttpRequestBase request,
                            RestFlow flow, RequestConfig config) {
        config.requestCharset(firstNonNull(config, {it.requestCharset}, StandardCharsets.UTF_8))
        config.scheme(firstNonNull(config, {it.scheme}, HTTP.Scheme.HTTPS))
        config.host(firstNonNull(config, {it.host}))
        config.path(config.buildPath())
        config.query(coalesceStringMap([:], config, {it.query}))
        config.header(coalesceStringMap([:], config, {it.headers}))
        config.authentication = firstNonNull(config, {it.authentication}, null)
        runPreRequestHandlers(flow, config)
        request.setURI(new URI(new StringBuilder().with {
            append config.scheme.uriScheme
            append '://'
            append config.host
            append '/'
            append config.path
            if (config.query) {
                append '?'
                def first = true
                config.query.each { k, v ->
                    if (first) {
                        first = false
                    } else {
                        append '&'
                    }
                    append URLEncoder.encode(k, config.requestCharset.name())
                    append '='
                    append URLEncoder.encode(v as String, config.requestCharset.name())
                }
            }
            it as String
        }))
        flow.log.send('URI', request.getURI())
        config.headers.each { k, v ->
            request.setHeader(k, v as String)
        }
        if (config.authentication) {
            request.addHeader(HTTP.Authorization,
                              config.authentication.type.encodeAuthHeader(config.authentication.evidence))
        }
        HttpEntity entity = null
        if (config.content != null) {
            entity = new StringEntity(config.content, (ContentType) createContentType(config.contentType, config.charset))
        } else if (config.rawContent != null) {
            entity = new ByteArrayEntity(config.rawContent, (ContentType) createContentType(config.contentType))
        }
        if (entity != null) {
            if (!(request instanceof HttpEntityEnclosingRequest)) {
                throw new UnsupportedOperationException("Content not supported for ${request.getClass().getSimpleName()}")
            }
            request.entity = entity
        }
        def httpContext = new BasicHttpContext()
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE,
            flow.cookies.enabled ? flow.cookies.store : NoCookiesStore.INSTANCE)
        return httpBackend.execute(request, httpContext).withCloseable {HttpResponse resp ->
            flow.log.recv resp.statusLine.statusCode,
                          HTTP.CodeNames.get(resp.statusLine.statusCode, '?'),
                          resp.statusLine.reasonPhrase
            resp.allHeaders.each { h ->
                flow.log.recv h.name + ':', h.value
            }
            return new Response(flow, resp)
        }
    }

    private static void runPreRequestHandlers(RestFlow flow, RequestConfig config) {
        boolean clearNowExecutingHandlers = false
        if (nowExecutingHandlers.get() == null) {
            nowExecutingHandlers.set(new HashSet())
            clearNowExecutingHandlers = true
        }
        try {
            flow.preRequestHandlers.each {h ->
                if (nowExecutingHandlers.get().add(h)) {
                    try {
                        h.preRequest(flow, config)
                    }
                    finally {
                        nowExecutingHandlers.get().remove(h)
                    }
                }
            }
        }
        finally {
            if (clearNowExecutingHandlers) {
                nowExecutingHandlers.remove()
            }
        }
    }

    private static Map<String, String> coalesceStringMap(Map<String, String> map, RequestConfig config,
                                                         Function<? super RequestConfig, Map<String, ?>> getter) {
        if (config == null) {
            return map
        }
        coalesceStringMap(map, config.parent, getter)
        getter.apply(config).each { k, v ->
            map.put(k as String, v as String)
        }
        return map
    }

    private static ContentType createContentType(String mimeType, Charset charset = null) {
        if (!mimeType) {
            return null
        } else if (NO_CHARSET_TYPES.contains(mimeType)) {
            return ContentType.create(mimeType, (Charset)null)
        } else {
            return ContentType.create(mimeType, charset)
        }
    }

    static <T> T firstNonNull(RequestConfig config, Function<? super RequestConfig, T> getter, T fallback) {
        T value = getter.apply(config)
        if (value == null) {
            value = (T)(config.parent ? firstNonNull(config.parent, getter, fallback) : fallback)
        }
        return value
    }

    static <T> T firstNonNull(RequestConfig config, Function<? super RequestConfig, T> getter) {
        T value = firstNonNull(config, getter, null)
        if (value == null) {
            throw new IllegalStateException('Required value not set')
        }
        return value
    }

    /**
     * Build a URL path of fragments. The resulting path will <em>not</em>
     * start with a '/'. The methods makes sure that all fragments are
     * separated by a single '/'. If a fragment begins with '/', this does
     * <em>not</em> denote an absolute path. For absolute paths, use '//'.
     */
    static String doBuildPath(List<String> fragments) {
        StringBuilder buf = new StringBuilder()
        for (p in fragments) {
            if (p) {
                if (p.startsWith('/')) {
                    p = p.substring(1)
                }
                if (p.startsWith('/')) {
                    // absolute path
                    buf.setLength(0)
                    buf.append(p.substring(1))
                } else {
                    if (buf.length() > 0 && buf.charAt(buf.length() - 1) != (char) '/') {
                        buf.append '/'
                    }
                    buf.append(p)
                }
            }
        }
        return buf.toString()
    }

    static <T> List<T> buildList(RequestConfig config, Function<? super RequestConfig, T> getter,
                                 List<T> list = new ArrayList()) {
        if (config.parent != null) {
            buildList(config.parent, getter, list)
        }
        list.add(getter.apply(config))
        return list
    }

}
