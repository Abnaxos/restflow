package net.netconomy.tools.restflow.dsl

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import net.netconomy.tools.restflow.impl.Authentication
import net.netconomy.tools.restflow.impl.MarkupBuilder
import net.netconomy.tools.restflow.impl.RequestExecution
import net.netconomy.tools.restflow.impl.UtfCharsets
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.message.BasicNameValuePair
import org.intellij.lang.annotations.Language

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


/**
 * Represents a request configuration. Request configurations are organised
 * as stack and coalesced before submission. Configurations override
 * configurations of parent requests, the final request path is built top
 * down using {@link RequestExecution#doBuildPath(java.util.List) buildPath()}.
 *
 * @since 2018-10-12
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class RequestConfig {

    static final Charset DEFAULT_CONTENT_CHARSET = StandardCharsets.UTF_8
    static final Charset DEFAULT_REQUEST_CHARSET = StandardCharsets.UTF_8

    final RequestConfig parent

    Charset requestCharset

    HTTP.Scheme scheme
    String host
    String path

    Authentication authentication

    String contentType = null
    Charset charset = DEFAULT_CONTENT_CHARSET
    String content = null
    byte[] rawContent = null

    final Map<String, Object> headers = [:]
    final Map<String, Object> query = [:]

    RequestConfig(RequestConfig parent = null, String path = null) {
        this.parent = parent
        this.path = path
    }

    /**
     * Set the scheme AKA protocol (HTTP(S)).
     */
    void scheme(HTTP.Scheme scheme) {
        this.scheme = scheme
    }

    /**
     * Set the scheme to HTTP and the host name (may include port).
     *
     * @see #scheme(HTTP.Scheme)
     */
    void http(String host = null) {
        scheme = HTTP.Scheme.HTTP
        if (host) {
            this.host = host
        }
    }

    /**
     * Set the scheme to HTTPS and the host name (may include port).
     *
     * @see #scheme(HTTP.Scheme)
     */
    void https(String host = null) {
        scheme = HTTP.Scheme.HTTPS
        if (host) {
            this.host = host
        }
    }

    /**
     * Set the hostname, an explicit port may optionally be specified using ':<port>'.
     */
    void host(String host) {
        this.host = host
    }

    /**
     * Set an OAuth bearer token.
     */
    void bearerAuth(String token) {
        authentication = new Authentication(Authentication.Type.BEARER, token)
    }

    /**
     * Set a basic auth username/password.
     */
    void basicAuth(String username, String password) {
        authentication = new Authentication(Authentication.Type.BASIC, username + ':' + password)
    }

    /**
     * Clear authentication info
     */
    void clearAuth() {
        authentication = null
    }

    /**
     * Set the path component.
     *
     * @see RequestExecution#doBuildPath(java.util.List)
     */
    void path(String path) {
        this.path = path
    }

    /**
     * (rarely used) Set the request charset, i.e. the charset used for
     * encoding the URL. Defaults to UTF-8.
     */
    void requestCharset(String requestCharsetName) {
        requestCharset(Charset.forName(requestCharsetName))
    }

    /**
     * (rarely used) Set the request charset, i.e. the charset used for
     * encoding the URL. Defaults to UTF-8.
     */
    void requestCharset(Charset requestCharset) {
        this.requestCharset = requestCharset
    }

    /**
     * Set the content type.
     */
    void contentType(String contentType) {
        this.contentType = contentType
    }

    /**
     * Set the content charset for text content (defaults to UTF-8).
     */
    void charset(String charsetName) {
        charset(Charset.forName(charsetName))
    }

    /**
     * Set the content charset for text content (defaults to UTF-8).
     */
    void charset(Charset charset) {
        this.charset = charset
    }

    /**
     * Set the text content.
     */
    void content(String content) {
        this.content = content
        this.rawContent = null
    }

    /**
     * Set the binary content.
     */
    void content(byte[] content) {
        this.content = null
        this.rawContent = content
    }

    /**
     * Set the Accept header.
     */
    void accept(String mimeType) {
        headers.put(HTTP.Accept, mimeType)
    }

    /**
     * Set the Accept header to JSON.
     */
    void acceptJson() {
        accept(HTTP.JsonType)
    }

    /**
     * Set one or more custom HTTP header(s).
     */
    void header(Map h) {
        putAll(h, headers)
    }

    /**
     * Add a query parameter.
     */
    void query(Map q) {
        putAll(q, query)
    }

    /**
     * Set JSON content using a {@code JsonBuilder}. Also sets the content type and
     * Accept header to "application/json". A UTF charset will be forced.
     *
     * @see "<a href="http://docs.groovy-lang.org/latest/html/gapi/groovy/json/JsonBuilder.html">JsonBuilder</a>"
     */
    void json(@DelegatesTo(JsonBuilder) Closure closure) {
        def builder = new JsonBuilder()
        builder.call(closure)
        rawJson(builder.toPrettyString(), false)
    }

    /**
     * Set JSON content using a map. Also sets the content type and Accept header
     * to "application/json". A UTF charset will be forced.
     */
    void json(Map map) {
        def builder = new JsonBuilder()
        builder.call(map)
        rawJson(builder.toPrettyString(), false)
    }

    /**
     * Set JSON source code. Also sets the content type and Accept header
     * to "application/json". A UTF charset will be forced.
     *
     * @param json
     *
     * @deprecated Use {@link #rawJson(String)} instead.
     */
    @Deprecated
    void json(@Language("JSON") String json) {
        rawJson(json, true)
    }

    /**
     * Set JSON source code. Also sets the content type and Accept header
     * to "application/json". A UTF charset will be forced.
     *
     * @param json
     */
    void rawJson(@Language("JSON") String json, boolean validate = true) {
        acceptJson()
        if (validate) {
            new JsonSlurper().parseText(json)
        }
        contentType = HTTP.JsonType
        charset = UtfCharsets.forceUtf(charset)
        content = json
    }

    /**
     * Create a <a
     * href="http://docs.groovy-lang.org/latest/html/api/groovy/xml/MarkupBuilder.html">markup
     * builder</a> to set the XML content. The content type and charset will be set after the
     * root tag has been closed.
     *
     * <p>Note that an XML header will be prepended using the request's current
     * charset. <strong>If you want to change the charset, set it <em>before</em>
     * building the XML.</strong>
     *
     * @see "<a href='http://docs.groovy-lang.org/latest/html/api/groovy/xml/MarkupBuilder.html'>MarkupBuilder</a>"
     */
    groovy.xml.MarkupBuilder getXml() {
        xml()
    }

    /**
     * Create a <a
     * href="http://docs.groovy-lang.org/latest/html/api/groovy/xml/MarkupBuilder.html">markup
     * builder</a> to set the XML content. The content type and charset will be set after the
     * root tag has been closed.
     *
     * <p>Note that an XML header will be prepended using the request's current
     * charset. <strong>If you want to change the charset, set it <em>before</em>
     * building the XML.</strong>
     *
     * @see "<a href='http://docs.groovy-lang.org/latest/html/api/groovy/xml/MarkupBuilder.html'>MarkupBuilder</a>"
     */
    groovy.xml.MarkupBuilder xml(boolean pretty = false, String indent = '  ') {
        return new MarkupBuilder(new StringWriter(), this, pretty, indent)
    }

    /**
     * Set XML source code. Also sets the content type to "application/xml".
     */
    void rawXml(@Language("XML") String xml) {
        content = xml
        contentType = HTTP.XmlType
    }

    /**
     * Add URL-encoded form data. Also sets the content type.
     */
    void form(Map map) {
        List<NameValuePair> formData = []
        map.each {k, v -> formData.add(new BasicNameValuePair(k as String, v as String))}
        def entity = new UrlEncodedFormEntity(formData, charset)
        content = new String(entity.content.bytes, charset)
        contentType = HTTP.UrlEncodedFormType
    }

    /**
     * Build the full build path.
     *
     * @see RequestExecution#doBuildPath(java.util.List)
     */
    String buildPath() {
        RequestExecution.doBuildPath(RequestExecution.buildList(this, {it.path}))
    }

    private static void putAll(Map from, Map<String, Object> into) {
        from.each { k, v -> into.put(k as String, v) }
    }
}
