package net.netconomy.tools.restflow.dsl

import net.netconomy.tools.restflow.impl.*
import org.apache.http.client.CookieStore
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.methods.*
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContextBuilder

import java.awt.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.List


/**
 * Represent the state of a REST flow. This includes a cookie store, the
 * base template request, a reference to the last response.
 *
 * @since 2018-10-14
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class RestFlow {

    private static final Object GLOBAL_LOCK = new Object()
    private static volatile RestFlow GLOBAL = null

    private final ProfileLoader profileLoader
    private final CloseableHttpClient httpBackend

    final CookieStore cookieStore = new BasicCookieStore()
    /**
     * A list of {@link PreRequestHandler}s that may modify the request
     * configuration before each HTTP request.
     */
    final List<PreRequestHandler> preRequestHandlers = new ArrayList<>()

    /**
     * Allows access to the this instance by a different name. Useful in
     * closures that delegate to another instance, e.g.
     * <code>flow.host</code> in a closure delegating to
     * {@link RequestConfig}, where <code>host</code> would delegate to the
     * request's <code>host</code>.
     */
    final RestFlow flow = this

    /**
     * A map for extension objects. Any reference to a unknown property will
     * also look here (using {@code propertyMissing( )}.
     */
    final Map<String, Object> ext = new LinkedHashMap<>()

    final CommLog log

    /**
     * The default charset for decoding text responses.
     */
    Charset defaultResponseCharset

    /**
     * The current base request.
     */
    RequestConfig request

    /**
     * The response of the last HTTP request or {@code null}.
     */
    Response response

    RestFlow(ProfileLoader profileLoader, CommLog log = new PrintCommLog()) {
        this.profileLoader = profileLoader ?: new ProfileLoader(
                Thread.currentThread().contextClassLoader ?: RestFlow.classLoader)
        this.log = log
        SSLContextBuilder sslCtxBuilder = new SSLContextBuilder()
        sslCtxBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy())
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslCtxBuilder.build())
        httpBackend = HttpClients.custom().
                setSSLSocketFactory(sslSocketFactory).
                setDefaultRequestConfig(org.apache.http.client.config.RequestConfig.custom().
                        setCookieSpec(CookieSpecs.STANDARD).
                        build()).
                disableRedirectHandling().
                setDefaultCookieStore(cookieStore).
                addInterceptorLast(new LogRequestInterceptor(this)).
                build()
        reset()
    }

    /**
     * Get the global RESTflow instance creating it if necessary.
     */
    static RestFlow getGlobalInstance() {
        if (GLOBAL == null) {
            synchronized (GLOBAL_LOCK) {
                if (GLOBAL == null) {
                    GLOBAL = new RestFlow()
                }
            }
        }
        return GLOBAL
    }

    /**
     * Run the closure in the global {@code RestFlow} instance.
     *
     * @return The result of the closure.
     *
     * @deprecated The global instance will be removed.
     */
    @Deprecated
    static <T> T global(@RestDsl(RestFlow) Closure<T> closure) {
        DslUtil.invokeDelegateFirst(closure, getGlobalInstance())
    }

    /**
     * Run the closure in a new RESTflow instance and return the instance.
     *
     * @return The new RESTflow instance initialized by the closure.
     */
    RestFlow init(@RestDsl(RestFlow) Closure closure) {
        DslUtil.invokeDelegateFirst(closure, this)
        return this
    }

    /**
     * Run the closure in this RESTflow instance.
     *
     * @return The result of the closure.
     */
    def <T> T run(@RestDsl(RestFlow) Closure<T> closure) {
        DslUtil.invokeDelegateFirst(closure, this)
    }

    /**
     * Reset the RESTflow instance to the default state, clear all customisations.
     */
    void reset() {
        cookieStore.clear()
        ext.clear()
        defaultResponseCharset = StandardCharsets.UTF_8
        request = new RequestConfig()
        response = null
        profileLoader.applyAuto(this)
    }

    /**
     * Apply the named extension to this RESTflow instance.
     *
     * @param args A map of arguments.
     *
     * @param name The name of the extension.
     */
    void apply(Map<String, Object> args = null, String name) {
        profileLoader.apply(this, name, args)
    }

    /**
     * Add a pre-request handler to this RESTflow instance.
     *
     * @param closure The closure to be executed before each request; the
     * delegate will be set to the RESTflow instance, the request configuration of the request to be run is passed as parameter.
     */
    void preRequestHandler(@RestDsl(RequestConfig) Closure<?> closure) {
        preRequestHandlers.add(new PreRequestHandler() {
            @Override
            void preRequest(RestFlow flow, RequestConfig requestConfig) {
                DslUtil.invokeDelegateFirst(closure, requestConfig)
            }
        })
    }

    /**
     * Configure the current base request using the closure. In most cases,
     * there is just one base request, however, this is actually a stack. An
     * empty base request can be temporarily pushed using {@link
     * RestFlow#group(groovy.lang.Closure) group()}.
     *
     * @return The result of the closure
     *
     * @deprecated use {@link #request(Closure)} instead
     */
    @Deprecated
    <T> T baseRequest(@RestDsl(RequestConfig) Closure<T> closure) {
        request(closure)
    }

    /**
     * @deprecated use {@link #request} instead.
     */
    @Deprecated getBaseRequest() {
        request
    }

    /**
     * Configure the current base request using the closure. In most cases,
     * there is just one base request, however, this is actually a stack. An
     * empty base request can be temporarily pushed using {@link
     * RestFlow#group(groovy.lang.Closure) group()}.
     *
     * @return The result of the closure
     */
    def <T> T request(@RestDsl(RequestConfig) Closure<T> closure) {
        DslUtil.invokeDelegateFirst(closure, request)
    }

    /**
     * Pushes an empty base request applying the given path on the stack and
     * runs the closure and finally pops it. Useful for grouping several
     * request that apply to the same base path.
     *
     * @return The result of the closure.
     */
    def <T> T group(String path, @RestDsl(RestFlow) Closure<T> closure) {
        request = new RequestConfig(request)
        try {
            request.path path
            DslUtil.invokeDelegateFirst(closure, this)
        } finally {
            request = request.parent
        }
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP GET request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response GET(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpGet()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP GET request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response GET(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpGet()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP PUT request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response PUT(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpPut()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP PUT request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response PUT(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpPut()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP POST request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response POST(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpPost()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP POST request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response POST(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpPost()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP DELETE request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response DELETE(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpDelete()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP DELETE request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response DELETE(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpDelete()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP HEAD request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response HEAD(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpHead()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP HEAD request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response HEAD(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpHead()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP OPTIONS request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response OPTIONS(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpOptions()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP OPTIONS request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response OPTIONS(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpOptions()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP TRACE request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response TRACE(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpTrace()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP TRACE request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response TRACE(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpTrace()}, path, null, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP PATCH request. The response will be available as in
     * the field {@code response}.
     *
     * @param query Query parameters.
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response PATCH(Map<String, ?> query, String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpPatch()}, path, query, closure)
    }

    /**
     * Push a new request on the stack, configure it using the closure and
     * submit it as HTTP PATCH request. The response will be available as in
     * the field {@code response}.
     *
     * @param path The path of the request.
     * @param closure The configuration clsoure
     *
     * @return The response.
     */
    Response PATCH(String path, @RestDsl(RequestConfig) Closure closure = null) {
        __executeRequest({new HttpPatch()}, path, null, closure)
    }

    private Response __executeRequest(Closure<? extends HttpRequestBase> createRequest, String path, Map<String, ?> query, Closure closure) {
        RequestConfig request = new RequestConfig(request, path)
        if (query) {
            request.query query
        }
        DslUtil.invokeDelegateFirst(closure, request)
        response = RequestExecution.execute(httpBackend, createRequest.call(), this, request)
    }


    /**
     * Retrieve a password. This will first try to retrieve the password
     * from a system property named {@code restflow.auth.<name>  .<username>},
     * then it will attempt to show a password prompt using a Swing dialog,
     * and finally, if this throws a {@code HeadlessException}, it will try
     * to read the password from the console.
     *
     * @param username The username
     * @param name A name for the password, shown in the password prompt
     *                 or used to lookup the system property.
     *
     * @return The password or {@code null}.
     */
    String promptPassword(String username, String name) {
        String password = PasswordPrompt.nonInteractivePassword(username, name)
        boolean retryPasswordPrompt = true
        if (password == null && retryPasswordPrompt) {
            try {
                password = PasswordPrompt.swingPasswordPrompt(username, name)
                retryPasswordPrompt = false
            } catch (HeadlessException e) {
                log.info "Cannot show password dialog: $e"
            }
        }
        if (password == null && retryPasswordPrompt) {
            try {
                password = PasswordPrompt.consolePasswordPrompt(username, name)
            } catch (IOException e) {
                log.info "Cannot read password from console: $e"
            }
        }
        if (password == null) {
            log.warn "No password for $username ($name)"
        }
        return password
    }


    def propertyMissing(String name) {
        if (ext.containsKey(name)) {
            return ext.get(name)
        } else {
            throw new MissingPropertyException(name, getClass())
        }
    }
}
