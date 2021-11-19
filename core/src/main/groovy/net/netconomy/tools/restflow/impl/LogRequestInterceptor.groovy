package net.netconomy.tools.restflow.impl

import net.netconomy.tools.restflow.dsl.RestFlow
import org.apache.http.*
import org.apache.http.protocol.HttpContext

import java.nio.charset.StandardCharsets


/**
 * @since 2018-10-18
 * @author Raffael Herzog (r.herzog@netconomy.net)
 */
class LogRequestInterceptor implements HttpRequestInterceptor {

    final RestFlow flow

    LogRequestInterceptor(RestFlow flow) {
        this.flow = flow
    }

    @Override
    void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        flow.log.send request.requestLine.method, request.requestLine.uri
        request.allHeaders.each { h ->
            flow.log.send h.name + ':', h.value
        }
        HttpEntity entity = null
        if (request instanceof HttpEntityEnclosingRequest) {
            entity = request.entity
        }
        flow.log.sendBody(entity ? new HttpBody(entity, StandardCharsets.UTF_8) : HttpBody.EMPTY)
    }
}
