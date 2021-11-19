package net.netconomy.tools.restflow.dsl

import groovy.json.JsonSlurper
import groovy.transform.ToString
import groovy.util.slurpersupport.GPathResult
import net.netconomy.tools.restflow.impl.CommLog
import net.netconomy.tools.restflow.impl.HttpBody
import org.apache.http.HttpResponse

import java.nio.charset.Charset


/**
 * @since 2018-10-12
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
@ToString(includes = ['statusCode', 'contentType'])
class Response {

    final CommLog log

    final int statusCode
    final String reasonPhrase
    final Map<String, String> headers
    final HttpBody body

    Response(RestFlow client, HttpResponse response) {
        this.log = client.log
        this.statusCode = response.statusLine.statusCode
        this.reasonPhrase = response.statusLine.reasonPhrase
        this.headers = new LinkedHashMap<String, String>().with {
            response.allHeaders.each { h ->
                put(h.name, h.value)
            }
            asImmutable()
        }
        body = response.entity ? new HttpBody(response.entity, client.defaultResponseCharset) : HttpBody.EMPTY
        log.recvBody(body)
    }

    boolean getHasContent() {
        return rawContent != null && rawContent.length
    }

    String getContentType() {
        return body.mimeType
    }

    Charset getCharset() {
        return body.charset
    }

    byte[] getRawContent() {
        return body.rawContent
    }

    String getContent() {
        return body.content
    }

    Object getJson() {
        return new JsonSlurper().parseText(content)
    }

    GPathResult getXml() {
        return new XmlSlurper().parseText(content)
    }

}
