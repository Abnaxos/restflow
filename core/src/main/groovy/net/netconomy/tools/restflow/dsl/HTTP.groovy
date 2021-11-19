package net.netconomy.tools.restflow.dsl

import org.apache.http.HttpStatus

/**
 * @since 2018-10-15
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class HTTP {

    public static final Accept = 'Accept'
    public static final Authorization = 'Authorization'

    public static final AnyType = '*/*'
    public static final JsonType = 'application/json'
    public static final XmlType = 'application/xml'
    public static final PlainTextType = 'text/plain'
    public static final OctetStream = 'application/octet-stream'
    public static final UrlEncodedFormType = 'application/x-www-form-urlencoded'

    enum Scheme {

        HTTP, HTTPS;

        final uriScheme = name().toLowerCase()
    }

    public static final Map<Integer, String> CodeNames = HttpStatus.with { c ->
        Map<Integer, String> map = [:]
        c.declaredFields.
                findAll { f -> f.name.startsWith('SC_') }.
                each { f -> map.put((int) f.get(null), f.name.substring(3)) }
        map.asImmutable()
    }

}
